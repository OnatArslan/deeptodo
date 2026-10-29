package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.user.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// DaoAuthenticationProvider'ın kullanıcı deposuna açılan iki kapısı:
// - UserDetailsService: email → kullanıcı + saklı hash (parola karşılaştırmasını provider yapar, biz değil)
// - UserDetailsPasswordService: başarılı login'de hash eski formatta ise ({bcrypt}) yenisini kaydetmek
// Bu sınıf bir bean olduğu için Spring Boot yedek in-memory kullanıcıyı ("Using generated security password") artık oluşturmaz.
//
// İkisi AuthService'te değil: AuthService AuthenticationManager'a, AuthenticationManager provider'a, provider bu
// sınıfa bağlı. AuthService bu interface'leri uygulasaydı bağımlılık kendi üstüne dönerdi (circular dependency).
// User'a UserRepository ile değil UserService ile ulaşılır (spec §5: başka feature'ın repository'si kullanılmaz).
@Service
public class AppUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UserService userService;

    public AppUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    // Kullanıcı yoksa null değil UsernameNotFoundException: provider timing attack korumasını bu exception'a bakarak yapar.
    @Override
    public UserDetails loadUserByUsername(String email) {
        return userService.findByEmail(email)
                .map(AppUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
    }

    // newPassword düz parola değil, provider'ın varsayılan encoder'la (argon2) ürettiği yeni hash.
    @Override
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        if (!(user instanceof AppUserDetails appUser)) {
            throw new IllegalArgumentException("unexpected UserDetails type: " + user.getClass().getName());
        }
        userService.changePasswordHash(appUser.id(), newPassword);
        return appUser.withPasswordHash(newPassword);
    }
}
