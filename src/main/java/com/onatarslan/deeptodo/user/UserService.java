package com.onatarslan.deeptodo.user;

import com.onatarslan.deeptodo.common.error.BusinessRuleViolationException;
import com.onatarslan.deeptodo.common.error.ResourceNotFoundException;
import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

// user feature'ının dışa açık kapısı (spec §5): başka feature'lar User'a UserRepository ile değil bu servisle ulaşır.
@Service
public class UserService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Register use-case'i auth'ta (AuthService.register); burası yalnız User satırını oluşturur ve
    // "bu email ile kullanıcı oluşturulabilir mi" sorusunu cevaplar. Parola hash'ini çağıran üretir.
    @Transactional
    public User create(String email, String displayName, String passwordHash) {
        String normalizedEmail = User.normalizeEmail(email);

        // Ön kontrol: yaygın durumda (email zaten kayıtlı) anlaşılır bir 409, DB hatası beklemeden.
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw emailAlreadyInUse();
        }

        try {
            // saveAndFlush: INSERT'i şimdi çalıştırır; UNIQUE ihlali burada, try içinde fırlar (commit anında değil).
            return userRepository.saveAndFlush(new User(normalizedEmail, displayName, passwordHash));
        } catch (DataIntegrityViolationException e) {
            // Ön kontrol ile INSERT arasına aynı email'le başka bir kayıt girdiyse son savunma hattı DB'nin UNIQUE'i.
            // Yalnız email constraint'i 409'a çevrilir; başka bir constraint ihlali (ör. CHECK) gerçek bir hatadır.
            if (e.getCause() instanceof ConstraintViolationException violation
                    && EMAIL_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
                throw emailAlreadyInUse();
            }
            throw e;
        }
    }

    // Login'de kayıttaki kuralla aynı normalizasyon: "Alice@X.com" ile kaydolan "alice@x.com" ile de girer.
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email));
    }

    // Dirty checking: managed entity'nin alanı değişir, commit'te UPDATE çalışır (updated_at'i auditing yazar).
    // Şu an bu metodu yalnız login'deki hash yükseltmesi çağırır; aynı kullanıcının iki eşzamanlı login'i
    // ikisi de geçerli iki hash yazar, son yazan kalır. Parola değiştirme eklenirse bu bir lost update olur
    // (eski parolanın hash'i yeni parolanın üstüne yazılabilir); o zaman koşullu UPDATE gerekir.
    @Transactional
    public void changePasswordHash(UUID userId, String newPasswordHash) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        user.changePasswordHash(newPasswordHash);
    }

    private static BusinessRuleViolationException emailAlreadyInUse() {
        return new BusinessRuleViolationException("email already in use");
    }
}
