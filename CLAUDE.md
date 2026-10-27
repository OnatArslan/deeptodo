# CLAUDE.md — DeepToDo

DeepToDo, workspace tabanlı collaborative task management backend'i (Java 21, Spring Boot 4.1).
Proje **agentic** geliştirilir: `ROADMAP.md`'deki adımları Claude uygular (kod, doğrulama, `develop`'a commit).

Normal agentic projelerden tek farkı: büyük ve kapsamlı değişiklikler yerine **her prompt'ta tek bir küçük adım**
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
doğrular, chat'te ne yaptığını ve nedenini anlatır; Onat okuduktan sonra `develop`'a **commit edip push eder**.

**Onat — product owner, okuyucu, öğrenen.** Prompt ile ilerletir ("devam" ya da belirli bir adım),
kodu okur, soru sorar. Kapsam ve mimari kararlar Onat'ındır.

**Onay gerektirenler (Onat'a tek satır sor, bekle):** `develop` → `main` release, production deploy / production'a
dokunan her işlem, uygulanmış (commit edilmiş) bir migration'ı değiştirmek, kapsam değişikliği.

---

## Commit akışı (karar 2026-10-25)

PR yok. Her adım doğrudan `develop`'a tek bir commit olarak girer. Onat değişikliği sağ paneldeki diff ağacında
okuyabilsin diye adım **commit edilmeden bekletilir**; commit, Onat'ın bir sonraki "devam"ında yapılır.

### Branch modeli

```text
main      ← yalnız Onat'ın kararıyla develop'tan alınır (release)
develop   ← bütün adımların commit'leri doğrudan burada
```

Feature branch açılmaz. Geçmişte açılmış referans branch'leri (ör. `wip/user-workspace-reference`) yalnız okunur.

### Bir prompt = bir roadmap adımı = bir commit

- Bir adım, Onat'ın tek oturuşta okuyup anlayabileceği büyüklüktedir: **yaklaşık 300 satır değişiklik ve 10 dosyayı geçmez**
  (migration ve test dahil). Geçecekse işi böl ve önce bölme planını göster.
- Bir adım tek bir şey öğretir. "Rate limiter + token denylist + refresh refactor" üç adımdır.
- Kendiliğinden ilgisiz refactor, yeni dependency ya da kapsam dışı iş ekleme. Gerekli görürsen chat'te
  "Sonraki adım önerisi" olarak yaz; karar Onat'ın.

### Adımın yaşam döngüsü

```text
"devam"  ─▶ 1. Önceki adım commit edilmemiş duruyorsa: commit + push (develop). Çalışma ağacı temiz.
         ─▶ 2. Sıradaki adımı planla, kodu yaz, doğrula, ROADMAP / STATUS'u güncelle.
         ─▶ 3. Commit ETME. Yeni dosyaları `git add -N` ile işaretle (git takip etmediği dosyaları diff'te göstermez).
               Chat'te anlat. Değişiklikler sağ panelde (uncommitted) görünür.
Onat okur, sorar ─▶ istenen düzeltmeler aynı adıma (hâlâ commit edilmemiş) eklenir.
"devam"  ─▶ döngü başa döner.
```

- Commit'ten hemen önce yeniden derlenir (`./mvnw -q -DskipTests compile`): Onat dosyaları okurken IDE'de yanlışlıkla
  değiştirmiş olabilir. Onat'ın bilinçli değişiklikleri de aynı commit'e girer; commit mesajında belirtilir.
- Onat "commitle" derse anlatımdan sonra beklemeden commit + push edilir.
- Doğrulama geçmediyse adım öyle bırakılmaz: sorun chat'te açıkça söylenir, düzeltilir ya da adım geri alınır.
- Commit edilmiş geçmiş yeniden yazılmaz (`--amend` / rebase / force push yok); sonradan düzeltme yeni commit'tir.

### Her adımdan önce

1. İlgili kodu ve spec bölümünü oku.
2. Kısa bir plan yaz: ne değişecek, hangi dosyalar, neden bu sırayla. Mimari ya da kapsam kararı gerekiyorsa
   önce Onat'a sor (tek satır, seçenek + önerin).

### Her adımda

- Kod + gerekiyorsa migration.
- **Doğrulama:** derleme, uygulamanın açılması, migration'ların çalışması, değişen davranışın elle (curl) denenmesi;
  concurrency ya da altyapı (Redis/Kafka) içeren davranışlarda otomatik test. Sonuçlar commit mesajına ve chat'e yazılır;
  başarısız olan bir şey varsa açıkça söylenir.
- `ROADMAP.md`'de adım işaretlenir (`[x]` + commit'ten sonra kısa SHA), `STATUS.md` güncellenir (biten iş, yeni migration,
  teknik borç) — aynı commit'te.

### Commit mesajı (kalıcı kayıt — chat anlatımının özeti)

```text
<İngilizce, emir kipinde başlık: "Add refresh token rotation">

Ne: bir iki cümle (Türkçe).
Neden: hangi problemi çözüyor, spec'te nereye karşılık geliyor; ROADMAP adımı.
Doğrulama: çalıştırılan kontroller ve sonuçları.
Bilinçli olarak yapılmayanlar: seçilmeyen alternatifler, kapsam dışı bırakılanlar.
```

### Chat anlatımı (her adımın sonunda)

Kısa bir rapor değil, öğretici bir anlatım; ama tek adım, tek konu:

1. **Ne yaptım** — 2–3 cümle; değişikliklerin sağ panelde commit edilmemiş olarak beklediği.
2. **Nasıl çalışıyor** — runtime'da ne oluyor: hangi istek, hangi sınıf, hangi sırayla. Gerekirse küçük text diyagram.
3. **Neden böyle** — önemli kararlar ve seçilmeyen alternatifler (her biri bir iki cümle).
4. **Okuma rehberi** — Onat'ın hangi dosyayı hangi sırayla okuması gerektiği (`dosya:satır` linkleri).
5. **Kendin dene** — 1–3 komut ve beklenen sonuç.
6. **Sıradaki adım** — roadmap'teki bir sonraki adımın tek satırlık özeti.

### Derin anlatım — Authentication, Redis, Kafka (ROADMAP'te 🔬)

Bu üç konu projenin en zor ve en çok öğreten kısmıdır; "çalışıyor" yetmez, Onat onları işletebilecek ve hata
ayıklayabilecek kadar anlamalıdır. Bu adımlarda chat anlatımı yukarıdaki altı bölümü korur ama **derinleşir**:

1. **Problem ve alternatif** — Bu teknoloji hangi problemi çözüyor; kendimiz yazsaydık ne yazacaktık
   (auth için: framework'ün hazır verdiği ile bizim yazdığımız; Keycloak denemesinde aynı işin nasıl yapıldığı).
   Neyi kazandık, neyi kaybettik.
2. **Kavram modeli** — Temel kavramlar ve birbirleriyle ilişkisi, kendi terimleriyle
   (Spring Security: SecurityFilterChain ve filter sırası, Authentication, AuthenticationManager / Provider,
   SecurityContext, JWT claim'leri, token ailesi; Redis: key, veri yapıları, TTL, tek thread modeli;
   Kafka: topic, partition, offset, consumer group, delivery semantics).
3. **Protokol / wire seviyesi** — Ağda gerçekte ne gidip geliyor. Auth: HTTP istek / cevap header'ları
   (`Authorization`, `WWW-Authenticate`, `Set-Cookie` flag'leri), decode edilmiş token'ın claim'leri tek tek,
   DB'ye yazılan satırlar. Redis: çalışan komutlar
   (`redis-cli MONITOR` çıktısıyla). Kafka: kaydın key / value / header'ları, offset commit'i.
4. **Runtime sırası** — Bir isteğin bizim kodumuzdan dış sisteme ve geri dönüşü; hangi sınıf, hangi ağ çağrısı,
   hangi sırayla. Sequence diyagramı (text).
5. **Hata modları** — Dış sistem çökerse, yavaşlarsa, ağ koparsa, mesaj iki kez gelirse ne olur; kodumuz nasıl davranır.
   Mümkünse **denenerek gösterilir** (container durdurulur, sonuç gösterilir).
6. **Konfigürasyon** — Eklenen her ayarın ne yaptığı; local ile production arasında neden farklı olduğu.
7. **Kendin gözlemle** — Onat'ın kendi bakabileceği araçlar ve komutlar (`curl -v`, token decode,
   `logging.level.org.springframework.security=TRACE` ile filter zinciri, `psql` ile ilgili tablolar, `redis-cli`,
   `kafka-console-consumer`, ilgili log satırları).
8. **Kontrol soruları** — 3–5 soru.

Anlatım uzunsa birkaç mesaja bölünebilir; "bir mesajda tek konu" kuralı adımın alt konuları için esner.
Commit mesajı da bu adımlarda aynı derinliğin kısa özetini taşır.

### Commit

- Açıklayıcı, İngilizce, emir kipinde başlık ("Add Redis-backed login rate limiter").
- Commit metinlerinde Claude'a atıf yok.
- `secrets/` asla commit edilmez.

---

## Kapsam

`PROJECT_SPEC.md` bağlayıcıdır. Kapsam değişikliği normal bir refactor gibi yapılmaz: önce etkisini açıkla, karar Onat'ın.
`STATUS.md`'deki "Locked scope decisions" uygulama sırasında yeniden açılmaz.

Authentication uygulamada, Spring Security ile yazılır (spec §10, karar 2026-10-25). Keycloak denendi ve bırakıldı;
yeniden önerilmez.

Redis ve Kafka yalnız spec §2.1'deki **öğrenme modülleri** use-case'leri için kullanılır; "production gibi dursun" diye
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
- **Uygulanmış (commit edilmiş) bir migration asla değiştirilmez;** düzeltme yeni migration'dır.

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
- Access token'ı uygulama üretir (RS256, `kid`); doğrulama Spring Security Resource Server'ın yerleşik JWT desteğiyle
  (`iss`, `aud`, `exp`). Özel JWT doğrulama filtresi yazma.
- Refresh token opaque'tır, DB'de yalnız hash'i durur; rotation ve reuse detection koşullu update ile (spec §7.9, §10).
- Private key ve parolalar repoya girmez: local'de `secrets/` (gitignore), production'da env / secret.
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
- İstisnalar (ilgili adımda yazılır): concurrency'ye dayanan invariant'lar (son OWNER) ve
  Redis / Kafka entegrasyonları (Testcontainers ile gerçek altyapıya karşı).
- Diğer adımlarda doğrulama: derleme + uygulamanın açılması + elle deneme; sonuçlar commit mesajında.

---

## Çalıştırma

```bash
docker compose up -d        # local PostgreSQL (+ G adımlarından sonra Redis, Kafka)
./mvnw spring-boot:run
./mvnw clean verify
```

Local PostgreSQL host'ta `localhost:5433`'te (5432 bu makinede başka bir projede). `DB_URL` varsayılanı buna göre.

---

## Cevap biçimi

Türkçe; Java/Spring/SQL terimleri İngilizce. Her adımın sonunda "Chat anlatımı" yapısı. Onat bir şeyin nedenini
sorduğunda: kısa cevap → runtime'da ne olduğu → koddaki yeri; framework'ü sihir gibi anlatma. Bir mesajda tek konu.
Auth / Redis / Kafka adımlarında (🔬) "Derin anlatım" bölümü geçerlidir.
