package com.onatarslan.deeptodo.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    // RS256: private key imzalar, public key doğrular. Private key yalnız bu uygulamada; public key'i doğrulayan
    // herkes alabilir (R5'te kendimiz). HS256'da tek bir ortak secret olurdu: doğrulayabilen herkes token da üretebilirdi.
    //
    // withKeyPair header'a alg=RS256, typ=JWT ve kid yazar. kid, public key'in RFC 7638 thumbprint'i
    // (n ve e değerlerinin SHA-256'sı): elle verilen bir isim değil, key'den türetilir; bir key'e yanlış isim verilemez.
    // R6'da birden çok key olunca doğrulayıcı key'i bu kid ile seçer.
    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) throws IOException {
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        try (InputStream in = open(properties.privateKeyLocation())) {
            privateKey = RsaKeyConverters.pkcs8().convert(in);  // "-----BEGIN PRIVATE KEY-----"
        }
        try (InputStream in = open(properties.publicKeyLocation())) {
            publicKey = RsaKeyConverters.x509().convert(in);    // "-----BEGIN PUBLIC KEY-----"
        }
        // İki dosya aynı key çiftinden değilse imzalar R5'te hiç doğrulanamaz; bunu ilk login'de değil açılışta görelim.
        // RSA'da public ve private key aynı modulus'u (n) paylaşır.
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("JWT public key does not match the private key: "
                    + properties.publicKeyLocation());
        }
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build();
    }

    // Dosya yoksa uygulama açılmaz; mesajda hangi yolun beklendiği yazar (key üretimi: CLAUDE.md → Çalıştırma).
    private static InputStream open(Resource resource) throws IOException {
        if (!resource.exists()) {
            throw new IllegalStateException("JWT key not found: " + resource.getDescription());
        }
        return resource.getInputStream();
    }
}
