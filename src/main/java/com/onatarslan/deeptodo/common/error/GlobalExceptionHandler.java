package com.onatarslan.deeptodo.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Feature'ların ortak hata sözleşmesi (spec §13). Domain exception'ları HTTP bilmez; eşleme burada.
// Validation (400) gibi Spring MVC hataları spring.mvc.problemdetails.enabled ile ProblemDetail döner.
// Security filter zincirindeki 401/403 buraya hiç gelmez: onları auth/web/SecurityProblemHandler yazar.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail onNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ProblemDetail onForbidden(ForbiddenOperationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail onConflict(BusinessRuleViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
