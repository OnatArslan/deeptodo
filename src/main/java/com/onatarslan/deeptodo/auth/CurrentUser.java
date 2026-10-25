package com.onatarslan.deeptodo.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

// Controller'da kimin istek attığını bulmanın tek yolu:
//
//   public X handler(@AuthenticationPrincipal Jwt jwt) {
//       UUID userId = CurrentUser.id(jwt);
//
// @AuthenticationPrincipal, SecurityContext'teki Authentication'ın principal'ını parametreye koyar.
// Resource server akışında principal doğrulanmış Jwt'dir; sub claim'i AccessTokenService'in yazdığı userId.
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
