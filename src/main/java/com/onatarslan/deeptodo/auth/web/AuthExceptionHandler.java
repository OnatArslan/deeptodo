package com.onatarslan.deeptodo.auth.web;

import com.onatarslan.deeptodo.auth.exception.EmailAlreadyExistException;
import com.onatarslan.deeptodo.auth.exception.InvalidRefreshTokenException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    private final RefreshTokenCookies refreshTokenCookies;

    public AuthExceptionHandler(RefreshTokenCookies refreshTokenCookies) {
        this.refreshTokenCookies = refreshTokenCookies;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail onLoginFailure(AuthenticationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    // Hangi sebeple reddedildiği (bilinmiyor / süresi doldu / reuse) istemciye söylenmez.
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> onInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                // Geçersiz cookie istemcide kalmasın; her refresh denemesinde tekrar gönderilmesin.
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));
    }

    @ExceptionHandler(EmailAlreadyExistException.class)
    public ProblemDetail onEmailAlreadyExists(EmailAlreadyExistException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email already in use");
    }
}
