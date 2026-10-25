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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    // For JPA
    protected User() {

    }

    // Parola Keycloak'ta. users.password_hash kolonu (V1, NOT NULL) K3 migration'ında kaldırılır;
    // o zamana kadar kullanıcı oluşturan bir akış yok.
    public User(String email, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display name can not be null or blank");
        }
        this.email = normalizeEmail(email);
        this.displayName = displayName.strip();
        this.status = UserStatus.ACTIVE;
    }

    // PATCH /api/users/me: yalnız düşük riskli profil alanı (spec §11).
    public void rename(String newDisplayName) {
        if (newDisplayName == null || newDisplayName.isBlank()) {
            throw new IllegalArgumentException("display name can not be null or blank");
        }
        this.displayName = newDisplayName.strip();
    }

    // DELETE /api/users/me: hard delete yok, hesap kapatılır (spec §7.1, §14).
    public void deactivate() {
        this.status = UserStatus.DEACTIVATED;
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

    public UserStatus getStatus() {
        return status;
    }


}
