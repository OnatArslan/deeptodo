package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.auth.web.SecurityProblemHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// Uygulama yalnız OAuth2 Resource Server: token'ları Keycloak üretir, burada sadece doğrulanır.
// Login, register, refresh, parola gibi bir kod yok (spec §10).
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                           SecurityProblemHandler securityProblemHandler) throws Exception {
        http
                // Sunucu kimseyi hafızasında tutmaz; her istek kimliğini Bearer token ile getirir.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF, tarayıcının kendiliğinden gönderdiği kimlik bilgisine (cookie) karşı korur.
                // Kimlik yalnız Authorization header'ında; tarayıcı onu kendiliğinden eklemez.
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Hata cevapları /error'a forward edilir; orada tekrar 401 üretilmesin.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Geri kalan her şey kimlik istiyor. Bu satır en sonda durmalı.
                        .anyRequest().authenticated()
                )
                // Filter zincirindeki 401 (kimlik yok/geçersiz) ve 403 (erişim reddedildi) cevapları ProblemDetail olsun.
                // Bu hatalar controller'a ulaşmadığı için @RestControllerAdvice onları yakalayamaz.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(securityProblemHandler)
                        .accessDeniedHandler(securityProblemHandler))
                // JwtDecoder'ı Spring Boot, spring.security.oauth2.resourceserver.jwt.issuer-uri'den kurar.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(securityProblemHandler)
                        .accessDeniedHandler(securityProblemHandler)
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }
}
