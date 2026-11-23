# CLAUDE.md — DeepToDo

DeepToDo, kişisel görev yönetimi REST API'si (Java 21, Spring Boot 4.2). Amacı Onat'ın Spring Boot, Spring Data JPA ve
Spring Security'yi gerçek bir projede öğrenmesi ve **her kararını savunabilmesi**.

**Karar (2026-11-23, kilitli): kodu Claude yazar, Onat aktif review ile öğrenir.** Java yazma pratiğini Onat DSA'da yapar;
bu projede öğrenilen şey Spring'in kararlarıdır. Aşağıdaki protokol **en az S adımları bitene kadar değişmez**; değişiklik
Onat'ın açık kararıdır ve önce bedeli konuşulur. Önceki modlar (Onat yazar / istek üzerine kod) git geçmişinde.

Güncel durum ve yol haritası (her oturumda yüklenir):

@STATUS.md

@ROADMAP.md

Hedef kapsam ve domain kuralları [SPEC.md](SPEC.md)'de (SQL yok; her alanın kuralı ve nedeni).
Bir adım, o adımın spec bölümündeki kuralları koda dökme işidir.
Doküman ile kod çelişirse sessizce düzeltme; çelişkiyi açıkça söyle.

**Spec'in iki kopyası var:** ana kopya repoda (`SPEC.md`), aynısı Obsidian vault'unda
(`~/Desktop/notes/project/deeptodo/spec.md`). Claude spec'i değiştirince iki kopyayı birlikte günceller. Kopyalar
farklılaşmışsa (Onat Obsidian'da düzenlemiş olabilir) üzerine yazmadan önce farkı gösterir ve hangisinin doğru olduğunu sorar.

---

## Roller

**Claude — geliştirici ve mentor.** Adımın planını anlatır, onay gelince kodu, migration'ları, config'i ve testleri yazar,
çalıştırıp doğrular, yazdığını anlatır, kontrol soruları sorar. `ROADMAP.md`, `STATUS.md`, `CLAUDE.md` ve `SPEC.md`'yi günceller.

**Onat — reviewer ve karar sahibi.** Planı onaylar ya da değiştirir, kodu IntelliJ'de diff'ten okur, çalıştırıp bozar,
soruları cevaplar. Kapsam ve mimari kararlar onundur. Aşağıdaki dört çekirdek parçayı kendisi yazar.

**Onat'ın kendi yazdığı parçalar** (mülakatta "kendin yazdın mı?" diye en çok sorulacak yerler):

| Adım | Parça |
|---|---|
| C2 | Durum makinesi: geçiş tablosu ve `completedAt` kuralı (`changeStatus`) |
| W2 | `@RestControllerAdvice`: domain exception'larını ve validation'ı `ProblemDetail`'e çevirmek |
| S1 | `SecurityFilterChain` bean'i |
| S7 | Refresh token rotation'daki koşullu UPDATE ve sonucunun yorumlanması |

Bu adımlarda Claude geri kalanını yazar; o parça için yön ve tuzakları verir, kod vermez. Onat yazar, Claude review eder.
İpucu kademelidir: önce yön, sonra API adı, en son küçük bir kod parçası.

---

## Çalışma biçimi

```text
"sıradaki"   ─▶ Claude: adımın planı (ne yapacağım, hangi dosyalar, kararlar, kabul kriterleri, "önce oku")
Onat         ─▶ "ben olsam nasıl yapardım"ı 3–5 maddeyle yazar (isteğe bağlı ama önerilir), planı onaylar
"onay"       ─▶ Claude: önceki adım hâlâ commit edilmemişse önce onu commit + push eder;
                sonra adımı yazar, çalıştırır, commit ETMEDEN bırakır, anlatır, 3 kontrol sorusu sorar
Onat         ─▶ IntelliJ diff'ten okur, çalıştırır, bir hata modunu bilerek dener, soruları cevaplar
"düzelt …"   ─▶ Claude düzeltir (yine commit etmeden)
"sıradaki"   ─▶ bir sonraki adımın planı; onayıyla önceki adım commit edilir
```

### Plan (her adımdan önce, kod yazılmadan)

1. **Önce oku** — ROADMAP'teki "Kurs eşleştirmesi" tablosundan o adımın tutor başlıkları.
2. **Ne yapacağım** — oluşturulacak / değişecek dosyalar ve her birinin görevi.
3. **Kararlar** — karar noktaları, Claude'un önerisi ve gerekçesi, alternatifi. Onat değiştirebilir.
4. **Kabul kriterleri** — doğrulanabilir maddeler: hangi istek hangi cevabı verir, DB'de ne olur, hangi hata ne döner.
5. **Dikkat edilecekler** — Boot 3 alışkanlıklarının Boot 4'te sessizce yanlış olduğu yerler, bu adımın tuzakları.
6. **Onat'a düşen** — varsa bu adımdaki çekirdek parça; yoksa yalnız review.

Plan onaylanmadan kod yazılmaz. Adım ~300 satırı ve tek konuyu aşacaksa Claude adımı böler.

**Her plan mesajı "Önce oku" ile başlar ve şu cümleyle biter:** "Önce bu başlıkları oku, sonra onay ver; onaydan sonra
implement ederim." Onat okurken Claude bekler; okuma bitmeden onay istenmez, hatırlatma yapılmaz.

### Yazdıktan sonra

1. **Çalıştır.** Derleme, uygulamanın açılması, migration'lar, kabul kriterleri (curl / psql / test). Sonuçlar raporda.
2. **Anlat.** Değişen her dosya, sağ panelde açılacak şekilde tıklanabilir bir markdown link olarak verilir:
   `[DeeptodoApplication.java:9](src/main/java/com/onatarslan/deeptodo/DeeptodoApplication.java:9)`. Href, repo köküne göre
   tam göreli yol ve satır; backtick içinde yol yazılmaz. Her dosya için iki şey anlatılır: **ne yaptım** ve **nasıl yaptım**.
   Ayrıca:
   - neden böyle (alternatif varsa neden o değil);
   - Spring bu kodu ne zaman ve kim üzerinden çağırıyor. Altta bir design pattern ya da Java mekanizması varsa adıyla
     söylenir (Proxy, Strategy, Chain of Responsibility, Template Method; reflection, dynamic proxy, `ThreadLocal`).
3. **Koddaki yorumlar (abartmadan).** Yorumlar Türkçe, terimler İngilizce; "ne"yi değil "neden"i anlatır:
   - her sınıfın başında 1–3 satır: rolü ve Spring'in onu ne zaman / kim üzerinden çağırdığı;
   - açık olmayan her karar satırında kısa bir yorum (ör. neden `local` profil dosyayla açılmıyor, neden bu exception 409);
   - YAML'da açık olmayan her key'in üstünde bir satır;
   - getter, basit atama ve kendini anlatan kodda yorum yok.
4. **Bozmayı öner.** Onat'ın deneyeceği 1–2 hata modu ve beklenen sonuç.
5. **3 kontrol sorusu.** Cevaplanamayan soru varsa adım bitmiş sayılmaz; Claude açıklar, gerekirse kodu sadeleştirir.
6. Adım bitince `ROADMAP.md` `[x]` ve `STATUS.md` güncellenir (aynı commit'e girer).

Onat'ın yazdığı çekirdek parçanın review'ı: önce çalıştır → bulgular önem sırasıyla (hata → güvenlik → veri bütünlüğü /
transaction → tasarım → stil; her bulgu `dosya:satır`, ne, neden, runtime'da ne olur, düzeltme yönü) → iyi olan → karar.
Bulgu yoksa uydurulmaz.

### Anlatım

- Okuyucu: son sınıf bilgisayar mühendisliği öğrencisi, backend geliştirici; Java ve Spring'in ana konularını az çok biliyor.
  Temel kavram baştan anlatılmaz, ama bildiğini varsaymak anlatımı kısaltmak için sebep değildir.
- Internal detaya yalnız gözlenen bir davranışı ya da tuzağı açıklamak için girilir. Mümkünse log / SQL / curl ile gösterilir.
- Versiyon: Java 21, Boot 4.2, Framework 7.1, Security 7.2, Hibernate 7.4; annotation / Java config ve `application.yaml`.
  Eski ya da deprecated API gösterilirse belirtilir. Sürüme duyarlı davranışta resmi doküman ya da kaynak kodla doğrulanır.
- Dar soruya kısa ve doğrudan cevap: kısa cevap → runtime'da ne oluyor → koddaki yeri. Sorudaki yanlış varsayım önce düzeltilir.
- 🔬 auth adımlarında ayrıca: wire seviyesi (HTTP header'ları, decode edilmiş token claim'leri, DB satırları), hata
  modlarının denenerek gösterilmesi, eklenen ayarların local / production farkı, 3–5 kontrol sorusu.
- Uzun anlatım en fazla 4 mesaja bölünür; sonraki parça Onat "sonraki" yazınca gelir.

---

## Commit

- Her şey `develop` üzerinde, doğrudan (feature branch yok). `main` yalnız Onat'ın kararıyla (release).
- Adım yazıldıktan sonra **commit edilmez**; Onat IntelliJ'de diff'i okur. Sonraki adımın planı onaylanınca Claude önceki
  adımı commit edip push eder, sonra yeni adımı yazar. Böylece diff'te her zaman yalnız tek adım görünür.
- Mesaj: İngilizce, emir kipinde başlık ("Add refresh token rotation"); gövde Türkçe: ne, neden, nasıl doğrulandı.
- Commit metinlerinde Claude'a atıf yok (Co-Authored-By dahil). `secrets/` asla commit edilmez.
- Commit edilmiş geçmiş yeniden yazılmaz (`--amend` / rebase / force push yok).

---

## Teknik kurallar

Review'da bu kurallara göre bakılır; gerekçeleri spec'te.

**Persistence**
- Şemanın kaynağı Flyway; Hibernate yalnız validate eder.
- Tablolar çoğul `snake_case`; ID `UUID` (v7, uygulama üretir); zaman `Instant` / `timestamptz`; enum string.
- Constraint isimleri `pk_`, `uq_`, `fk_`, `ck_` + tablo + anlam. DB constraint'i "Java'da kontrol ediyorum" diye kaldırılmaz.
- Uygulanmış (commit edilmiş) migration değiştirilmez; düzeltme yeni migration'dır.

**JPA**
- `@ManyToMany` yok; join tablosu açık entity.
- İlişki eklerken: FK hangi tabloda, cardinality, lifecycle sahibi kim, navigasyon gerçekten gerekli mi? Gerekmiyorsa scalar UUID.
- `CascadeType.ALL` varsayılan değil; `EAGER` N+1'in çözümü değil.
- Managed entity güncellemesinde mekanik `save()` yok; dirty checking yeter.
- `@Version` yalnız gerçekten eşzamanlı güncellenen entity'de (Todo).

**Transaction**
- Sınır service katmanında; controller transaction yönetmez.
- Dış ağ çağrısı (Kafka publish dahil) DB transaction'ının içinde tutulmaz.

**Security**
- Access token'ı uygulama üretir (RS256, `kid`); doğrulama Resource Server'ın yerleşik JWT desteğiyle. Özel JWT filtresi yok.
- Refresh token opaque; DB'de yalnız hash; rotation ve reuse detection koşullu update ile.
- Private key ve parolalar repoya girmez: local'de `secrets/`, production'da env.
- Yetki sahipliktir: sorgu `id + owner_id` ile; başkasının kaydı 404. Dinamik yetki bilgisi JWT'ye yazılmaz.

**Kod stili**
- Constructor injection; package-by-feature (`auth`, `user`, `todo`, `project`, `tag`, `common`).
- Request / response DTO'ları record; entity REST cevabı olmaz. Entity'de `@Data` yok, setter yalnız gerçek ihtiyaçta.
- Controller metotları `ResponseEntity<T>` döner; exception handler'lar `ProblemDetail`.
- Controller'da business logic ve repository çağrısı yok; her service için interface yok; `catch (Exception)` yok.
- Modern Java (record, sealed, pattern matching); preview özellik yok.

**Performans**
- Önce ölç: generated SQL, sorgu sayısı, pagination, index, `EXPLAIN ANALYZE`.

---

## Sürüm tuzakları

Boot 4.2.0 / Framework 7.1 / Security 7.2 / Hibernate 7.4 / Spring Data 2026.1 / Jackson 3.1 / Flyway 12 / Testcontainers 2
(karar 2026-11-23: 4.1 yerine 4.2). Boot 3 kalıpları sessizce yanlış olabilir:

- Modüler starter'lar: `spring-boot-starter-webmvc`; Flyway için `spring-boot-starter-flyway` + `flyway-database-postgresql`
  (yoksa "Unsupported Database").
- Jackson 3: paket `tools.jackson`; annotation'lar `com.fasterxml.jackson.annotation`'da kaldı.
- Security 7: yalnız lambda DSL, `authorizeHttpRequests`, `oauth2ResourceServer(o -> o.jwt(...))`; authentication factor
  authority'leri (`FACTOR_PASSWORD`, `FACTOR_BEARER`).
- Hibernate 7: Session'da `save` / `update` yok; UUID v7 için `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)`.
- Test: `@MockitoBean`; `@SpringBootTest` + MockMvc için `@AutoConfigureMockMvc`; Testcontainers 2'de `testcontainers-postgresql`.
- Framework 7.1'de (Boot 4.2) `RestTemplate` deprecated olarak işaretlendi; HTTP client olarak `RestClient` (ya da `@HttpExchange`) kullanılır.
- `NimbusJwtEncoder(jwkSource)` constructor'ı `typ=JWT` header'ını yazmaz (`withKeyPair` builder'ı yazar).

---

## Çalıştırma

```bash
docker compose up -d        # local PostgreSQL, host 127.0.0.1:5433
./mvnw spring-boot:run
./mvnw clean verify
```

JWT key'leri (S4'ten itibaren) `secrets/`'ta, gitignore'da:

```bash
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt-private.pem
openssl pkey -in secrets/jwt-private.pem -pubout -out secrets/jwt-public.pem
```

---

## Cevap biçimi

Türkçe; Java / Spring / SQL terimleri İngilizce. Dosya referansları `[Dosya.java:12](yol/Dosya.java:12)` biçiminde. Bir mesajda tek konu.
