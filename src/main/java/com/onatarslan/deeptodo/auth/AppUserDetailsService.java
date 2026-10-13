package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // UsernameNotFoundException zorunlu: DaoAuthenticationProvider yalnız bunu yakalayınca
        // sahte bir hash karşılaştırması yapar (timing attack koruması) ve hatayı BadCredentials'a çevirir.
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("user not found");
        }
        return userRepository
                .findByEmail(User.normalizeEmail(username))
                .map(AppUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
    }
}
