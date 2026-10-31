package com.onatarslan.deeptodo.auth.token;

import com.onatarslan.deeptodo.auth.config.RefreshTokenProperties;
import com.onatarslan.deeptodo.auth.domain.RefreshToken;
import com.onatarslan.deeptodo.auth.domain.RefreshTokenRepository;
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
import java.util.UUID;

// Refresh token: access token'dan farklı olarak JWT değil, anlamı olmayan rastgele bir değer (opaque).
// İçinde claim yok; ne olduğunu yalnız DB bilir. Bu yüzden iptal edilebilir: satırı revoke etmek yeter (R9, R10).
@Service
public class RefreshTokenService {

    // 256 bit: tahmin etmek (brute force) pratikte imkânsız.
    private static final int TOKEN_BYTES = 32;

    // SecureRandom: işletim sisteminin kriptografik rastgele kaynağı. java.util.Random tahmin edilebilir, kullanılmaz.
    // Thread-safe; tek instance paylaşılır.
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    // Login'de çağrılır: yeni bir aile (oturum) başlatır. Dönen ham değer yalnız cookie'ye yazılır; DB'de hash'i kalır.
    @Transactional
    public String issueForNewFamily(UUID userId) {
        String rawToken = generate();
        Instant now = clock.instant();
        repository.save(new RefreshToken(userId, UUID.randomUUID(), hash(rawToken), now, now.plus(properties.ttl())));
        return rawToken;
    }

    // 32 byte → base64url (padding'siz) → 43 karakter; cookie'ye ve URL'e kaçış gerektirmeden yazılabilir.
    private static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Parola gibi argon2 değil, düz SHA-256: token zaten 256 bit rastgele, kırılacak bir "zayıf parola" yok.
    // Ayrıca salt'sız olduğu için deterministik: gelen token'ın hash'i ile DB'de doğrudan arama yapılabilir
    // (uq_refresh_tokens_token_hash index'i). Salt'lı bir hash'le önce satırı bulmak mümkün olmazdı.
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);   // 64 hex karakter = varchar(64)
        } catch (NoSuchAlgorithmException e) {
            // Her JVM SHA-256'yı desteklemek zorunda (Java SE spec); buraya gelinmez.
            throw new IllegalStateException(e);
        }
    }
}
