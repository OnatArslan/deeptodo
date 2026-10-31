package com.onatarslan.deeptodo.auth.config;

import com.onatarslan.deeptodo.auth.AppUserDetailsService;
import com.onatarslan.deeptodo.auth.token.CurrentUserConverter;
import com.onatarslan.deeptodo.auth.web.SecurityProblemHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

// Kendi SecurityFilterChain bean'imiz tanımlanınca Spring Boot'un varsayılan zinciri (form login, Basic auth,
// üretilmiş parola) devre dışı kalır. Zincirde yalnız burada istediğimiz davranışlar olur.
// Login (R3) zincirde bir filter değil, sıradan bir endpoint: kimliği doğrular ve access token döner (R4).
// Sonraki istekler kimliği token'la getirir; onu zincirdeki BearerTokenAuthenticationFilter doğrular (R5).
@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problemHandler,
                                            JwtDecoder jwtDecoder, CurrentUserConverter currentUserConverter) throws Exception {
        // Spring'in varsayılan converter'ı; yalnız principal'ı değiştiriyoruz: controller'lar Jwt'yi değil
        // CurrentUser'ı görür. Authority'leri yine o kurar: scope / scp claim'i yok → boş, artı FACTOR_BEARER.
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtPrincipalConverter(currentUserConverter);

        http
                // Sunucu kimseyi hafızasında tutmaz: HttpSession açılmaz, JSESSIONID cookie'si yazılmaz.
                // Her istek kimliğini kendisi getirecek (R5: Authorization: Bearer <token>).
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF, tarayıcının bir siteye kendiliğinden eklediği kimlik bilgisini (cookie, Basic auth) kötüye kullanan
                // cross-site isteklere karşı korur. API'de kimlik Authorization header'ıyla gelecek; tarayıcı onu kendiliğinden
                // eklemez. Refresh token cookie'si (R7) ise tarayıcının kendiliğinden eklediği bir kimlik; onu CSRF token'ı
                // yerine SameSite=Strict korur: başka bir siteden başlatılan isteğe tarayıcı cookie'yi eklemez (spec §10).
                .csrf(AbstractHttpConfigurer::disable)

                // Varsayılan olarak açık gelen ama stateless bir API'de anlamı olmayan iki filter:
                // LogoutFilter /logout'ta session'ı kapatır (session yok; kendi logout'umuz R10'da /api/auth/logout),
                // RequestCache login'e yönlendirmeden önce isteği session'a saklar (yönlendirme ve session yok).
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Load balancer / container healthcheck'i kimliksiz çağırır.
                        .requestMatchers("/actuator/health").permitAll()
                        // Kaydolan / giriş yapan kişinin henüz kimliği yok; yalnız bu yöntem + yol açık (GET değil).
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        // Bir hata oluşunca istek /error'a forward edilir ve zincirden tekrar geçer. O aşamada kullanıcı
                        // anonim olabilir; /error'u da korursak asıl hata (ör. 403, 404) kaybolur, istemci 401 görür.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Geri kalan her şey kimlik ister. Kurallar sırayla değerlendirilir; bu satır en sonda durmalı.
                        .anyRequest().authenticated())

                // Resource server: BearerTokenAuthenticationFilter'ı zincire ekler. "Authorization: Bearer <token>" varsa
                // token'ı JwtDecoder'a (imza + iss / aud / exp, JwtConfig) verir, başarılıysa Authentication'ı
                // SecurityContext'e koyar. Header yoksa hiçbir şey yapmaz; istek anonim devam eder ve
                // AuthorizationFilter'da 401 alır. Token geçersizse 401'i filter'ın kendisi döner, entry point
                // olarak bizim handler'ımızla (verilmezse Spring'in gövdesiz BearerTokenAuthenticationEntryPoint'i).
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(problemHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(authenticationConverter))
                        // Security 7, GET /.well-known/oauth-protected-resource'ta API'yi tarif eden public bir JSON
                        // yayınlar (RFC 9728; 401'deki resource_metadata bunu gösterir). Varsayılanı token'ların istemci
                        // sertifikasına bağlı olduğunu (mTLS) söylüyor; bizde öyle değil.
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(
                                builder -> builder.tlsClientCertificateBoundAccessTokens(false))))

                // Zincirde oluşan 401 / 403 cevapları da ProblemDetail olsun. Bunlar DispatcherServlet'e hiç ulaşmaz,
                // @RestControllerAdvice onları göremez. Entry point verilmezse (form login / Basic auth da yokken)
                // Spring kimliksiz isteğe 401 değil 403 döner.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        return http.build();
    }

    // Email + parola doğrulamasının parçaları açıkça kuruluyor (Spring'in otomatik kurduğu global manager'a güvenmek yerine):
    //   AuthenticationManager (ProviderManager) → token tipini destekleyen provider'a sorar
    //   DaoAuthenticationProvider → kullanıcıyı yükler, durumunu ve parolayı kontrol eder, gerekirse hash'i yükseltir
    // Bilinmeyen email'de de saklı bir sahte hash'e karşı parola karşılaştırması yapar: cevap süresi email'in
    // kayıtlı olup olmadığını ele vermez (timing attack). Kapalı hesapta da parola kontrolü yine çalışır
    // (alwaysPerformAdditionalChecksOnUser, Security 7'de varsayılan true).
    @Bean
    AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Verilmezse yükseltme yapılmaz (varsayılanı NOOP); {bcrypt} hash'ler sonsuza kadar bcrypt kalır.
        provider.setUserDetailsPasswordService(userDetailsService);
        return new ProviderManager(provider);
    }

    // Delegating encoder: yeni hash'i "argon2" ile üretir ve başına id'sini yazar ("{argon2}$argon2id$..."),
    // doğrularken hash'in başındaki id'ye göre encoder seçer. R2'de üretilmiş "{bcrypt}" hash'ler bu yüzden geçerli kalır;
    // upgradeEncoding() id'si "argon2" olmayan hash için true döner → login'de argon2'ye yükseltilir.
    //
    // PasswordEncoderFactories yerine haritayı kendimiz kuruyoruz: yalnız gerçekten kullandığımız iki algoritma.
    // Fabrika {noop} (düz metin), {MD4}, {sha256} gibi eski / güvensiz id'leri de tanır; DB'ye öyle bir değer
    // sızarsa (ör. elle yazılmış bir satır) kabul edilirdi.
    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = Map.of(
                "argon2", argon2(),
                "bcrypt", new BCryptPasswordEncoder());
        return new DelegatingPasswordEncoder("argon2", encoders);
    }

    // Argon2id, OWASP Password Storage Cheat Sheet'in önerdiği en düşük ayar: 19 MiB bellek, 2 tur, 1 paralel hat.
    // Bellek maliyeti, GPU / ASIC ile paralel kırmayı pahalılaştıran şey; bcrypt'te bu yok (yalnız CPU maliyeti).
    // Bedeli: her hash'leme sunucuda da 19 MiB ister. Eşzamanlı çok sayıda login belleği zorlar (R11 rate limit).
    private static Argon2PasswordEncoder argon2() {
        int saltLength = 16;        // byte
        int hashLength = 32;        // byte
        int parallelism = 1;
        int memoryKib = 19 * 1024;  // 19 MiB
        int iterations = 2;
        return new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memoryKib, iterations);
    }
}
