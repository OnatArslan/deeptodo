package com.onatarslan.deeptodo.auth.jwt;

import com.onatarslan.deeptodo.auth.domain.RefreshToken;
import com.onatarslan.deeptodo.auth.domain.RefreshTokenRepository;
import com.onatarslan.deeptodo.auth.exception.InvalidRefreshTokenException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TokenService {

    // 32 byte = 256 bit rastgelelik; tahmin edilemez opaque token.
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final RefreshTokenProperties refreshProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder,
                        JwtProperties properties,
                        RefreshTokenProperties refreshProperties,
                        RefreshTokenRepository refreshTokenRepository,
                        Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.refreshProperties = refreshProperties;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {}

    // value: istemciye giden ham token (DB'de yok). familyId: logout / deactivation'ın revoke ettiği zincir.
    public record IssuedRefreshToken(String value, UUID userId, UUID familyId, Instant expiresAt) {}

    public IssuedAccessToken issueAccessToken(UUID userId, Collection<String> authorities) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("authorities", authorities)
                .build();

        // ─── Header: nasıl mühürlendiği ──────────────────────────────────────
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)   // alg
                .keyId(properties.keyId())                                  // kid
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();

        return new IssuedAccessToken(token, expiresAt);
    }

    // Login / register: yeni bir aile başlatır.
    @Transactional
    public IssuedRefreshToken issueRefreshToken(UUID userId) {
        return issueInFamily(userId, UUID.randomUUID(), clock.instant()).toIssued();
    }

    // Refresh: sunulan token'ı tüketir, aynı aileden bir halef üretir.
    //
    // noRollbackFor: reuse tespitinde aileyi revoke edip exception fırlatıyoruz. Varsayılan davranışta
    // RuntimeException transaction'ı rollback eder ve revoke da geri alınırdı; saldırgan token'ı kullanmaya devam ederdi.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedRefreshToken rotateRefreshToken(String rawToken) {
        Instant now = clock.instant();

        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("unknown refresh token"));

        if (current.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException("refresh token revoked");
        }
        if (current.getUsedAt() != null) {
            // Zaten kullanılmış token tekrar geldi: token çalınmış olabilir (katı reuse detection, spec §7.9).
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException("refresh token reuse detected");
        }
        if (!now.isBefore(current.getExpiresAt())) {
            throw new InvalidRefreshTokenException("refresh token expired");
        }

        // Yukarıdaki kontroller eşzamanlı istekleri ayıramaz; karar bu koşullu update'te.
        if (refreshTokenRepository.markUsed(current.getId(), now) == 0) {
            // Aynı token'la yarışan diğer istek bizden önce davrandı → reuse.
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException("refresh token reuse detected");
        }

        NewRefreshToken successor = issueInFamily(current.getUserId(), current.getFamilyId(), now);
        // FK (replaced_by → refresh_tokens.id) için halef önce INSERT edilmiş olmalı; issueInFamily flush ediyor.
        refreshTokenRepository.linkSuccessor(current.getId(), successor.entity().getId());
        return successor.toIssued();
    }

    // Logout: ailenin tamamını revoke eder. Bilinmeyen / zaten revoke edilmiş token sessizce geçilir (idempotent).
    @Transactional
    public void revokeFamily(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeFamily(UUID familyId) {
        refreshTokenRepository.revokeFamily(familyId, clock.instant());
    }

    // Ham token ile kaydedilen entity'yi birlikte taşır; ham değer yalnız bellekte, istemciye dönmek için.
    private record NewRefreshToken(String rawToken, RefreshToken entity) {
        IssuedRefreshToken toIssued() {
            return new IssuedRefreshToken(rawToken, entity.getUserId(), entity.getFamilyId(), entity.getExpiresAt());
        }
    }

    private NewRefreshToken issueInFamily(UUID userId, UUID familyId, Instant now) {
        String rawToken = generateRawToken();
        Instant expiresAt = now.plus(refreshProperties.ttl());

        RefreshToken entity = RefreshToken.issue(userId, familyId, hash(rawToken), now, expiresAt);
        refreshTokenRepository.saveAndFlush(entity);

        return new NewRefreshToken(rawToken, entity);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        // URL-safe ve padding'siz: cookie değerinde kaçış gerektirmez.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // DB'de yalnız hash: DB sızsa bile token'lar kullanılamaz (spec §7.9).
    // Token zaten 256 bit rastgele olduğu için bcrypt gibi yavaş hash'e gerek yok; SHA-256 yeterli ve
    // deterministik olduğu için hash ile arama yapılabilir.
    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
