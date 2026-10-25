package com.onatarslan.deeptodo.auth.config;

import com.onatarslan.deeptodo.auth.web.SecurityProblemHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// Kendi SecurityFilterChain bean'imiz tanımlanınca Spring Boot'un varsayılan zinciri (form login, Basic auth,
// üretilmiş parola) devre dışı kalır. Zincirde yalnız burada istediğimiz davranışlar olur.
// Henüz kimlik doğrulama mekanizması yok: korumalı her istek 401 alır. Login R3'te, Bearer token R5'te eklenir.
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problemHandler) throws Exception {
        http
                // Sunucu kimseyi hafızasında tutmaz: HttpSession açılmaz, JSESSIONID cookie'si yazılmaz.
                // Her istek kimliğini kendisi getirecek (R5: Authorization: Bearer <token>).
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF, tarayıcının bir siteye kendiliğinden eklediği kimlik bilgisini (cookie, Basic auth) kötüye kullanan
                // cross-site isteklere karşı korur. API'de kimlik Authorization header'ıyla gelecek; tarayıcı onu kendiliğinden
                // eklemez. Refresh token cookie'si geldiğinde (R7) bu karar o cookie için yeniden ele alınır.
                .csrf(AbstractHttpConfigurer::disable)

                // Varsayılan olarak açık gelen ama stateless bir API'de anlamı olmayan iki filter:
                // LogoutFilter /logout'ta session'ı kapatır (session yok; kendi logout'umuz R10'da /api/auth/logout),
                // RequestCache login'e yönlendirmeden önce isteği session'a saklar (yönlendirme ve session yok).
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Load balancer / container healthcheck'i kimliksiz çağırır.
                        .requestMatchers("/actuator/health").permitAll()
                        // Bir hata oluşunca istek /error'a forward edilir ve zincirden tekrar geçer. O aşamada kullanıcı
                        // anonim olabilir; /error'u da korursak asıl hata (ör. 403, 404) kaybolur, istemci 401 görür.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Geri kalan her şey kimlik ister. Kurallar sırayla değerlendirilir; bu satır en sonda durmalı.
                        .anyRequest().authenticated())

                // Zincirde oluşan 401 / 403 cevapları da ProblemDetail olsun. Bunlar DispatcherServlet'e hiç ulaşmaz,
                // @RestControllerAdvice onları göremez. Entry point verilmezse (form login / Basic auth da yokken)
                // Spring kimliksiz isteğe 401 değil 403 döner.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        return http.build();
    }
}
