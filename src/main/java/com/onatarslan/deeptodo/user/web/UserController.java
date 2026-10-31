package com.onatarslan.deeptodo.user.web;

import com.onatarslan.deeptodo.auth.CurrentUser;
import com.onatarslan.deeptodo.user.UserService;
import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.web.dto.MeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Kimin profili olduğu URL'den değil token'dan gelir (/users/{id} yok): başkasının id'sini yazarak
    // profiline ulaşmak mümkün değil. @AuthenticationPrincipal = SecurityContext'teki Authentication.getPrincipal().
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        User user = userService.getById(currentUser.id());
        return ResponseEntity.ok(new MeResponse(user.getId(), user.getEmail(), user.getDisplayName()));
    }
}
