package com.onatarslan.deeptodo.auth.token;

import com.onatarslan.deeptodo.auth.CurrentUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.UUID;

// İmzası ve claim'leri doğrulanmış Jwt → CurrentUser. JwtAuthenticationConverter, Authentication'ı kurarken
// principal'ı buradan alır (SecurityConfig).
//
// DB'ye gitmez: kimlik, imzanın kanıtladığı sub'dan gelir. Bedeli: hesabı kapatılan kullanıcının token'ı süresi
// dolana kadar (en fazla 15 dk + 60 sn saat payı) geçerli kalır. Kapatma anında refresh token'lar revoke edilir (R10),
// yeni access token alınamaz; anında iptal gerekirse jti denylist (G2).
@Component
public class CurrentUserConverter implements Converter<Jwt, OAuth2AuthenticatedPrincipal> {

    // InvalidBearerTokenException bir AuthenticationException: filter onu yakalar, 401 +
    // WWW-Authenticate: Bearer error="invalid_token" döner.
    @Override
    public OAuth2AuthenticatedPrincipal convert(Jwt jwt) {
        return new CurrentUser(parseSubject(jwt.getSubject()));
    }

    // sub'ı yalnız biz yazıyoruz (imza bunu garanti eder), ama UUID değilse 500 yerine 401 olsun.
    private static UUID parseSubject(String subject) {
        if (subject == null) {
            throw new InvalidBearerTokenException("Missing subject");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
    }
}
