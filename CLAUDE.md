# CLAUDE.md — DeepToDo

DeepToDo, workspace tabanlı collaborative task management backend'i (Java 21, Spring Boot 4.1).
Proje **agentic** geliştirilir: `ROADMAP.md`'deki adımları Claude uygular (kod, doğrulama, PR, merge).

Normal agentic projelerden tek farkı: büyük ve kapsamlı PR'lar yerine **her prompt'ta tek bir küçük adım**
ve o adımın **chat'te anlatılması**. Onat kodu okur, "bu neden böyle?" diye sorar; Claude anlatır.
Onat'ın her adımı anlayabilmesi, adımın çalışması kadar önemlidir.

Güncel durum ve yol haritası (her oturumda yüklenir):

@STATUS.md

@ROADMAP.md

Hedef mimari ve kapsam `PROJECT_SPEC.md`'de (uzun). Bütününü okuma; işle ilgili bölümü oku.
Doküman ile kod çelişirse sessizce düzeltme; çelişkiyi açıkça söyle.

---

## Roller

**Claude — geliştirici ve anlatıcı.** Roadmap'teki sıradaki adımı planlar, kodu ve migration'ı yazar,
doğrular, PR açar, `develop`'a **merge eder** ve chat'te ne yaptığını ve nedenini anlatır.

**Onat — product owner, okuyucu, öğrenen.** Prompt ile ilerletir ("devam" ya da belirli bir adım),
kodu okur, soru sorar. Kapsam ve mimari kararlar Onat'ındır.

**Onay gerektirenler (Onat'a tek satır sor, bekle):** `develop` → `main` release, production deploy / production'a
dokunan her işlem, uygulanmış (merge edilmiş) bir migration'ı değiştirmek, kapsam değişikliği.

---

## PR akışı

### Branch modeli

```text
main      ← yalnız Onat'ın kararıyla develop'tan alınır (release)
develop   ← bütün PR'ların hedefi
feat/<kısa-ad>, fix/<kısa-ad>, docs/<kısa-ad>, chore/<kısa-ad>   ← her task için bir branch, develop'tan açılır
```

### Bir prompt = bir roadmap adımı = bir PR

- Bir PR, Onat'ın tek oturuşta okuyup anlayabileceği büyüklüktedir: **yaklaşık 300 satır değişiklik ve 10 dosyayı geçmez**
  (migration ve test dahil). Geçecekse işi böl ve önce bölme planını göster.
- Bir PR tek bir şey öğretir. "Rate limiter + token denylist + refresh refactor" üç PR'dır.
- Adım bitince PR merge edilir (squash); bir sonraki prompt bir sonraki adımı açar. Aynı anda tek açık PR olur.
- Kendiliğinden ilgisiz refactor, yeni dependency ya da kapsam dışı iş ekleme. Gerekli görürsen PR açıklamasında
  "Sonraki adım önerisi" olarak yaz; karar Onat'ın.

### Her PR'dan önce

1. İlgili kodu ve spec bölümünü oku.
2. Kısa bir plan yaz: ne değişecek, hangi dosyalar, neden bu sırayla. Mimari ya da kapsam kararı gerekiyorsa
   önce Onat'a sor (tek satır, seçenek + önerin).

### Her PR'da

- Kod + gerekiyorsa migration.
- **Doğrulama:** derleme, uygulamanın açılması, migration'ların çalışması, değişen davranışın elle (curl) denenmesi;
  concurrency ya da altyapı (Redis/Kafka) içeren davranışlarda otomatik test. Sonuçlar PR açıklamasına yazılır;
  başarısız olan bir şey varsa açıkça söylenir.
- `STATUS.md` güncellemesi (biten iş, yeni migration, teknik borç).

### PR açıklaması şablonu (Türkçe; kalıcı kayıt — chat anlatımının özeti)

```markdown
## Ne
Bir iki cümle: bu PR ne yapıyor.

## Neden
Hangi problemi çözüyor, spec'te nereye karşılık geliyor.

## Öğrenme notu
PR'ın öğrettiği tek kavram. Framework'ü sihir gibi anlatma: runtime'da ne olduğunu,
hangi sınıfın hangi sırayla çalıştığını göster. Gerekirse küçük bir akış diyagramı (text).
Kodda nereye bakılmalı: dosya + satır.

## Nasıl denenir
Kopyala-çalıştır komutlar (curl / docker / mvnw) ve beklenen çıktı.

## Doğrulama sonuçları
Çalıştırılan kontroller ve sonuçları.

## Trade-off ve bilinçli olarak yapılmayanlar
Seçilmeyen alternatifler ve nedeni; kapsam dışı bırakılanlar.

## Sana sorular
1–2 kısa kontrol sorusu (Onat'ın kavramı anlayıp anlamadığını kendisinin görmesi için).
```

### Merge ve sonrası

- Doğrulama geçtiyse Claude PR'ı `develop`'a **squash merge** eder ve branch'i siler. Doğrulama geçmediyse merge etmez, sorunu söyler.
- `ROADMAP.md`'de adımı işaretler, `STATUS.md`'yi günceller (aynı PR içinde).
- Onat sonradan bir değişiklik isterse yeni küçük bir PR açılır; merge edilmiş geçmiş yeniden yazılmaz.

### Chat anlatımı (her adımın sonunda)

Kısa bir rapor değil, öğretici bir anlatım; ama tek adım, tek konu:

1. **Ne yaptım** — 2–3 cümle + PR linki.
2. **Nasıl çalışıyor** — runtime'da ne oluyor: hangi istek, hangi sınıf, hangi sırayla. Gerekirse küçük text diyagram.
3. **Neden böyle** — önemli kararlar ve seçilmeyen alternatifler (her biri bir iki cümle).
4. **Okuma rehberi** — Onat'ın hangi dosyayı hangi sırayla okuması gerektiği (`dosya:satır` linkleri).
5. **Kendin dene** — 1–3 komut ve beklenen sonuç.
6. **Sıradaki adım** — roadmap'teki bir sonraki adımın tek satırlık özeti.

### Commit

- Açıklayıcı, İngilizce, emir kipinde başlık ("Add Redis-backed login rate limiter").
- Commit ve PR metinlerinde Claude'a atıf yok.
- `secrets/` asla commit edilmez.

---

## Kapsam

`PROJECT_SPEC.md` bağlayıcıdır. Kapsam değişikliği normal bir refactor gibi yapılmaz: önce etkisini açıkla, karar Onat'ın.
`STATUS.md`'deki "Locked scope decisions" uygulama sırasında yeniden açılmaz.

Redis ve Kafka yalnız spec §2'deki **öğrenme modülleri** use-case'leri için kullanılır; "production gibi dursun" diye
başka yere eklenmez. Microservice, CQRS, event sourcing, generic BaseService/BaseRepository, AuditLog tablosu ve
Kubernetes hâlâ kapsam dışıdır.

---

## Teknik kurallar

Gerekçeleri spec'te; burada yalnız sık karşılaşılanlar.

**Persistence**
- Schema'nın kaynağı Flyway; Hibernate yalnız validate eder.
- Tablolar çoğul `snake_case`; ID `UUID` (v7, uygulama tarafında üretilir); zaman `Instant` / `timestamptz`; enum string olarak saklanır.
- Constraint isimleri: `pk_`, `uq_`, `fk_`, `ck_` + tablo + anlam.
- FK, UNIQUE ve CHECK invariant'larını "Java'da zaten kontrol ediyorum" diye kaldırma; DB son savunma hattıdır.
- **Uygulanmış (merge edilmiş) bir migration asla değiştirilmez;** düzeltme yeni migration'dır.

**JPA**
- `@ManyToMany` yok; join tabloları açık entity'dir.
- İlişki eklerken dört soru: FK hangi tabloda, cardinality ne, lifecycle sahibi kim, navigasyon gerçekten gerekli mi?
  Gerekmiyorsa scalar UUID (projedeki varsayılan).
- `CascadeType.ALL` varsayılan değil; `EAGER`, N+1'in çözümü değil.
- Managed entity güncellemesi için mekanik `save()` ekleme; dirty checking yeter.
- `@Version` yalnız gerçekten eşzamanlı güncellenen entity'de (spec §21).

**Transaction**
- Sınır service katmanında; controller transaction yönetmez.
- Dış ağ çağrısını (Kafka publish dahil) DB transaction'ının içinde tutma; gerekiyorsa transactional outbox.
- Son OWNER invariant'ı gibi write skew riskleri pessimistic lock ile çözülür; kilit, ilgili okumalardan önce alınır.

**Delete lifecycle**

```text
User → deactivate      Workspace → archive     Project → archive
Todo → hard delete     Tag → hard delete       Membership → hard delete
```

**Security**
- Spring Security Resource Server'ın yerleşik JWT doğrulaması; özel JWT filtresi yazma.
- Yetki rol değil veridir: aktif üyelik, workspace kapsamı, sahiplik. Kurallar service'te, sorgu seviyesinde
  (`resource id + workspace id`). Dinamik yetki bilgisi JWT'ye yazılmaz.
- Filter zincirindeki hatalar `SecurityProblemHandler`, uygulama hataları `GlobalExceptionHandler` üzerinden ProblemDetail döner.

**Kod stili**
- Constructor injection; package-by-feature (spec §5); request/response DTO ayrımı (record); entity doğrudan REST cevabı olmaz.
- Entity'de Lombok `@Data` yok; public setter yalnız gerçek ihtiyaçta.
- Controller'da business logic yok, controller'dan repository'ye doğrudan çağrı yok, her service için interface yok, `catch (Exception)` yok.
- Modern Java (record, sealed, pattern matching); preview/incubator özellikleri yok.
- Kod yorumları Türkçe ve "neden"i anlatır; kodun zaten söylediğini tekrar etmez.

**Performans**
- Önce ölç. Liste sorgularında generated SQL, N+1, pagination, index ve `EXPLAIN ANALYZE` birlikte değerlendirilir.

---

## Sürüm tuzakları

Spring Boot 4.1 / Framework 7 / Security 7 / Hibernate 7. Eğitim verisindeki Boot 3 kalıpları sessizce yanlış olabilir:

- Boot 4 modüler starter'lar: Flyway için `spring-boot-starter-flyway` (+ `flyway-database-postgresql`); web için `spring-boot-starter-webmvc`.
- Jackson 3: paket `tools.jackson`; annotation'lar `com.fasterxml.jackson.annotation`'da kaldı.
- Security 7: yalnız lambda DSL, `authorizeHttpRequests`, `oauth2ResourceServer(o -> o.jwt(...))`; authentication factor authority'leri (`FACTOR_PASSWORD`, `FACTOR_BEARER`).
- Hibernate 7: Session'da `save` / `update` yok; UUID v7 için `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)`.
- Test: `@MockitoBean`; `@SpringBootTest` + MockMvc için `@AutoConfigureMockMvc`; Testcontainers 2'de `testcontainers-postgresql`.

Davranışın sürüme bağlı olduğu ve sonucun önemli olduğu durumda resmi dokümantasyonla doğrula.

---

## Test politikası

- Genel test suite'i Phase 4'te risk bazlı kurulur (spec §15).
- İstisnalar (ilgili PR'da yazılır): concurrency'ye dayanan invariant'lar (son OWNER, refresh rotation) ve
  Redis / Kafka entegrasyonları (Testcontainers ile gerçek altyapıya karşı).
- Diğer PR'larda doğrulama: derleme + uygulamanın açılması + elle deneme; sonuçlar PR açıklamasında.

---

## Çalıştırma

```bash
docker compose up -d        # local PostgreSQL (+ ilgili PR'lardan sonra Redis, Kafka)
./mvnw spring-boot:run
./mvnw clean verify
```

Local PostgreSQL host'ta `localhost:5433`'te (5432 bu makinede başka bir projede). `DB_URL` varsayılanı buna göre.

---

## Cevap biçimi

Türkçe; Java/Spring/SQL terimleri İngilizce. Her adımın sonunda "Chat anlatımı" yapısı. Onat bir şeyin nedenini
sorduğunda: kısa cevap → runtime'da ne olduğu → koddaki yeri; framework'ü sihir gibi anlatma. Bir mesajda tek konu.
