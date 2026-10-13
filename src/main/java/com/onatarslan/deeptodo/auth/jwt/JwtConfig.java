package com.onatarslan.deeptodo.auth.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    RSAKey jwtSigningKey(JwtProperties properties, ResourceLoader resourceLoader) throws IOException {
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        try (InputStream in = resourceLoader.getResource(properties.privateKeyLocation()).getInputStream()){
            privateKey = RsaKeyConverters.pkcs8().convert(in);   // "BEGIN PRIVATE KEY" formatı
        }
        try (InputStream in = resourceLoader.getResource(properties.publicKeyLocation()).getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(in);     // "BEGIN PUBLIC KEY" formatı
        }
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(properties.keyId()).build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwtSigningKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey) throws JOSEException {
        // Kontrolcü SADECE public key'i alıyor (mühür örneği).
        // Private key'e ihtiyacı yok; RS256'nın asimetrik olmasının anlamı bu (T4).
        //
        // withPublicKey varsayılan olarak RS256 bekliyor. Token'ın header'ında
        // "alg": "none" ya da "HS256" yazsa bile kabul etmiyor. Algoritmayı
        // token değil doğrulayıcı belirliyor (T4).
        return NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
