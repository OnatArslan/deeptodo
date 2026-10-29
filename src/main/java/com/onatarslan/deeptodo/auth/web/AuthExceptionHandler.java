package com.onatarslan.deeptodo.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Auth endpoint'lerinin controller içinden fırlayan hataları. Filter zincirindeki 401 / 403 buraya gelmez
// (onları SecurityProblemHandler yazar); bu, login'de AuthenticationManager'dan çıkan exception.
// GlobalExceptionHandler'da değil: security kodu common'da durmaz (spec §5).
// assignableTypes: yalnız AuthController için geçerli. Global olsaydı başka bir controller'da çıkan herhangi bir
// AuthenticationException da "Invalid email or password" olarak dönerdi; o mesaj yalnız login için doğru.
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    // Bilinmeyen email, yanlış parola, kapalı hesap: hepsi aynı cevap. Hangisi olduğunu söylersek saldırgan
    // kayıtlı email'leri ve kapalı hesapları tek tek öğrenir (user enumeration). Sebep yalnız log'da (DEBUG).
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail onAuthenticationFailure(AuthenticationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
