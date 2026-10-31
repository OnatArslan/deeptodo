package com.onatarslan.deeptodo.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Bearer token'la gelen istekte "kim": SecurityContext'teki Authentication'ın principal'ı. Controller'lar
// @AuthenticationPrincipal CurrentUser ile alır; Jwt'yi ya da claim isimlerini bilmeleri gerekmez.
// Yalnız id taşır: email, isim, üyelik gibi bilgiler değişebilir, gerektiğinde ilgili service'ten okunur (spec §10).
public record CurrentUser(UUID id) implements OAuth2AuthenticatedPrincipal {

    // Authentication.getName() bunu döner (log'larda ve Spring'in "kim" sorduğu yerlerde görünen değer).
    @Override
    public String getName() {
        return id.toString();
    }

    // Claim'ler Jwt'de duruyor (Authentication.getCredentials()); principal'da tekrar tutmuyoruz.
    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }

    // Rol yok (yetki veridir: üyelik, sahiplik). Spring kendisi FACTOR_BEARER authority'sini ekler.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }
}
