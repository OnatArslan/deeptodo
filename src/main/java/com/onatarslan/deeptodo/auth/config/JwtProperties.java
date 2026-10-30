package com.onatarslan.deeptodo.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// application.yaml → deeptodo.security.jwt.*. @Validated: eksik bir ayarla uygulama hiç açılmaz;
// token üretmeye çalışırken (ilk login'de) patlamaktan iyidir.
// Key yerleri Resource: "file:./secrets/..." local'de, production'da env ile başka bir yol (E4).
@Validated
@ConfigurationProperties("deeptodo.security.jwt")
public record JwtProperties(
        // Token'ı kim üretti. R5'te doğrulayıcı aynı değeri bekler.
        @NotBlank String issuer,
        // Token kimin için üretildi. Başka bir servis için üretilmiş bir token bu API'de kabul edilmesin diye (R5).
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @NotNull Resource privateKeyLocation,
        @NotNull Resource publicKeyLocation
) {
}
