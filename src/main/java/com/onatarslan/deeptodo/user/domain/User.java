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

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    // For JPA
    protected User() {

    }

    public User(String email, String passwordHash, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display name can not be null or blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("password hash can not be null or blank");
        }
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName.strip();
        this.status = UserStatus.ACTIVE;
    }



    public void changePasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("password hash can not be null or blank");
        }
        this.passwordHash = newPasswordHash;
    }

    // Kaydederken ve ararken aynı kural kullanılmalı; aksi halde kayıtlı kullanıcı login olamaz.
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
