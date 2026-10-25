package com.onatarslan.deeptodo.user;

import com.onatarslan.deeptodo.common.error.ResourceNotFoundException;
import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserRepository;
import com.onatarslan.deeptodo.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User getMe(UUID userId) {
        return findUser(userId);
    }

    // Managed entity değişir; dirty checking commit'te UPDATE'i yazar, save() gerekmez.
    @Transactional
    public User rename(UUID userId, String displayName) {
        User user = findUser(userId);
        user.rename(displayName);
        return user;
    }

    // Yerel hesabı kapatır. Keycloak tarafı (disable + session'ları kapatma) K4'te eklenecek.
    @Transactional
    public void deactivate(UUID userId) {
        findUser(userId).deactivate();
    }

    // Workspace'e üye eklerken (spec §11: yalnız sistemde zaten var olan, aktif kullanıcı eklenir).
    // Diğer feature'lar User'a UserRepository ile değil bu servis üzerinden ulaşır (spec §5).
    @Transactional(readOnly = true)
    public Optional<User> findActiveByEmail(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }
}
