# DeepToDo Spec — 07 Kalite: test, loglama, performans, bitti tanımı

> Adımlar: **Q1–Q3**. Kurallar her adımda geçerli; testlerin çoğu Q1'de yazılır.

## Test

Testler gerçek PostgreSQL'e karşı çalışır (Testcontainers). H2 kullanılmaz: CHECK'ler, ifade üzerindeki tekillik ve
composite FK'ler gerçek DB'de test edilmeden doğrulanmış sayılmaz.

| Alan | Senaryo | Beklenen |
|---|---|---|
| Auth akışı | register → login → `/me` → refresh → `/me` (yeni token) → eski refresh token tekrar | son adım 401 ve ailenin bütün token'ları iptal |
| Auth akışı | logout → aynı refresh token | 401 |
| Auth akışı | hesap kapatma → login | 401 |
| Eşzamanlılık | aynı refresh token ile 10 paralel istek | 1 × 200, 9 × 401, ailede tek halef |
| Sahiplik | Mehmet, Ayşe'nin todo / project / tag'ine GET, PATCH, DELETE ve tag takma | hepsi 404, Ayşe'nin verisi değişmemiş |
| Sahiplik | Ayşe kendi todo'suna Mehmet'in project'ini bağlamaya çalışır | 404 |
| DB | `completedAt` dolu ama durum DONE değil (doğrudan SQL ile) | DB reddeder |
| DB | başka sahibin project'i ile todo (doğrudan SQL ile) | DB reddeder (composite FK) |
| DB | aynı sahipte "Tez" ve "tez" | DB reddeder |
| Optimistic locking | aynı version ile iki PATCH | ikincisi 409 |
| Durum makinesi | `CANCELLED → DONE` | 409; `DONE → TODO` sonrası `completedAt` null |
| N+1 | 20 todo × 3 tag'lik sayfa | sorgu sayısı sayfa boyutundan bağımsız (sabit, en fazla 3) |

Unit test yalnız gerçek mantık içeren yerlerde yazılır (durum makinesi, token üretimi). Spring'in kendisini test eden test
(ör. "repository save edince kaydediyor mu") yazılmaz.

## Loglama

- **Asla loglanmaz:** parola, parola hash'i, access token, refresh token (ham ya da hash), `Authorization` ve `Cookie` header'ları.
- Login başarısızlığının gerçek sebebi (bilinmeyen email / yanlış parola / kapalı hesap) DEBUG seviyesinde, email ile birlikte.
- Beklenmeyen 500'ler ERROR seviyesinde, stack trace ile.
- SQL loglaması yalnız local profilde ve gerektiğinde açılır.

## Performans

- Todo listesi tek sayfa için sabit sayıda sorgu çalıştırır. Tag'ler ve project adı için todo başına ayrı sorgu (N+1) yok.
- Sayfa boyutu en fazla 100.
- Liste sorgusunun en sık kullanılan yolu (sahip + sıralama) index'le desteklenir. Hangi index'in gerektiğine
  `EXPLAIN ANALYZE` çıktısıyla karar verilir, tahminle değil (J5).
- Hikari bağlantı havuzu varsayılan boyutta kalır; ölçmeden değiştirilmez.

## Konfigürasyon

| Ayar | Local | Production |
|---|---|---|
| DB bağlantısı | `compose.yaml` varsayılanları | ortam değişkeni |
| JWT key'leri | `secrets/` dosyaları | ortam değişkeni / secret |
| JWT issuer | `http://localhost:8080` | public adres |
| Refresh cookie `Secure` | kapalı | açık |
| Actuator | health + gözlem endpoint'leri (local profili) | yalnız health |

Hiçbir parola, key ya da token repoya girmez.

## Bitti tanımı (v1)

1. `docker compose up` app ile PostgreSQL'i birlikte açar; app health `UP`.
2. OpenAPI arayüzünden bütün uçlar Bearer token ile denenebilir.
3. Yukarıdaki test tablosunun tamamı yeşil (`./mvnw verify`).
4. README: projeyi çalıştırma, mimari özet, önemli tasarım kararları (bu spec'in "Kararlar" bölümlerinden).
5. Spec ile kod çelişmiyor. Çelişki varsa ya kod ya spec düzeltilmiş.
