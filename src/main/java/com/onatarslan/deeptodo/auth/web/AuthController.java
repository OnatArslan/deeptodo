package com.onatarslan.deeptodo.auth.web;

import com.onatarslan.deeptodo.auth.AuthService;
import com.onatarslan.deeptodo.auth.web.dto.RegisterRequest;
import com.onatarslan.deeptodo.auth.web.dto.RegisterResponse;
import com.onatarslan.deeptodo.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Spec §10: register yalnız User oluşturur (workspace yok). Token'lar R4'te eklenince cevap genişleyecek.
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password(), request.displayName());
        return new RegisterResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
