package com.onatarslan.deeptodo.user.web;

import com.onatarslan.deeptodo.auth.CurrentUser;
import com.onatarslan.deeptodo.user.UserService;
import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.web.dto.MeResponse;
import com.onatarslan.deeptodo.user.web.dto.UpdateMeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

// Bütün endpoint'ler "kendi hesabım" üzerinde çalışır: kimin hesabı olduğu URL'den değil token'dan gelir.
// Bu yüzden başka bir kullanıcının hesabına erişmek mümkün değil; ayrıca yetki kontrolü gerekmiyor.
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return toResponse(userService.getMe(CurrentUser.id(jwt)), authentication);
    }

    @PatchMapping
    public MeResponse update(@AuthenticationPrincipal Jwt jwt,
                             Authentication authentication,
                             @Valid @RequestBody UpdateMeRequest request) {
        return toResponse(userService.rename(CurrentUser.id(jwt), request.displayName()), authentication);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal Jwt jwt) {
        userService.deactivate(CurrentUser.id(jwt));
    }

    private static MeResponse toResponse(User user, Authentication authentication) {
        var authorities = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), authorities);
    }
}
