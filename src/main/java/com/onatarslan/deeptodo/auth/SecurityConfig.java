package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.auth.web.SecurityProblemHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter,
                                           SecurityProblemHandler securityProblemHandler) throws Exception {
        http
                // Sunucu kimseyi hafızasında tutmayacak (1. mesajdaki "stateless").
                // Her istek kimliğini kendisi getirmek zorunda.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 2. mesajdaki POST → 403'ün nedeni buydu. Neden kapatabildiğimizi 21. mesajda anlatacağım.
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Kayıt ve giriş kapıları herkese açık, çünkü kartı olmayan biri kart almaya buradan gelecek
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Hata yanıtlarının doğru dönmesi için (aşağıda açıklıyorum)
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Geri kalan her şey kimlik istiyor. Bu satır en sonda durmalı.
                        .anyRequest().authenticated()
                )
                // Filter zincirindeki 401 (kimlik yok/geçersiz) ve 403 (erişim reddedildi) cevapları ProblemDetail olsun.
                // Bu hatalar controller'a ulaşmadığı için @RestControllerAdvice onları yakalayamaz.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(securityProblemHandler)
                        .accessDeniedHandler(securityProblemHandler))
                // YENİ: kimliği bulan filter artık Bearer token okuyor.
                // jwt(...) context'teki JwtDecoder bean'ini otomatik buluyor.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(securityProblemHandler)
                        .accessDeniedHandler(securityProblemHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }


    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        // Uzmanı oluşturuyoruz:görevliyi(AppUserDetailsService) ve parmak izi kontrolcüsünü veriyoruz
        var dao = new DaoAuthenticationProvider(userDetailsService);
        dao.setPasswordEncoder(passwordEncoder);
        // Masaya bu uzmanı koyuyoruz
        return new ProviderManager(dao); // provider manager birden fazla auth providera sahip olabilir
    }

    @Bean
    BearerTokenResolver bearerTokenResolver() {
        var defaultResolver = new DefaultBearerTokenResolver();

        return request -> request.getRequestURI().startsWith("/api/auth") ? null : defaultResolver.resolve(request);
    }

}
