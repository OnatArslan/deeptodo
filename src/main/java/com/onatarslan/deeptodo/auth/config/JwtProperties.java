package com.onatarslan.deeptodo.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

// application.yaml → deeptodo.security.jwt.*. @Validated: eksik bir ayarla uygulama hiç açılmaz.
//
// Key alanları doğrudan RSAPrivateKey / RSAPublicKey: YAML'daki değeri (dosya yolu ya da "-----BEGIN ..." ile başlayan
// PEM metni) Spring Security'nin kaydettiği Converter'lar key nesnesine çevirir (RsaKeyConversionServicePostProcessor).
// Dosya yoksa ya da PEM bozuksa binding hatası: uygulama açılmaz.
@Validated
@ConfigurationProperties("deeptodo.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @NotNull RSAPrivateKey privateKey,
        @NotNull RSAPublicKey publicKey
) {
}
