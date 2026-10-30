package com.onatarslan.deeptodo.auth.web;

import com.onatarslan.deeptodo.auth.AuthService;
import com.onatarslan.deeptodo.auth.token.AccessToken;
import com.onatarslan.deeptodo.auth.web.dto.LoginRequest;
import com.onatarslan.deeptodo.auth.web.dto.LoginResponse;
import com.onatarslan.deeptodo.auth.web.dto.RegisterRequest;
import com.onatarslan.deeptodo.auth.web.dto.RegisterResponse;
import com.onatarslan.deeptodo.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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

    // 200 + access token. Token'ı kabul eden taraf (resource server) R5'te gelir; o zamana kadar korumalı
    // endpoint'ler token'lı istekte de 401 döner.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AccessToken token = authService.login(request.email(), request.password());
        return ResponseEntity.ok(new LoginResponse(token.value(), "Bearer", token.expiresIn().toSeconds()));
    }
}
