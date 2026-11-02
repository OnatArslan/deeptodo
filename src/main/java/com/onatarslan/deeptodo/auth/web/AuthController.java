package com.onatarslan.deeptodo.auth.web;

import com.onatarslan.deeptodo.auth.AuthService;
import com.onatarslan.deeptodo.auth.AuthTokens;
import com.onatarslan.deeptodo.auth.config.RefreshTokenProperties;
import com.onatarslan.deeptodo.auth.exception.InvalidRefreshTokenException;
import com.onatarslan.deeptodo.auth.token.AccessToken;
import com.onatarslan.deeptodo.auth.web.dto.LoginRequest;
import com.onatarslan.deeptodo.auth.web.dto.LoginResponse;
import com.onatarslan.deeptodo.auth.web.dto.RegisterRequest;
import com.onatarslan.deeptodo.auth.web.dto.RegisterResponse;
import com.onatarslan.deeptodo.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthService authService;
    private final RefreshTokenProperties refreshTokenProperties;

    public AuthController(AuthService authService, RefreshTokenProperties refreshTokenProperties) {
        this.authService = authService;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    // Spec §10: register yalnız User oluşturur (workspace yok, token yok); istemci ardından login olur.
    // 201 ama Location header'ı yok: oluşan kullanıcının adresleyebileceğimiz bir URL'i yok (/api/users/{id} açılmayacak;
    // kullanıcı kendine R5'teki /api/users/me ile token'la ulaşır).
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password(), request.displayName());
        RegisterResponse body = new RegisterResponse(user.getId(), user.getEmail(), user.getDisplayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // 200: access token gövdede (istemci Authorization header'ına koyar), refresh token Set-Cookie'de.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(authService.login(request.email(), request.password()));
    }

    // Gövde yok: kimlik cookie'deki refresh token. Cevap login ile aynı şekilde (yeni access token + yeni cookie);
    // tarayıcı eski cookie'nin üstüne yazar. Cookie yoksa 401 (aynı cevap: AuthExceptionHandler).
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("refresh token cookie is missing");
        }
        return tokenResponse(authService.refresh(refreshToken));
    }

    private ResponseEntity<LoginResponse> tokenResponse(AuthTokens tokens) {
        AccessToken accessToken = tokens.accessToken();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(tokens.refreshToken()).toString())
                .body(new LoginResponse(accessToken.value(), "Bearer", accessToken.expiresIn().toSeconds()));
    }

    // Refresh token'ın neden cookie'de olduğu, bayrak bayrak:
    //   HttpOnly        JavaScript okuyamaz: sayfaya XSS ile sızan bir script token'ı çalamaz (access token'ı çalabilir,
    //                   ama o 15 dk'da ölür).
    //   SameSite=Strict başka bir siteden başlatılan isteğe tarayıcı cookie'yi eklemez: CSRF koruması (spec §10).
    //   Path=/api/auth  cookie yalnız /api/auth/** isteklerine gider; diğer API çağrılarında ağda dolaşmaz.
    //   Secure          yalnız HTTPS (production'da true, local HTTP'de false).
    //   Max-Age         token'ın DB'deki ömrüyle aynı; süresi dolunca tarayıcı da siler.
    private ResponseCookie refreshTokenCookie(String value) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(refreshTokenProperties.cookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(refreshTokenProperties.ttl())
                .build();
    }
}
