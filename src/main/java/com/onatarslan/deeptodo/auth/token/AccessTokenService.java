package com.onatarslan.deeptodo.auth.token;

import com.onatarslan.deeptodo.auth.config.JwtProperties;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Access token işlemleri; şimdilik yalnız üretim (G2'de jti denylist ile iptal de buraya gelir).
// Token kim olduğunu söyler, ne yapabileceğini değil: içinde rol, workspace üyeliği, email yok (spec §10: değişebilen
// yetki bilgisi JWT'ye yazılmaz; token süresi dolana kadar eskimiş bilgi taşırdı).
@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(UUID userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())                         // iss: token'ı kim üretti
                .subject(userId.toString())                          // sub: kimin adına (değişmeyen id; email değişebilir)
                .audience(List.of(properties.audience()))            // aud: hangi API için
                .issuedAt(now)                                       // iat
                .expiresAt(now.plus(properties.accessTokenTtl()))    // exp: kısa ömür; iptal edilemediği için (G2'ye kadar)
                .id(UUID.randomUUID().toString())                    // jti: bu token'a özgü id (G2 denylist bununla çalışır)
                .build();
        // Header verilmediği için encoder kendi varsayılanını kullanır: alg=RS256, typ=JWT, kid (JwtConfig).
        String value = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new AccessToken(value, properties.accessTokenTtl());
    }
}
