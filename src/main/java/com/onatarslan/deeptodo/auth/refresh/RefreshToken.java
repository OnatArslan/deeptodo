package com.onatarslan.deeptodo.auth.refresh;

import com.onatarslan.deeptodo.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Spec §7.9. Her rotation yeni bir satır üretir; ham token DB'de tutulmaz, yalnız SHA-256 hash'i.
// Zaman alanları servisten (Clock) gelir: expiry ve reuse kararları test edilebilir olsun.
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    // Association değil scalar UUID: refresh akışı User'a navigasyon istemiyor,
    // lazy proxy / open-in-view=false tuzaklarından kaçınıyoruz. FK yine DB'de.
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // Aynı login'den doğan token zinciri; reuse tespit edilince bütün aile revoke edilir.
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    // Rotation'da set edilir (RefreshTokenRepository.markUsed). Doluysa token bir daha kullanılamaz.
    @Column(name = "used_at")
    private Instant usedAt;

    // Logout, reuse ya da deactivation'da set edilir (RefreshTokenRepository.revokeFamily).
    @Column(name = "revoked_at")
    private Instant revokedAt;

    // For JPA
    protected RefreshToken() {
    }

    private RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public static RefreshToken issue(UUID userId, UUID familyId, String tokenHash, Instant now, Instant expiresAt) {
        return new RefreshToken(userId, familyId, tokenHash, now, expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
