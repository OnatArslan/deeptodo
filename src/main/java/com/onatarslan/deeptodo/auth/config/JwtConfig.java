package com.onatarslan.deeptodo.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
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

    // RS256: private key imzalar, public key doğrular. Private key yalnız bu uygulamada; public key'i doğrulayan
    // herkes alabilir (R5'te kendimiz). HS256'da tek bir ortak secret olurdu: doğrulayabilen herkes token da üretebilirdi.
    //
    // withKeyPair header'a alg=RS256, typ=JWT ve kid yazar. kid, public key'in RFC 7638 thumbprint'i
    // (n ve e değerlerinin SHA-256'sı): elle verilen bir isim değil, key'den türetilir; bir key'e yanlış isim verilemez.
    // R6'da birden çok key olunca doğrulayıcı key'i bu kid ile seçer.
    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) throws IOException {
        RSAPrivateKey privateKey = readPrivateKey(properties.privateKeyLocation());
        RSAPublicKey publicKey = readPublicKey(properties.publicKeyLocation());
        // İki dosya aynı key çiftinden değilse imzalar hiç doğrulanamaz; bunu ilk istekte değil açılışta görelim.
        // RSA'da public ve private key aynı modulus'u (n) paylaşır.
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("JWT public key does not match the private key: "
                    + properties.publicKeyLocation());
        }
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build();
    }

    // Resource server'ın token doğrulayıcısı (BearerTokenAuthenticationFilter → JwtAuthenticationProvider → decode).
    // Sırayla: JWS'yi parse eder, header'daki alg RS256 değilse reddeder (alg=none ya da HS256 ile gelen sahte token
    // burada düşer), imzayı public key ile doğrular, sonra claim'leri aşağıdaki validator'lardan geçirir.
    // Doğrulama yalnız public key ister: ağ çağrısı yok, DB yok.
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties, Clock clock) throws IOException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(readPublicKey(properties.publicKeyLocation())).build();

        // Varsayılan JwtTimestampValidator exp claim'i hiç olmayan token'ı geçirir; bizim token'larımızda exp her zaman
        // var, olmayanı reddediyoruz. Clock: AccessTokenService ile aynı saat (testte sabitlenebilir).
        // Saat farkı payı 60 sn (varsayılan): token exp'ten sonra en fazla 60 sn daha kabul edilir.
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setAllowEmptyExpiryClaim(false);
        timestamps.setClock(clock);

        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                timestamps,
                new JwtIssuerValidator(properties.issuer()),        // iss: bu token'ı biz mi ürettik
                new JwtAudienceValidator(properties.audience())));  // aud: bu API için mi üretildi
        return decoder;
    }

    private static RSAPrivateKey readPrivateKey(Resource location) throws IOException {
        try (InputStream in = open(location)) {
            return RsaKeyConverters.pkcs8().convert(in);  // "-----BEGIN PRIVATE KEY-----"
        }
    }

    private static RSAPublicKey readPublicKey(Resource location) throws IOException {
        try (InputStream in = open(location)) {
            return RsaKeyConverters.x509().convert(in);   // "-----BEGIN PUBLIC KEY-----"
        }
    }

    // Dosya yoksa uygulama açılmaz; mesajda hangi yolun beklendiği yazar (key üretimi: CLAUDE.md → Çalıştırma).
    private static InputStream open(Resource resource) throws IOException {
        if (!resource.exists()) {
            throw new IllegalStateException("JWT key not found: " + resource.getDescription());
        }
        return resource.getInputStream();
    }
}
