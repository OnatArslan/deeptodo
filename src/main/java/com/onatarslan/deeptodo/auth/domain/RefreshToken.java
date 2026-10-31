package com.onatarslan.deeptodo.auth.domain;

import com.onatarslan.deeptodo.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Bir refresh token = bir satır (spec §7.9). Token'ın kendisi değil, SHA-256 hash'i saklanır: DB sızsa da
// satırlardan geçerli bir token elde edilemez.
//
// family_id: aynı login'den rotation ile (R8) türeyen bütün token'lar aynı aileyi paylaşır. Bir aile = bir oturum;
// reuse detection (R9) ve logout aileyi toptan revoke eder.
// AuditableEntity değil: tabloda updated_at yok; satır oluştuktan sonra yalnız used_at / revoked_at değişir.
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    // Scalar FK: token'dan User'a navigasyon gerekmiyor (CLAUDE.md → JPA).
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    // R8: rotation'da set edilir (koşullu UPDATE ile).
    @Column(name = "used_at")
    private Instant usedAt;

    // R9: reuse detection / logout'ta set edilir.
    @Column(name = "revoked_at")
    private Instant revokedAt;

    // For JPA
    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
