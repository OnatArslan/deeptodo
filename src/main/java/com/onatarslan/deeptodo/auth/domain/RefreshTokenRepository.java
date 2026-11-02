package com.onatarslan.deeptodo.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Koşullu UPDATE: "hâlâ kullanılabilir durumdaysa kullanılmış işaretle", tek SQL cümlesinde. Dönen değer
    // etkilenen satır sayısı: 1 = bu istek token'ı aldı, 0 = token yok / kullanılmış / revoke / süresi dolmuş.
    //
    // Eşzamanlı iki istek aynı token'la gelirse: ikisi de UPDATE çalıştırır; PostgreSQL ilkinin kilitlediği satırda
    // ikinciyi bekletir. İlki commit edince ikinci WHERE'i yeniden değerlendirir, used_at artık dolu → 0 satır.
    // Yalnız biri kazanır; "önce SELECT, sonra Java'da kontrol, sonra UPDATE" yapsaydık ikisi de kazanabilirdi.
    @Modifying
    @Query("""
            update RefreshToken t set t.usedAt = :now
            where t.tokenHash = :tokenHash
              and t.usedAt is null
              and t.revokedAt is null
              and t.expiresAt > :now
            """)
    int markUsed(String tokenHash, Instant now);

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
