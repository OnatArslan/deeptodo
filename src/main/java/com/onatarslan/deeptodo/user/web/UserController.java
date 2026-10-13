package com.onatarslan.deeptodo.user.web;

import com.onatarslan.deeptodo.user.web.dto.MeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {



    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return new MeResponse(
                authentication.getName(),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
        );
    }

}
