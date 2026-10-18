package com.onatarslan.deeptodo.auth.refresh;

import com.onatarslan.deeptodo.auth.refresh.RotationResult.Reason;
import com.onatarslan.deeptodo.auth.refresh.RotationResult.Rejected;
import com.onatarslan.deeptodo.auth.refresh.RotationResult.Rotated;
import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserRepository;
import com.onatarslan.deeptodo.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

// Opaque refresh token'ların yaşam döngüsü: üret → rotate → revoke (spec §7.9).
// Katı reuse detection: kullanılmış bir token tekrar gelirse bütün aile revoke edilir (grace window yok).
@Service
public class RefreshTokenService {

    // 32 byte = 256 bit rastgelelik; tahmin edilemez.
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               RefreshTokenProperties properties,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    // value: istemciye giden ham token. DB'de yok; yalnız hash'i var.
    public record IssuedRefreshToken(String value, Instant expiresAt) {}

    // Login / register: yeni bir aile (oturum) başlatır.
    @Transactional
    public IssuedRefreshToken issue(UUID userId) {
        return issueInFamily(userId, UUID.randomUUID(), clock.instant());
    }

    // Refresh: sunulan token'ı tüketir ve aynı aileden yenisini üretir.
    // Her adım bir öncekinin sonucuna bakar; reddedilen durumda yapılan revoke bu transaction'la commit edilir.
    @Transactional
    public RotationResult rotate(String rawToken) {
        Instant now = clock.instant();

        // 1. Token'ı bul.
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hashToken(rawToken));
        if (found.isEmpty()) {
            return new Rejected(Reason.UNKNOWN);
        }
        RefreshToken token = found.get();

        // 2. Token'ın kendisi geçerli mi?
        if (token.isRevoked()) {
            return new Rejected(Reason.REVOKED);
        }
        if (token.isUsed()) {
            // Kullanılmış token tekrar geldi: çalınmış olabilir. Aileyi kapat.
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            return new Rejected(Reason.REUSED);
        }
        if (token.isExpired(now)) {
            return new Rejected(Reason.EXPIRED);
        }

        // 3. Sahibi hâlâ refresh yapabilir mi? (spec §7.1: DEACTIVATED kullanıcı refresh yapamaz)
        Optional<User> user = userRepository.findById(token.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE);
        if (user.isEmpty()) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            return new Rejected(Reason.USER_INACTIVE);
        }

        // 4. Token'ı tüket. Yukarıdaki kontroller eşzamanlı iki isteği ayıramaz; karar bu koşullu update'te.
        //    0 dönerse aynı token'la yarışan diğer istek bizden önce tüketti → reuse.
        if (refreshTokenRepository.markUsed(token.getId(), now) == 0) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            return new Rejected(Reason.REUSED);
        }

        // 5. Aynı aileden yenisini üret.
        return new Rotated(issueInFamily(token.getUserId(), token.getFamilyId(), now), user.get());
    }

    // Logout: ailenin tamamını revoke eder. Bilinmeyen token sessizce geçilir (idempotent).
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        refreshTokenRepository.findByTokenHash(hashToken(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private IssuedRefreshToken issueInFamily(UUID userId, UUID familyId, Instant now) {
        String rawToken = generateRawToken();
        Instant expiresAt = now.plus(properties.ttl());

        refreshTokenRepository.save(RefreshToken.issue(userId, familyId, hashToken(rawToken), now, expiresAt));

        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        // URL-safe ve padding'siz: cookie değerinde kaçış gerektirmez.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // DB'de yalnız hash: DB sızsa bile token'lar kullanılamaz.
    // Token zaten 256 bit rastgele olduğu için bcrypt gibi yavaş hash'e gerek yok; SHA-256 yeterli ve
    // deterministik olduğu için hash ile arama yapılabilir.
    private static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
