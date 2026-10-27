package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.user.UserService;
import com.onatarslan.deeptodo.user.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// Authentication use-case'leri. Şimdilik yalnız register; login R3'te, token'lar R4 / R7'de eklenir.
@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    // Bilerek @Transactional değil: parola hash'leme kasıtlı olarak yavaştır. Hash'leme transaction'ın dışında yapılır,
    // DB bağlantısı yalnız UserService.register'ın kısa INSERT'i boyunca tutulur.
    public User register(String email, String rawPassword, String displayName) {
        String passwordHash = passwordEncoder.encode(rawPassword);
        return userService.register(email, displayName, passwordHash);
    }
}
