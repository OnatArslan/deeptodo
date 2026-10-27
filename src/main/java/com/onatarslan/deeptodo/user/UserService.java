package com.onatarslan.deeptodo.user;

import com.onatarslan.deeptodo.common.error.BusinessRuleViolationException;
import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// user feature'ının dışa açık kapısı (spec §5): başka feature'lar User'a UserRepository ile değil bu servisle ulaşır.
@Service
public class UserService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Parola hash'ini çağıran (auth) üretir; burası yalnız "bu email ile kullanıcı oluşturulabilir mi" sorusunu cevaplar.
    @Transactional
    public User register(String email, String displayName, String passwordHash) {
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

    private static BusinessRuleViolationException emailAlreadyInUse() {
        return new BusinessRuleViolationException("email already in use");
    }
}
