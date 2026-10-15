package com.onatarslan.deeptodo.auth.domain;

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

    // Association değil scalar UUID: refresh akışı User'a navigasyon istemiyor (sub için id yeterli),
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

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

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

    // Login: yeni bir aile başlatır. Rotation: aynı familyId ile çağrılır.
    public static RefreshToken issue(UUID userId, UUID familyId, String tokenHash, Instant now, Instant expiresAt) {
        return new RefreshToken(userId, familyId, tokenHash, now, expiresAt);
    }

    // Kullanılabilir mi: hiç kullanılmamış, revoke edilmemiş ve süresi dolmamış.
    // Not: eşzamanlı iki refresh'te ikisi de burada true görebilir; rotation'ın doğruluğu
    // bu kontrole değil, repository'deki koşullu update'e ("where used_at is null") dayanacak (spec §7.9).
    public boolean isActive(Instant now) {
        return usedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedByTokenId() {
        return replacedByTokenId;
    }
}
