package com.onatarslan.deeptodo.user.web;

import com.onatarslan.deeptodo.user.UserService;
import com.onatarslan.deeptodo.user.web.dto.MeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        var user = userService.getMe(userId);
        var authorities = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority).toList();

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                authorities

        );
    }

}
