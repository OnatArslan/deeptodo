package com.onatarslan.deeptodo.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;

// RS256: private key imzalar (encoder), public key doğrular (decoder).
// HS256'da tek bir ortak secret olurdu: doğrulayabilen herkes token da üretebilirdi.
//
// Key rotation: yeni key çiftini yapılandır, uygulamayı yeniden başlat. Eski key'le imzalanmış access token'lar
// 401 alır; istemci refresh token ile (R7) yeni key'le imzalı bir token alır. Tek instance'ta geçiş süresi gerekmez.
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    // NIST / OWASP'ın RSA için önerdiği en küçük boyut.
    private static final int MIN_RSA_KEY_BITS = 2048;

    // İmza key'i: key çifti + kid. kid, public key'in RFC 7638 thumbprint'i (n ve e'nin SHA-256'sı): key değişince
    // kendiliğinden değişir, elle verilen bir isim gibi eski değerde unutulamaz.
    @Bean
    RSAKey jwtSigningKey(JwtProperties properties) throws JOSEException {
        RSAPrivateKey privateKey = properties.privateKey();
        RSAPublicKey publicKey = properties.publicKey();

        // Yanlış eşleşmiş dosyalarla açılırsak token'lar üretilir ama hiçbiri doğrulanamaz; bunu açılışta görelim.
        // RSA'da public ve private key aynı modulus'u (n) paylaşır.
        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalStateException("JWT public and private keys do not belong to the same key pair");
        }
        int bits = publicKey.getModulus().bitLength();
        if (bits < MIN_RSA_KEY_BITS) {
            throw new IllegalStateException("JWT RSA key must be at least " + MIN_RSA_KEY_BITS + " bits, got " + bits);
        }

        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
    }

    // Encoder, header'daki alg'a (RS256) uyan key'i set'ten seçer ve o key'in kid'ini header'a yazar.
    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    // Resource server (SecurityConfig) gelen Bearer token'ları bununla doğrular. Yalnız public key verilir.
    // Sıra: parse → alg RS256 mi → imza → claim validator'ları. Ağ çağrısı yok, DB yok.
    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties, Clock clock) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey())
                // Header'da başka bir alg (none, HS256) yazsa da kabul edilmez: alg confusion saldırısına karşı.
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        // Varsayılan JwtTimestampValidator exp'i olmayan token'ı geçirir; biz exp'i zorunlu tutuyoruz.
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
}
