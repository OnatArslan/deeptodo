package com.onatarslan.deeptodo.user.domain;

import com.onatarslan.deeptodo.common.domain.AuditableEntity;
import jakarta.persistence.*;

import java.util.Locale;

@Entity
@Table(name = "users")
@EntityListeners(UserExampleListener.class) // this is for example
public class User extends AuditableEntity {

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    // Parolanın kendisi değil, PasswordEncoder'ın ürettiği hash ("{argon2}$argon2id$..." ya da eski "{bcrypt}$2a$10$...").
    // Parola hiçbir yerde saklanmaz.
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    // For JPA
    protected User() {

    }

    // Entity hash'i hazır alır; hash'lemeyi bilmez (o auth'un işi). Bu sayede domain, Spring Security'ye bağımlı değil.
    public User(String email, String displayName, String passwordHash) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display name can not be null or blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("password hash can not be null or blank");
        }
        this.email = normalizeEmail(email);
        this.displayName = displayName.strip();
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
    }

    // Kaydederken ve ararken aynı kural kullanılmalı; aksi halde aynı kişi iki farklı email ile görünür.
    // Locale.ROOT: Türkçe locale'de "I".toLowerCase() noktasız "ı" üretir.
    public static String normalizeEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("email can not be null or blank");
        }
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }


}
