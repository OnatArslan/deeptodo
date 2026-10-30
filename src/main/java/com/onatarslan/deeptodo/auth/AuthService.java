package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.auth.token.AccessToken;
import com.onatarslan.deeptodo.auth.token.AccessTokenService;
import com.onatarslan.deeptodo.user.UserService;
import com.onatarslan.deeptodo.user.domain.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// Authentication use-case'leri: register ve login. Login access token döner; refresh token R7'de eklenir.
@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AccessTokenService accessTokenService;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, AccessTokenService accessTokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.accessTokenService = accessTokenService;
    }

    // Bilerek @Transactional değil: parola hash'leme kasıtlı olarak yavaştır. Hash'leme transaction'ın dışında yapılır,
    // DB bağlantısı yalnız UserService.create'in kısa INSERT'i boyunca tutulur.
    // Register use-case'i burası; UserService yalnız User satırını oluşturur. Token dönmez (spec §10: register
    // yalnız User oluşturur); istemci ardından login olur. Refresh token ile birlikte R7'de yeniden değerlendirilir.
    public User register(String email, String rawPassword, String displayName) {
        String passwordHash = passwordEncoder.encode(rawPassword);
        return userService.create(email, displayName, passwordHash);
    }

    // Aynı sebeple @Transactional değil: okuma ve (gerekirse) hash yükseltmesi ayrı, kısa transaction'lar;
    // aradaki argon2 karşılaştırması bağlantı tutmaz.
    // Kimlik hatalı ya da hesap kapalıysa AuthenticationException fırlar (AuthExceptionHandler → 401).
    public AccessToken login(String email, String rawPassword) {
        // İddia: "ben bu email'im, parolam bu". unauthenticated → henüz kimse doğrulamadı, authority yok.
        Authentication claim = UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword);
        Authentication result;
        try {
            result = authenticationManager.authenticate(claim);
        } catch (InternalAuthenticationServiceException e) {
            // Provider, kullanıcıyı yüklerken çıkan her hatayı (ör. DB'ye ulaşılamadı) bu tipe sarar; o da bir
            // AuthenticationException'dır ve 401'e dönüşürdü: istemci "parolam yanlış" sanar. Asıl hata
            // kimlik değil sunucu hatası olduğu için sarmalı açıp asıl exception'ı fırlatıyoruz (500).
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }

        // Kimlik bu noktada doğrulandı; token bunun imzalı kanıtı. Sonraki istekler parolayı değil token'ı getirir.
        AppUserDetails user = (AppUserDetails) result.getPrincipal();
        return accessTokenService.issue(user.id());
    }
}
