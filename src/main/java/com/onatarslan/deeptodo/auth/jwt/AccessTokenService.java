package com.onatarslan.deeptodo.auth.jwt;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

// Yalnız access token (JWT) üretir. DB yok, transaction yok: imzalı token kendi kendini doğrular.
// Doğrulama tarafı JwtConfig'teki JwtDecoder; onu Spring Security'nin resource server filter'ı kullanır.
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

    public record IssuedAccessToken(String value, long expiresInSeconds) {}

    public IssuedAccessToken issue(UUID userId, Collection<String> authorities) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        // ─── Claims: kimin için, kim tarafından, ne zamana kadar ─────────────
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

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new IssuedAccessToken(token, properties.accessTokenTtl().toSeconds());
    }
}
