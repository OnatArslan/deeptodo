package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.auth.exception.InvalidRefreshTokenException;
import com.onatarslan.deeptodo.auth.token.AccessToken;
import com.onatarslan.deeptodo.auth.token.AccessTokenService;
import com.onatarslan.deeptodo.auth.token.RefreshTokenService;
import com.onatarslan.deeptodo.user.UserService;
import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Authentication use-case'leri: register, login, refresh. Login ve refresh access token + refresh token döner.
@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, AccessTokenService accessTokenService,
                       RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    // Bilerek @Transactional değil: parola hash'leme kasıtlı olarak yavaştır. Hash'leme transaction'ın dışında yapılır,
    // DB bağlantısı yalnız UserService.create'in kısa INSERT'i boyunca tutulur.
    // Register use-case'i burası; UserService yalnız User satırını oluşturur. Token dönmez (spec §10: register
    // yalnız User oluşturur); istemci ardından login olur. Refresh token da yalnız login'de üretilir.
    public User register(String email, String rawPassword, String displayName) {
        String passwordHash = passwordEncoder.encode(rawPassword);
        return userService.create(email, displayName, passwordHash);
    }

    // Aynı sebeple @Transactional değil: okuma ve (gerekirse) hash yükseltmesi ayrı, kısa transaction'lar;
    // aradaki argon2 karşılaştırması bağlantı tutmaz.
    // Kimlik hatalı ya da hesap kapalıysa AuthenticationException fırlar (AuthExceptionHandler → 401).
    public AuthTokens login(String email, String rawPassword) {
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

        // Kimlik bu noktada doğrulandı. Access token (15 dk) sonraki isteklerin kimliği; refresh token (14 gün) süresi
        // dolan access token'ın parola sormadan yenilenmesi için (R8). Refresh satırı kendi kısa transaction'ında yazılır.
        AppUserDetails user = (AppUserDetails) result.getPrincipal();
        AccessToken accessToken = accessTokenService.issue(user.id());
        String refreshToken = refreshTokenService.issueForNewFamily(user.id());
        return new AuthTokens(accessToken, refreshToken);
    }

    // Parolasız yenileme: cookie'deki refresh token → yeni access token + yeni refresh token.
    // Tek transaction: markUsed, halefin INSERT'i ve kullanıcı kontrolü birlikte commit olur ya da birlikte geri alınır.
    // Login'den farkı: argon2 yok, transaction kısa (birkaç SQL + ~1 ms imza).
    //
    // Kullanıcı durumuna burada bakılır (her API isteğinde değil, R5 kararı): kapatılan hesap en geç access token'ın
    // süresi dolunca dışarıda kalır, çünkü yenileyemez. Reddedince rollback: token harcanmamış olarak kalır.
    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = userService.getById(rotation.userId());
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRefreshTokenException("user is not active");
        }
        return new AuthTokens(accessTokenService.issue(user.getId()), rotation.successor());
    }
}
