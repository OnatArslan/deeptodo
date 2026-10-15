package com.onatarslan.deeptodo.auth.web;

import com.onatarslan.deeptodo.auth.AuthService;
import com.onatarslan.deeptodo.auth.AuthTokens;
import com.onatarslan.deeptodo.auth.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;

    public AuthController(AuthService authService, RefreshTokenCookies refreshTokenCookies) {
        this.authService = authService;
        this.refreshTokenCookies = refreshTokenCookies;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthTokens tokens = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens))
                .body(new RegisterResponse(tokens.userId(), tokens.accessToken(), "Bearer", tokens.accessTokenExpiresIn()));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withTokens(authService.login(request));
    }

    // Body yok: refresh token cookie'den okunur. Cookie yoksa servis 401'e çevirir.
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(name = "${deeptodo.security.refresh-token.cookie-name}", required = false) String refreshToken) {
        return withTokens(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "${deeptodo.security.refresh-token.cookie-name}", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }

    private ResponseEntity<TokenResponse> withTokens(AuthTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens))
                .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.accessTokenExpiresIn()));
    }

    private String refreshCookie(AuthTokens tokens) {
        return refreshTokenCookies.issue(tokens.refreshToken(), tokens.refreshTokenExpiresAt()).toString();
    }
}
