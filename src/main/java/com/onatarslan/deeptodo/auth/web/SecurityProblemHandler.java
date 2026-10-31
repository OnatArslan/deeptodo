package com.onatarslan.deeptodo.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;

// Filter zincirinde oluşan 401 / 403 cevaplarını ProblemDetail olarak yazar.
// ExceptionTranslationFilter bir AuthenticationException görürse commence(), AccessDeniedException görürse
// (kullanıcı kimlikliyse) handle() çağırır. İkisi de controller'dan önce olur; DispatcherServlet devrede değildir.
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    // Status'u ve WWW-Authenticate header'ını Spring'in Bearer entry point'i yazar (RFC 6750 formatı); gövde yazmaz.
    private final BearerTokenAuthenticationEntryPoint bearerHeaders = new BearerTokenAuthenticationEntryPoint();

    public SecurityProblemHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    // 401: kimlik yok ya da geçersiz. İki yerden çağrılır:
    //   token hiç yok → AuthorizationFilter reddeder → ExceptionTranslationFilter → buraya
    //   token geçersiz → BearerTokenAuthenticationFilter doğrudan buraya (exception OAuth2AuthenticationException)
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // RFC 9110: 401 cevabı, istemciye hangi şemayla kimlik getireceğini WWW-Authenticate ile söyler.
        // Token yoksa: Bearer resource_metadata="…"; geçersizse ek olarak error="invalid_token" ve sebebi
        // (error_description: "Jwt expired at …" gibi). Sebep header'da; gövde genel kalır.
        bearerHeaders.commence(request, response, authException);
        String detail = authException instanceof OAuth2AuthenticationException
                ? "Invalid or expired access token"
                : "Authentication required";
        writeProblem(request, response, HttpStatus.UNAUTHORIZED, detail);
    }

    // 403: kimlik var ama bu işleme izin yok.
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
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
