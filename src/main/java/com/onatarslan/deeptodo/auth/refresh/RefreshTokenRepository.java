package com.onatarslan.deeptodo.auth.refresh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Rotation'ın doğruluğu bu koşullu update'e dayanır (spec §7.9).
    // Aynı token'la eşzamanlı iki istek gelirse PostgreSQL ikinciyi satır kilidinde bekletir;
    // birincisi commit edince WHERE'i yeniden değerlendirir, used_at artık dolu olduğu için 0 döner.
    // 1 = bu istek token'ı kullandı, 0 = başkası zaten kullanmış ya da revoke edilmiş.
    @Modifying
    @Query("""
            update RefreshToken t
               set t.usedAt = :now
             where t.id = :id
               and t.usedAt is null
               and t.revokedAt is null
            """)
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    // Reuse tespiti ve logout: ailenin henüz revoke edilmemiş bütün token'ları.
    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :now
             where t.familyId = :familyId
               and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
