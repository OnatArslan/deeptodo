package com.onatarslan.deeptodo.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;

// Filter zincirinde oluşan 401 / 403 cevaplarını ProblemDetail formatında yazar.
// Bu hatalar DispatcherServlet'e hiç ulaşmaz; @RestControllerAdvice onları göremez, bu yüzden burada.
//
// AuthenticationEntryPoint: kimlik yok ya da geçersiz (token eksik, bozuk, süresi dolmuş) → 401
// AccessDeniedHandler:      kimlik var ama erişim kuralı reddetti → 403
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    // Standart davranışı koruyoruz: WWW-Authenticate header'ı (RFC 6750) ve status kodunu bunlar yazar.
    // Biz yalnız gövdeyi ekliyoruz.
    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final JsonMapper jsonMapper;

    public SecurityProblemHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        bearerEntryPoint.commence(request, response, authException);
        writeProblem(request, response, HttpStatus.UNAUTHORIZED, "Missing or invalid access token");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        bearerAccessDeniedHandler.handle(request, response, accessDeniedException);
        writeProblem(request, response, HttpStatus.FORBIDDEN, "Access denied");
    }

    // GlobalExceptionHandler ile aynı ProblemDetail tipi; fark yalnız kimin serialize ettiği.
    // Orada metot ProblemDetail döner, DispatcherServlet'in message converter'ı JSON'a çevirir.
    // Burada DispatcherServlet yok: status'u, Content-Type'ı ve gövdeyi ham response'a kendimiz yazıyoruz.
    private void writeProblem(HttpServletRequest request, HttpServletResponse response,
                              HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
