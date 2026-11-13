# DeepToDo — Spec

> v8 (2026-11-13): tek dosya. SQL içermez; domain'i, her alanın sağlaması gereken kuralları ve nedenlerini anlatır.
> Ana kopya repoda (`SPEC.md`), aynısı Obsidian'da (`notes/project/deeptodo/spec.md`).

## Bu spec nasıl okunur

| § | Bölüm | Kullanan adımlar |
|---|---|---|
| 1 | Genel bakış: ürün, sözlük, entity haritası, sahiplik, ortak kurallar, kural katmanları | hepsi |
| 2 | Kullanıcı | S2, S3, S8 |
| 3 | Kimlik doğrulama ve oturum: register, login, token'lar, RefreshToken | S1–S8 |
| 4 | Todo: durum makinesi, PATCH, listeleme | C2, W1, W3, J1, J2, J5, S6 |
| 5 | Project | J3, S6 |
| 6 | Tag ve etiketleme | J4, J5, S6 |
| 7 | API ve hata sözleşmesi | W1–W3 ve her adımın uçları |
| 8 | Kalite: test, loglama, performans, bitti tanımı | Q1–Q3 |

Her entity bölümü aynı sırayı izler:

1. **Nedir**
2. **Yaşam döngüsü**
3. **Kurallar:** numaralı ve test edilebilir.
4. **Alanlar**
5. **Kural nerede korunur**
6. **İlişkiler**
7. **Kararlar:** neden böyle, seçilmeyen alternatif.

Bir adıma başlarken önce o adımın bölümünü oku. Görev kartı bu bölümdeki kuralları koda dökme işidir.

---

## 1. Genel bakış

**Yapım sırası** (ROADMAP): Spring Core → Web → Data JPA → Security. Spec projenin **son halini** anlatır; yol üstünde
bazı kurallar henüz geçerli değildir:
- J1'e kadar veri bellekte tutulur (DB yok); `createdAt` / `updatedAt` ve `version` JPA ile gelir.
- S6'ya kadar kayıtların sahibi yoktur: herkes her şeyi görür, sahiplik kuralları (404, aynı sahip FK'leri) S6'da eklenir.
- S1'e kadar bütün uçlar açıktır.

### Ürün

DeepToDo, kişisel görev yönetimi REST API'sidir. Frontend'i yoktur; istemci curl, Postman ya da OpenAPI arayüzüdür.

Bir kullanıcının bir günü:

1. Ayşe kaydolur (email, isim, parola), sonra giriş yapar. Bir **access token** (15 dk) ve cookie'de bir **refresh token** (14 gün) alır.
2. "Tez" adında bir **project** açar, altına üç **todo** ekler; birine bitiş tarihi koyar.
3. "acil" adında kırmızı bir **tag** oluşturur ve iki todo'ya takar.
4. Listesini "acil + tamamlanmamış, bitiş tarihine göre" filtreler, sayfa sayfa gezer.
5. Bir todo'yu `IN_PROGRESS`, sonra `DONE` yapar; tamamlanma zamanı kendiliğinden yazılır.
6. 15 dakika sonra access token'ı dolar. İstemci refresh token'la sessizce yenisini alır; Ayşe hiçbir şey fark etmez.
7. Tez bitince project'i arşivler: todo'lar durur ama project'e yeni todo eklenemez.
8. Çıkış yapar: oturumu kapanır, refresh token'ı bir daha çalışmaz.

Mehmet de aynı sistemde. Ayşe'nin hiçbir kaydını göremez, değiştiremez; var olup olmadığını bile öğrenemez.

### Sözlük

| Terim | Anlamı |
|---|---|
| **Kullanıcı (User)** | Sisteme kayıtlı kişi. Bütün verinin sahibi |
| **Sahip (owner)** | Bir kaydı oluşturan ve tek erişebilen kullanıcı |
| **Todo** | Yapılacak bir iş: başlık, durum, öncelik, isteğe bağlı bitiş tarihi |
| **Project** | Todo'ları gruplayan klasör. Bir todo en fazla bir project'te olur |
| **Tag** | Todo'lara takılan serbest etiket. Bir todo'nun birden çok tag'i olabilir |
| **Etiketleme (TodoTag)** | "Şu tag şu todo'ya takılı" kaydı |
| **Access token** | Kısa ömürlü imzalı kimlik kartı (JWT). Her istekte `Authorization` header'ında gider |
| **Refresh token** | Uzun ömürlü, tek kullanımlık rastgele değer. Yalnız yeni access token almak için kullanılır |
| **Oturum (aile, family)** | Bir login ile başlayan refresh token zinciri. Her yenilemede zincire yeni halka eklenir |
| **Arşivleme** | Kaydı silmeden "kullanım dışı" yapmak |
| **Kapatma (deactivate)** | Kullanıcı hesabını silmeden kullanılamaz yapmak |

### Entity haritası

```text
                       ┌──────────────┐
                       │     User     │
                       └──────┬───────┘
          ┌─────────────┬─────┴───────┬──────────────────┐
          │ 1:N         │ 1:N         │ 1:N              │ 1:N
   ┌──────▼─────┐ ┌─────▼──────┐ ┌────▼────┐   ┌─────────▼────────┐
   │  Project   │ │    Todo    │ │   Tag   │   │   RefreshToken   │
   └──────┬─────┘ └──┬─────▲───┘ └────▲────┘   └──────────────────┘
          │ 0..1 : N │     │          │
          └──────────┘     │ N        │ N
                        ┌──┴──────────┴──┐
                        │    TodoTag     │   (Todo N ── N Tag)
                        └────────────────┘
```

- Project, Todo, Tag ve TodoTag'in her biri **tek bir sahibe** aittir.
- Bir todo **en fazla bir** project'te olur, hiç olmayabilir.
- Todo ile Tag arasındaki çoka-çok ilişki, kendi kaydı olan **TodoTag** üzerinden kurulur.
- Farklı sahiplerin kayıtları birbirine **asla** bağlanamaz: Ayşe'nin todo'su Mehmet'in project'ine giremez, Mehmet'in tag'i takılamaz.
  Bu kural hem kodda hem DB'de korunur (bkz. §5, §6).

### Temel ilke: sahiplik

Sistemde rol yok (admin, editor…). Yetki tek bir soruya iner: **bu kayıt isteği yapan kullanıcının mı?**

- Bütün okuma ve yazmalar sahibin kimliğiyle sınırlanır. Kayıt, "bu id" **ve** "bu sahip" ile aranır. Önce id ile bulup
  sonra sahibi kontrol etmek ikinci tercihtir: unutulmaya açıktır, ayrıca iki ayrı hata yolu üretir.
- Başkasının kaydı **404** döner, 403 değil. 403, "kayıt var ama senin değil" demektir; kaydın varlığını sızdırır.
- Sahiplik bilgisi token'a yazılmaz. Token yalnız "sen kimsin"i söyler; "neyin sahibisin" her istekte DB'den gelir.

### Ortak kurallar (bütün entity'ler)

| Konu | Kural | Neden |
|---|---|---|
| Kimlik | UUID v7, uygulama üretir, DB üretmez | Kaydetmeden önce id bilinir. Zamana göre sıralı olduğu için index dostudur. Tahmin edilemez, sıralı sayı gibi "kaç kullanıcı var"ı sızdırmaz |
| Zaman | Java'da `Instant`, DB'de saat dilimli zaman damgası; her şey UTC | Saat dilimi dönüşümü istemcinin işi. Sunucu tek bir mutlak zaman bilir |
| `createdAt` / `updatedAt` | Her entity'de var, zorunlu. Kod elle yazmaz, JPA auditing yazar; zamanı enjekte edilen `Clock` verir | Elle yazılan zaman unutulur. `Clock` testte sabitlenebilir |
| Enum'lar | Adıyla (metin) saklanır, sırasıyla (ordinal) değil. İzin verilen değerler DB'de de kısıtlanır | Enum'a araya değer eklenince ordinal veriyi sessizce bozar |
| Metin alanları | Baştaki / sondaki boşluk kırpılır. "Boş olamaz" = kırpıldıktan sonra en az bir karakter. İsteğe bağlı metinlerde boş string `null` sayılır | `"   "` başlıklı bir todo anlamsızdır. `""` ile `null`'ın iki ayrı "yok" anlamı taşımaması gerekir |
| Tekillik | "Aynı sahipte aynı ad" kuralları büyük / küçük harf duyarsızdır ("Tez" = "tez") | Kullanıcı gözüyle aynı ad |
| Şema | Flyway migration'larıyla kurulur. Hibernate yalnız doğrular, şema üretmez | Şema kodla birlikte versiyonlanır, her ortamda aynıdır |
| Migration | Commit edilmiş migration bir daha değişmez; düzeltme yeni migration'dır | Flyway checksum'ı uygulanmış migration'ın değiştiğini yakalar ve uygulamayı açmaz |
| Constraint adları | `pk_`, `uq_`, `fk_`, `ck_` + tablo + anlam (ör. `uq_users_email`) | Hata mesajında hangi kuralın çiğnendiği okunur; kod da hatayı adına göre ayırt edebilir |
| Silme | User kapatılır, Project arşivlenir; Todo, Tag ve TodoTag gerçekten silinir | Bkz. her entity'nin "Kararlar" bölümü |

### Kural katmanları: bir kural nerede korunur?

Her kuralın bir "nerede korunur" satırı var. Dört katman:

| Katman | Ne yakalar | Ne yakalayamaz | Örnek |
|---|---|---|---|
| **İstek (Bean Validation)** | Biçim: zorunlu mu, uzunluk, email biçimi, regex. DB'ye gitmeden, ucuz, alan bazında 400 | DB'deki duruma bağlı her şey: "bu email kayıtlı mı?" | `title` boş → 400 |
| **Domain (entity / service)** | İş kuralları ve durum geçişleri: "CANCELLED bir todo doğrudan DONE olamaz" | Eşzamanlı iki isteğin yarışı | geçersiz geçiş → 409 |
| **Sorgu** | Sahiplik: kayıt `id + sahip` ile aranır | — | başkasının todo'su → 404 |
| **DB constraint** | Son savunma hattı: tekillik, CHECK, FK, NOT NULL. Yarışı da yakalar | Kullanıcıya güzel mesaj vermez, hatayı uygulama çevirir | aynı anda iki kayıt, aynı email → biri 409 |

Bir kural birden fazla katmanda durabilir ve çoğu zaman durmalıdır. Örneğin "email tekil" kuralı:
- Service önce kontrol eder, böylece kullanıcıya düzgün bir 409 verir.
- DB yine UNIQUE tutar, çünkü iki eşzamanlı kayıt service kontrolünü aynı anda geçebilir.

**"Java'da zaten kontrol ediyorum" diye DB constraint'i kaldırılmaz.**

### Kapsam dışı

Workspace, paylaşım, rol ve admin; e-posta doğrulama ve parola sıfırlama; dosya eki, bildirim, hatırlatma, tekrarlayan görev;
frontend; microservice. Keycloak, Redis, Kafka ve public deploy yalnız ROADMAP'in opsiyonel O bölümünde, v1'den sonra.

### Teknoloji

| Alan | Seçim |
|---|---|
| Dil / build | Java 21, Maven |
| Framework | Spring Boot 4.1 (Framework 7, Security 7) |
| Web | Spring Web MVC, Bean Validation, `ProblemDetail` |
| Persistence | PostgreSQL 17, Flyway, Spring Data JPA (Hibernate 7) |
| Security | Spring Security, OAuth2 Resource Server (JWT, RS256), Argon2id |
| Test | JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL) |
| Paket | springdoc-openapi, Docker |

---

## 2. Kullanıcı (User)

> Adımlar: **S2** (tablo, entity, parola, register), **S3** (login), **S8** (hesap kapatma). Genel kurallar: §1.

### Nedir

Sisteme kayıtlı kişi. Bütün todo, project, tag ve oturumların sahibidir. Kimliği **email** ile tanınır, **görünen adı**
(displayName) ile hitap edilir.

### Yaşam döngüsü

```text
  register            hesap kapatma (DELETE /api/users/me)
 ─────────▶ ACTIVE ─────────────────────────────────────▶ DEACTIVATED
```

- Kayıtla `ACTIVE` başlar.
- Hesap kapatılınca `DEACTIVATED` olur. v1'de geri dönüş yok.
- Kullanıcı **silinmez**. Kapalı hesabın verisi DB'de durur ama kimse erişemez: sahibi giriş yapamaz, başkası zaten göremez.

### Kurallar

| # | Kural |
|---|---|
| U1 | Email sistemde tekildir ve büyük / küçük harf duyarsızdır: `Ayse@Mail.com` ile `ayse@mail.com` aynı hesaptır |
| U2 | Email küçük harfe çevrilerek saklanır. Login'de de aynı çevrim yapılır, kullanıcı hangi harflerle yazarsa yazsın girer |
| U3 | Email geçerli bir adres biçimindedir, en fazla 320 karakterdir. İçinde boşluk varsa (baş / son dahil) geçersizdir; kırpılmaz, reddedilir |
| U4 | Görünen ad kırpılır; kırpıldıktan sonra 1–100 karakterdir |
| U5 | Parola 8–128 karakterdir. **Düz parola hiçbir yerde saklanmaz ve loglanmaz**; yalnız hash'i saklanır |
| U6 | Kapalı (`DEACTIVATED`) kullanıcı giriş yapamaz ve oturum yenileyemez. Hata, yanlış parola ile aynıdır (hesabın kapalı olduğu sızmaz) |
| U7 | Hesap kapatılınca kullanıcının bütün oturumları (refresh token aileleri) aynı transaction'da iptal edilir |
| U8 | Kapalı kullanıcının elindeki access token süresi dolana kadar (en fazla 15 dk) geçerli kalır. Bu bilinçli bir penceredir; bkz. Kararlar |
| U9 | Kapalı hesabın email'i yeniden kayıt için kullanılamaz (satır durduğu için tekillik devam eder) |

### Alanlar

| Alan | Anlamı | Tip | Zorunlu | Kural | Geçerli | Geçersiz |
|---|---|---|:-:|---|---|---|
| `id` | Kimlik | UUID | ✓ | v7, uygulama üretir, değişmez | `0193…` | — |
| `email` | Giriş kimliği | metin ≤ 320 | ✓ | U1–U3; saklanan değer her zaman küçük harf | `ayse@mail.com` | `ayse@`, ` ayse@mail.com`, `Ayse@Mail.com` (saklanan değer olarak) |
| `displayName` | Görünen ad | metin ≤ 100 | ✓ | U4 | `Ayşe Y.` | `""`, `"   "` |
| `passwordHash` | Parola hash'i | metin ≤ 255 | ✓ | Önekli biçim: `{argon2}$argon2id$…` | — | düz parola |
| `status` | Hesap durumu | enum `UserStatus` | ✓ | `ACTIVE`, `DEACTIVATED` | `ACTIVE` | `active`, `DELETED` |
| `createdAt` | Kayıt zamanı | Instant | ✓ | auditing yazar, bir daha değişmez | | |
| `updatedAt` | Son değişiklik | Instant | ✓ | auditing yazar | | |

Kullanıcı tablosu Security aşamasında (S2) doğar; o ana kadar sistemde kullanıcı kavramı yoktur. Todo, project ve tag
tablolarına sahip kolonu sonradan (S6) eklenir: "dolu bir tabloya zorunlu kolon nasıl eklenir?" sorusu orada konuşulur.

### Kural nerede korunur

| Kural | İstek | Domain / service | DB |
|---|:-:|:-:|:-:|
| Email biçimi, uzunluk (U3) | ✓ | | uzunluk |
| Email küçük harf (U2) | | ✓ normalize | ✓ "küçük harfe çevrilmiş haline eşit olmalı" kısıtı |
| Email tekil (U1) | | ✓ ön kontrol → 409 | ✓ tekillik (yarış) |
| Görünen ad boş olamaz (U4) | ✓ | ✓ kırpma | ✓ "boşluk dışı en az bir karakter" kısıtı |
| Parola uzunluğu (U5) | ✓ | | |
| Status değerleri | | enum | ✓ izin verilen değerler kısıtı |
| Kapalı kullanıcı giriş yapamaz (U6) | | ✓ (`UserDetails.isEnabled`) | |
| Kapatınca oturumların iptali (U7) | | ✓ tek transaction | |

### İlişkiler

- Todo, Project, Tag ve RefreshToken kullanıcıya bağlıdır (sahip). Kullanıcı bunlara **navigasyon tutmaz**: `User`
  entity'sinde `List<Todo>` yoktur. Kullanıcının todo'ları todo sorgusuyla gelir.
- Bağlı kayıtlar kullanıcıyı UUID olarak (`ownerId`) tutar ve DB'de FK ile kullanıcıya bağlanır.

### Kararlar

- **Silmek yerine kapatmak.** Silmek bütün todo, project, tag ve token kayıtlarını da silmeyi ya da sahipsiz bırakmayı
  gerektirir. Kapatma geri alınabilir bir yol bırakır (v1'de kullanılmasa da) ve "bu email bir daha kullanılamaz"
  kuralını kendiliğinden verir. Bedeli: kapalı hesapların verisi DB'de kalır. Gerçek bir üründe KVKK / GDPR silme talebi
  ayrı bir anonimleştirme işi olurdu (kapsam dışı).
- **Email'i küçük harf saklamak.** Alternatifler: DB'de büyük / küçük harf duyarsız bir metin tipi kullanmak, ya da tekilliği
  "küçük harfe çevrilmiş email" üzerinden kurmak. Küçük harf saklamak en basitidir ve her sorguda aynı çevrimi yapma
  zorunluluğunu kaldırır. DB kısıtı, unutulan bir normalize'ı yakalar.
- **Boolean değil enum.** `active: boolean` bugün yeter, ama "kilitli", "doğrulanmamış" gibi bir üçüncü durum geldiğinde
  ikinci bir boolean'a ve "ikisi birden true olursa?" sorusuna dönüşür.
- **U8 penceresi.** Access token doğrulanırken DB'ye gidilmez (S5). Bu her isteği hızlı ve DB'den bağımsız yapar, ama kapanan
  hesabın token'ı süresi dolana kadar çalışır. Anında iptal gerekseydi bir token kara listesi gerekirdi (opsiyonel O2'de Redis
  ile). 15 dk kabul edilebilir bir bedel.
- **`User` cevabı.** Entity REST cevabı olarak dönmez. `passwordHash` ve `status` dışarı sızmasın, cevap şekli de tablo
  şekline bağlı kalmasın diye ayrı bir response DTO kullanılır.

---

## 3. Kimlik doğrulama ve oturum

> Adımlar: **S1–S8** (sahiplik S6'da, §1). Kullanıcı kuralları: §2. Uçların istek / cevap şekilleri: §7.

### Kavramlar

- **Authentication (kimlik doğrulama):** "Sen kimsin?" Login'de parola ile, sonraki isteklerde access token ile yapılır.
- **Authorization (yetki):** "Bunu yapabilir misin?" Bu projede rol yok, sahiplik var (§1).
  Yetki her istekte DB sorgusuyla yapılır, token'dan okunmaz.
- **İki token, iki iş:**

| | Access token | Refresh token |
|---|---|---|
| Biçim | JWT (imzalı, içi okunabilir) | Opaque: anlamsız rastgele metin |
| Ömür | 15 dk | 14 gün (her yenilemede yeniden 14 gün) |
| Nerede taşınır | `Authorization: Bearer …` header'ı | `HttpOnly` cookie |
| Ne için | Her API isteği | Yalnız `/api/auth/refresh` ve `/logout` |
| Sunucu nasıl doğrular | İmza + claim'ler; **DB'ye gitmez** | DB'de hash'i aranır |
| Geri çağrılabilir mi | Hayır (süresi dolana kadar geçerli) | Evet (revoke) |

Neden iki token: kısa ömürlü access token çalınsa bile zarar 15 dk ile sınırlı kalır. Uzun ömürlü refresh token ise
JavaScript'in okuyamadığı bir cookie'de durur, yalnız tek bir uca gider ve her an iptal edilebilir.

### Akışlar

#### Register — `POST /api/auth/register` (S2)

1. İstek doğrulanır: email biçimi, parola uzunluğu, görünen ad (U3–U5). Hatalıysa 400.
2. Email normalize edilir (U2). Kayıtlıysa 409.
3. Parola hash'lenir (Argon2id). Hash'leme yavaştır (~50 ms) ve kasıtlıdır; DB transaction'ının **dışında** yapılır.
4. Kullanıcı `ACTIVE` olarak kaydedilir. Yarışta DB tekilliği yakalarsa yine 409.
5. Cevap 201 ve kullanıcı bilgisi. **Token dönmez**: kayıt ile giriş ayrı işlerdir, istemci ayrıca login olur.

#### Login — `POST /api/auth/login` (S3, S4, S7)

1. Email normalize edilir. Kullanıcı yüklenir, parola hash'e karşı doğrulanır.
2. Bilinmeyen email, yanlış parola ve kapalı hesap → **aynı 401, aynı gövde** (`Invalid email or password`). Gerçek sebep
   yalnız DEBUG log'a yazılır.
3. Bilinmeyen email'de de parola doğrulaması kadar zaman harcanır (Spring'in `DaoAuthenticationProvider`'ı bunu yapar);
   cevap süresi, email'in kayıtlı olup olmadığını ele vermez.
4. Başarıda: access token üretilir. Yeni bir oturum (aile) için refresh token üretilir ve hash'i DB'ye yazılır.
5. Cevap 200. Gövdede access token, `Set-Cookie`'de refresh token.

#### Korumalı istek (S5)

1. İstemci `Authorization: Bearer <access token>` gönderir.
2. Spring Security token'ı doğrular: imza (bizim public key), `alg` yalnız RS256, `iss`, `aud`, `exp` (60 sn saat farkı payı).
3. `sub` claim'i UUID'ye çevrilir ve isteğin kullanıcısı (`CurrentUser`) olur. **DB'ye gidilmez.**
4. Token yok ya da geçersizse 401 + `WWW-Authenticate: Bearer …`.

#### Refresh — `POST /api/auth/refresh` (S7)

1. İstek gövdesizdir; refresh token cookie'den okunur. Cookie yoksa 401.
2. Token'ın hash'i hesaplanır ve tek bir **koşullu güncelleme** çalışır: "bu hash'li, kullanılmamış, iptal edilmemiş, süresi
   dolmamış satırı kullanıldı olarak işaretle".
   - 1 satır değişti: bu istek token'ı aldı.
   - 0 satır değişti: token geçersiz.
3. Kullanıcı hâlâ `ACTIVE` mi kontrol edilir (U6). Değilse işlem geri alınır, 401.
4. Aynı ailede yeni bir refresh token üretilir, yeni access token üretilir.
5. 2–4 tek transaction'dır: biri başarısız olursa token harcanmamış sayılır.
6. Cevap login ile aynı şekildedir.

Bu uçta `Authorization` header'ı **yok sayılır**. İstemci süresi dolmuş access token'ı göndermeye devam ederse, Spring
Security isteği refresh'e ulaşmadan 401 ile keserdi.

#### Reuse detection (S8)

Kullanılmış bir refresh token tekrar gelirse token çalınmış sayılır: ya saldırgan ya da gerçek kullanıcı eski kopyayı
kullanıyordur, hangisi olduğu bilinemez. **Bütün aile iptal edilir**, istek 401 alır. İki taraf da yeniden login olmak
zorundadır.

Bilinen yanlış pozitif: iki sekme aynı anda yenileme yaparsa ikinci sekme "reuse" görünür ve oturum kapanır. Basitlik ve
güvenlik karşılığında kabul edilir (grace window yok).

#### Logout — `POST /api/auth/logout` (S8)

Cookie'deki token'ın ailesi iptal edilir. Cookie silinir (`Max-Age=0`). Cevap 204.

Token yoksa ya da zaten geçersizse de 204 döner: logout her durumda "artık çıkmış durumdasın" sonucunu verir. Access token
süresi dolana kadar çalışmaya devam eder (U8 ile aynı pencere).

#### Hesap kapatma — `DELETE /api/users/me` (S8)

Access token gerekir. Kullanıcı `DEACTIVATED` olur, bütün aileleri iptal edilir (U7), cookie silinir. Cevap 204.

### Access token içeriği (S4)

Header:

| Alan | Değer | Neden |
|---|---|---|
| `alg` | `RS256` | Asimetrik: private key yalnız imzalar, public key yalnız doğrular |
| `kid` | Public key'in RFC 7638 thumbprint'i | Key değişince hangi key ile doğrulanacağı token'dan bilinir |
| `typ` | `JWT` | Bilgi amaçlı |

Claim'ler:

| Claim | Değer | Kural |
|---|---|---|
| `iss` | Uygulamanın adresi (config) | Doğrulamada eşleşmeli |
| `sub` | Kullanıcının id'si (UUID metni) | Email değil: email değişebilir, id değişmez |
| `aud` | `deeptodo-api` | Doğrulamada eşleşmeli. Başka bir servis için üretilmiş token bize gelirse reddedilir |
| `iat` | Üretim zamanı | |
| `exp` | `iat` + 15 dk | Zorunlu |
| `jti` | Rastgele UUID | Token'ın kendi kimliği (ileride kara liste için) |

**Token'a yazılmayanlar:** email, isim, rol, sahiplik ve parolaya dair hiçbir şey. JWT şifreli değildir, yalnız imzalıdır;
içini herkes okuyabilir.

### Key yönetimi (S4)

- 2048 bit RSA key çifti. Local'de `secrets/` (gitignore), production'da ortam değişkeni ya da secret.
- Uygulama açılırken key'ler okunur. Key eksikse, 2048 bitten kısaysa ya da public key private key'e ait değilse uygulama
  **açılmaz** ve sebebi söyler.
- Rotation: yeni key çifti + yeniden başlatma. Eski key'le imzalı access token'lar 401 alır, istemci refresh ile yenisini alır.

### RefreshToken (entity)

#### Nedir

Bir oturumun bir halkası. Ham değeri yalnız istemcinin cookie'sinde durur. DB'de onu tanımaya yetecek hash'i ve
durumu tutulur.

#### Yaşam döngüsü

```text
             ┌──────────────▶ KULLANILMIŞ  (refresh'te; yerine halef üretildi)
  üretildi ──┼──────────────▶ İPTAL        (logout, reuse, hesap kapatma)
   (aktif)   └──────────────▶ SÜRESİ DOLMUŞ (expiresAt geçti; satır değişmez, zaman karar verir)
```

Bir token **aktif** ise kullanılmamış, iptal edilmemiş ve süresi dolmamıştır. Aktif olmayan token bir daha aktif olmaz.

#### Kurallar

| # | Kural |
|---|---|
| RT1 | Ham token 32 byte güvenli rastgele değerdir (`SecureRandom`), base64url metin olarak cookie'ye yazılır |
| RT2 | DB'de yalnız SHA-256 hash'i (64 karakter hex) durur. Hash sistem genelinde tekildir |
| RT3 | Her login yeni bir aile başlatır. Her refresh, aynı ailede bir halef üretir |
| RT4 | Bir token **en fazla bir kez** kullanılabilir. Aynı token'la eşzamanlı iki istekten yalnız biri kazanır (koşullu güncelleme) |
| RT5 | Kullanılmış token tekrar gelirse ailenin bütün aktif token'ları iptal edilir |
| RT6 | Bitiş zamanı oluşturma zamanından sonradır (14 gün) |
| RT7 | Token sahibinin `ACTIVE` olması gerekir (U6) |

#### Alanlar

| Alan | Anlamı | Tip | Zorunlu | Kural |
|---|---|---|:-:|---|
| `id` | Kimlik | UUID | ✓ | v7 |
| `userId` | Sahip | UUID | ✓ | var olan bir kullanıcı |
| `familyId` | Oturum | UUID | ✓ | login'de yeni, refresh'te aynısı |
| `tokenHash` | Ham token'ın SHA-256'sı | metin = 64 | ✓ | tekil; ham token asla |
| `createdAt` | Üretim | Instant | ✓ | |
| `expiresAt` | Bitiş | Instant | ✓ | `createdAt`'ten sonra (RT6) |
| `usedAt` | Kullanıldığı an | Instant | | dolu = kullanılmış |
| `revokedAt` | İptal anı | Instant | | dolu = iptal |

`updatedAt` yok: bu kayıt "değişen bir nesne" değil, bir olay kaydıdır. Yalnız `usedAt` / `revokedAt` bir kez yazılır.

#### Kural nerede korunur

| Kural | Domain / service | DB |
|---|:-:|:-:|
| Hash tekil (RT2) | | ✓ tekillik |
| Tek kullanım (RT4) | ✓ koşullu güncellemenin sonucu (1 / 0) | ✓ güncellemenin koşulu atomik çalışır |
| Bitiş > oluşturma (RT6) | ✓ | ✓ CHECK |
| Sahip var (FK) | | ✓ FK |

#### Cookie

| Özellik | Değer | Neden |
|---|---|---|
| Ad | `refresh_token` | |
| `HttpOnly` | var | JavaScript okuyamaz; XSS token'ı çalamaz |
| `SameSite` | `Strict` | Başka siteden gelen isteğe eklenmez; CSRF koruması bunun üzerine kurulu |
| `Path` | `/api/auth` | Yalnız auth uçlarına gider, her API isteğinde taşınmaz |
| `Max-Age` | 14 gün | |
| `Secure` | production'da var, local'de yok (config) | Yalnız HTTPS'te gönderilir; local HTTP'de olsaydı cookie hiç gitmezdi |

#### Kararlar

- **Opaque, JWT değil.** Refresh token'ın içinde taşınacak bilgi yok, zaten her seferinde DB'ye bakılıyor. JWT olsaydı
  imzası doğru ama iptal edilmiş bir token'ı ayırt etmek için yine DB gerekirdi.
- **SHA-256, Argon2 değil.** Argon2 düşük entropili parolaları tahmin etmeyi yavaşlatmak içindir. 256 bit rastgele
  değerin tahmin edilecek bir yanı yok; hızlı hash yeterli ve DB'de tek sorguyla aranabilir. Parolada tersi geçerli.
- **Hash'i saklamak.** DB sızarsa saldırgan geçerli token'ları elde edemez.
- **Koşullu güncelleme, `@Version` değil.** "Önce oku, kullanılmamışsa güncelle" iki adımdır; iki istek aynı anda okursa ikisi
  de "kullanılmamış" görür. Tek bir koşullu UPDATE bu yarışı DB'de atomik olarak çözer. `@Version` da çözerdi, ama okuma +
  çakışma exception'ı gerekir ve niyeti daha az açık anlatır.
- **CSRF koruması kapalı.** Access token header'da taşınır; tarayıcı onu kendiliğinden eklemez, yani CSRF'e açık değil.
  Cookie kullanan yalnız refresh ve logout var; onları `SameSite=Strict` korur.
- **Halef bağlantısı yok.** "Bu token'ın yerine hangisi geldi" tutulmaz. Reuse detection için aile + `usedAt` yeterli.

### Hata modları (S adımlarında denenerek gösterilir)

| Durum | Sonuç |
|---|---|
| DB çöktü, istek access token ile geliyor | Kimlik doğrulama geçer (DB'siz). Veri isteyen uç 500 / 503 |
| DB çöktü, login | 500 (401 değil: "yanlış parola" demek yalan olur) |
| `alg=none` ya da HS256'ya çevrilmiş token | 401 |
| Başka key ile imzalanmış token, aynı `kid` | 401 (imza tutmaz) |
| Süresi 30 sn önce dolmuş token | 200 (60 sn saat farkı payı) |
| Süresi 2 dk önce dolmuş token | 401 |
| Aynı refresh token ile 10 paralel istek | 1 × 200, 9 × 401; ailede 1 halef |

---

## 4. Todo

> Adımlar: **C2** (domain sınıfı, durum makinesi, bellekte), **W1** (REST), **W3** (PATCH), **J1** (tablo, entity),
> **J2** (sayfalama, `@Version`), **J3** (project), **J4** (tag'ler), **J5** (filtreleme, N+1), **S6** (sahiplik). Genel kurallar: §1.

### Nedir

Yapılacak bir iş. Projenin merkezi: project ve tag, todo'ları düzenlemek için var.

### Yaşam döngüsü: durum makinesi

```text
oluştur ─▶ TODO
           açık işler:       TODO ⇄ IN_PROGRESS
           bitirme:          TODO | IN_PROGRESS ─▶ DONE
           vazgeçme:         TODO | IN_PROGRESS ─▶ CANCELLED
           yeniden açma:     DONE ─▶ TODO | IN_PROGRESS
                             CANCELLED ─▶ TODO
```

| Şuradan → | TODO | IN_PROGRESS | DONE | CANCELLED |
|---|:-:|:-:|:-:|:-:|
| **TODO** | – | ✓ | ✓ | ✓ |
| **IN_PROGRESS** | ✓ | – | ✓ | ✓ |
| **DONE** | ✓ | ✓ | – | ✗ |
| **CANCELLED** | ✓ | ✗ | ✗ | – |

- ✗ olan geçişler **409** döner. İptal edilmiş bir işi tamamlamak için önce yeniden açmak gerekir; tamamlanmış bir işi
  iptal etmek anlamsızdır.
- Aynı duruma "geçiş" (DONE → DONE) hata değildir, hiçbir şey değiştirmez (`completedAt` de değişmez).
- Todo **gerçekten silinir** (hard delete).

### Kurallar

| # | Kural |
|---|---|
| TD1 | Başlık kırpılır; kırpıldıktan sonra 1–200 karakterdir |
| TD2 | Açıklama isteğe bağlı, en fazla 2000 karakter. Boş string `null` sayılır |
| TD3 | Yeni todo `TODO` durumunda ve `MEDIUM` öncelikle başlar (istekte öncelik verilirse o kullanılır; durum verilemez) |
| TD4 | Durum değişiklikleri yalnız yukarıdaki tabloya göre olur |
| TD5 | `completedAt` **yalnız ve her zaman** `DONE` iken doludur. DONE'a girerken o anın zamanı yazılır, DONE'dan çıkarken temizlenir |
| TD6 | `dueAt` isteğe bağlı ve geçmiş olabilir (gecikmiş iş meşrudur) |
| TD7 | Project isteğe bağlıdır. Verilirse aynı sahibin ve `ACTIVE` bir project'i olmalıdır: başkasının ya da olmayan project 404, arşivlenmiş project 409 |
| TD8 | Project'i sonradan arşivlenen todo'lar olduğu gibi kalır, düzenlenebilir ve project'ten çıkarılabilir. Arşivlenmiş project'e **taşınamaz** (TD7) |
| TD9 | Eşzamanlı güncelleme korunur: istemci güncellemede elindeki `version`'ı gönderir. Version tutmazsa 409 (arada başkası değiştirmiş) |
| TD10 | Silme, todo'nun tag bağlantılarını aynı transaction'da kaldırır, sonra todo'yu siler. Tag'ler silinmez |
| TD11 | Bütün işlemler sahibe göre: başkasının todo'su her uçta 404 |

### Alanlar

| Alan | Anlamı | Tip | Zorunlu | Kural | Geçerli | Geçersiz |
|---|---|---|:-:|---|---|---|
| `id` | Kimlik | UUID | ✓ | v7 | | |
| `ownerId` | Sahip | UUID | ✓ | oluşturan kullanıcı; değişmez | | |
| `project` | Bağlı project | Project | | TD7, TD8 | | başkasının project'i |
| `title` | Başlık | metin ≤ 200 | ✓ | TD1 | `Literatür taraması` | `""`, `"   "` |
| `description` | Açıklama | metin ≤ 2000 | | TD2 | | 2001 karakter |
| `status` | Durum | enum `TodoStatus` | ✓ | `TODO`, `IN_PROGRESS`, `DONE`, `CANCELLED`; TD4 | | `done` |
| `priority` | Öncelik | enum `TodoPriority` | ✓ | `LOW`, `MEDIUM`, `HIGH`, `URGENT` | | `CRITICAL` |
| `dueAt` | Bitiş tarihi | Instant | | TD6 | `2026-11-20T09:00:00Z` | `2026-11-20` (saatsiz, saat dilimsiz) |
| `completedAt` | Tamamlanma | Instant | | TD5; istemci yazamaz | | |
| `version` | Sürüm | long | ✓ | optimistic locking; 0'dan başlar, JPA artırır; istemci yazamaz | | |
| `createdAt`, `updatedAt` | | Instant | ✓ | auditing | | |

### Kural nerede korunur

| Kural | İstek | Domain | Sorgu | DB |
|---|:-:|:-:|:-:|:-:|
| Başlık boş olamaz, uzunluk (TD1) | ✓ | ✓ kırpma | | ✓ "boşluk dışı en az bir karakter" kısıtı |
| Açıklama uzunluğu (TD2) | ✓ | | | uzunluk |
| Durum / öncelik değerleri | ✓ (enum'a çevrilemezse 400) | | | ✓ izin verilen değerler |
| Geçiş tablosu (TD4) | | ✓ **entity metodu** (`changeStatus`) | | |
| `completedAt` ↔ DONE (TD5) | | ✓ aynı metot | | ✓ "DONE ise dolu, değilse boş" CHECK'i |
| Project aynı sahipte (TD7) | | ✓ project sahibe göre aranır → 404 | ✓ | ✓ composite FK: (project, sahip) çifti project tablosunda olmalı |
| Project aktif (TD7) | | ✓ → 409 | | |
| Eşzamanlı güncelleme (TD9) | | ✓ `@Version` | | ✓ version kolonu |
| Sahiplik (TD11) | | | ✓ `id + ownerId` | ✓ FK |

TD4 ve TD5'in yeri önemli: durum, setter ile değil, kuralı bilen bir metotla değişir. `setStatus(DONE)` yazılabilseydi,
`completedAt`'i unutmak tek satırlık bir hata olurdu. Metot kuralı tek yerde tutar. DB CHECK'i ise metodu bypass eden
(elle SQL, yanlış bir güncelleme sorgusu) her yolu yakalar.

### Güncelleme anlamı (PATCH)

`PATCH /api/todos/{id}` kısmi güncellemedir (JSON Merge Patch anlamı):

| İstekte alan | Anlamı |
|---|---|
| hiç yok | değişmez |
| değer var | o değere değişir |
| `null` | temizlenir. Yalnız isteğe bağlı alanlarda: `description`, `dueAt`, `projectId`. Zorunlu alana (`title`, `status`, `priority`) `null` → 400 |
| `version` | **zorunlu**; tutmazsa 409 |

"Alan hiç yok" ile "alan `null`" farkı Java'da kendiliğinden görünmez: Jackson ikisini de `null` alana çevirir. Bu farkı
yakalamak W3'ün konusudur (seçenekler görev kartında).

### Listeleme ve filtreleme (W1, J2, J5)

`GET /api/todos` yalnız isteği yapanın todo'larını döner.

| Parametre | Anlamı | Kural |
|---|---|---|
| `page` | Sayfa (0'dan) | ≥ 0 |
| `size` | Sayfa boyutu | varsayılan 20, en fazla 100 (fazlası 100'e indirilir) |
| `sort` | Sıralama | `createdAt`, `dueAt`, `priority`, `title`; yön `asc` / `desc`; varsayılan `createdAt,desc`. Listede olmayan alan → 400 |
| `status` | Durum filtresi | birden çok verilebilir (`status=TODO&status=IN_PROGRESS`) |
| `priority` | Öncelik filtresi | birden çok verilebilir |
| `projectId` | Project filtresi | sahibin olmayan project → boş liste (hata değil) |
| `tagId` | Tag filtresi | todo'da bu tag takılı olmalı |
| `dueBefore`, `dueAfter` | Bitiş aralığı | `dueAt` olmayan todo'lar bu filtrelerde dışarıda kalır |
| `q` | Metin arama | başlık **veya** açıklamada, büyük / küçük harf duyarsız, "içerir" |

- Filtreler birbirine **VE** ile bağlanır. Aynı filtrenin birden çok değeri **VEYA**'dır.
- `priority` sıralaması alfabetik değil, anlamına göredir: `LOW < MEDIUM < HIGH < URGENT`. Enum metin olarak saklandığı
  için DB alfabetik sıralar; bunun çözümü J5'te konuşulur.
- Sayfadaki her todo, tag'leriyle birlikte döner. Sorgu sayısı sayfa boyutundan **bağımsız** olmalıdır: N+1 yok (J5'te ölçülür).

### İlişkiler

| İlişki | Tip | Neden |
|---|---|---|
| Todo → sahip | `ownerId` (UUID), navigasyon yok | Todo'dan kullanıcı nesnesine gitmeye hiç ihtiyaç yok; yalnız sorgu filtresi |
| Todo → Project | `@ManyToOne`, LAZY, isteğe bağlı | Cevapta project'in adı da gösterilebilir. Projenin `@ManyToOne` örneği |
| Todo → Tag'ler | `TodoTag` üzerinden; detay §6 | Join kaydının kendi verisi ve kuralları var |

### Kararlar

- **Hard delete.** Todo'ya başka hiçbir kayıt bağlı değil (tag bağlantıları onunla birlikte gider), denetim izi gereksinimi
  yok. "Vazgeçilmiş iş" anlamı için zaten `CANCELLED` var.
- **`@Version` yalnız todo'da.** Gerçekten eşzamanlı güncellenen tek kayıt bu: iki sekme, iki cihaz. Project ve tag adı
  için "son yazan kazanır" kabul edilebilir.
- **Version'ı istemci gönderir.** `@Version` tek başına yalnız aynı transaction içindeki yarışı yakalar. "Ben okuduktan
  sonra başkası değiştirdi mi?" sorusu, istemcinin okuduğu version'ı geri göndermesiyle cevaplanır.
- **Composite FK.** "Project aynı sahipte" kuralı service'te kontrol ediliyor. DB'de de durması için project tablosunda
  `(id, sahip)` çiftinin tekil olması ve todo'nun `(project, sahip)` çiftiyle ona bağlanması gerekir. Böylece yanlış sahipli
  bir bağlantı DB'ye hiç giremez.
- **Durum geçiş tablosu.** Serbest geçişe izin vermek daha basit olurdu. Tablo, iş kuralının domain nesnesinde nasıl
  durduğunu öğretmek için seçildi (ve gerçek ürünlerde de genellikle böyledir).

---

## 5. Project

> Adımlar: **J3**, **S6** (sahiplik ve aynı sahip FK'si). Todo ile bağlantısı: §4 TD7–TD8. Genel kurallar: §1.

### Nedir

Todo'ları gruplayan klasör: "Tez", "Ev", "İş başvuruları". Bir todo en fazla bir project'te olur.

### Yaşam döngüsü

```text
 oluştur              arşivle (POST /api/projects/{id}/archive)
─────────▶ ACTIVE ─────────────────────────────────────────▶ ARCHIVED
```

- Project **silinmez**, arşivlenir. v1'de arşivden çıkarma yok.
- Arşivlenmiş project **salt okunurdur**: adı ve açıklaması değişmez, yeni todo alamaz. İçindeki todo'lar yaşamaya devam eder.

### Kurallar

| # | Kural |
|---|---|
| PR1 | Ad kırpılır; kırpıldıktan sonra 1–120 karakterdir |
| PR2 | Aynı sahibin iki project'i aynı adı taşıyamaz; büyük / küçük harf duyarsız. **Arşivlenmiş project'ler de adlarını tutar** ("Tez" arşivdeyken yeni bir "tez" açılamaz) |
| PR3 | Açıklama isteğe bağlı, en fazla 1000 karakter; boş string `null` sayılır |
| PR4 | Yeni project `ACTIVE` başlar |
| PR5 | Arşivlenmiş project'e todo eklenemez ya da taşınamaz → 409 (§4 TD7) |
| PR6 | Arşivlenmiş project güncellenemez → 409. Zaten arşivlenmiş project'i arşivlemek hata değildir (204, değişiklik yok) |
| PR7 | Arşivleme, project'in todo'larına dokunmaz |
| PR8 | Listeleme varsayılan olarak yalnız `ACTIVE` project'leri döner; `?status=ARCHIVED` ile arşiv görülür. Sıra: ada göre |
| PR9 | Sahiplik: başkasının project'i her uçta 404 |

### Alanlar

| Alan | Anlamı | Tip | Zorunlu | Kural | Geçerli | Geçersiz |
|---|---|---|:-:|---|---|---|
| `id` | Kimlik | UUID | ✓ | v7 | | |
| `ownerId` | Sahip | UUID | ✓ | değişmez | | |
| `name` | Ad | metin ≤ 120 | ✓ | PR1, PR2 | `Tez` | `""`; aynı sahipte `tez` varken `TEZ` |
| `description` | Açıklama | metin ≤ 1000 | | PR3 | | |
| `status` | Durum | enum `ProjectStatus` | ✓ | `ACTIVE`, `ARCHIVED` | | `DELETED` |
| `createdAt`, `updatedAt` | | Instant | ✓ | auditing | | |

### Kural nerede korunur

| Kural | İstek | Domain | Sorgu | DB |
|---|:-:|:-:|:-:|:-:|
| Ad boş olamaz, uzunluk (PR1) | ✓ | ✓ kırpma | | ✓ "boşluk dışı en az bir karakter" kısıtı |
| Ad tekil, harf duyarsız (PR2) | | ✓ ön kontrol → 409 | | ✓ "sahip + küçük harfe çevrilmiş ad" tekilliği |
| Status değerleri | | enum | | ✓ izin verilen değerler |
| Arşivliyken salt okunur (PR5, PR6) | | ✓ entity metotları (`archive`, `rename`) durumu kontrol eder | | |
| Sahiplik (PR9) | | | ✓ `id + ownerId` | ✓ FK |
| Todo'nun project'i aynı sahipte | | | | ✓ project'te `(id, sahip)` çifti tekil; todo buna bağlanır |

PR2'deki DB tekilliği bir **ifade üzerinde** kurulur (sütunun kendisi değil, küçük harfe çevrilmiş hali). Bunun nasıl
yapıldığı J3'ün DB konusu (sahip kolonu S6'da tekilliğe katılır). Burada ad olduğu gibi saklanır ("Tez"); kullanıcının yazdığı biçim korunur, yalnız tekillik harf
duyarsızdır. Email'den farkı bu: orada saklanan değer zaten küçük harfti (§2).

### İlişkiler

- Project → sahip: `ownerId` (UUID), navigasyon yok.
- Project → Todo'lar: **navigasyon yok** (`List<Todo>` tutulmaz). Bir project'in todo'ları, todo listesinin `projectId`
  filtresiyle gelir. İlişkinin sahibi todo tarafıdır (FK todo tablosunda).

### Kararlar

- **Silmek yerine arşivlemek.** Silmek, todo'ların ne olacağı sorusunu açar: onlar da mı silinsin, yoksa project'siz mi
  kalsın? Arşivleme bu soruyu ortadan kaldırır ve "bitti ama geçmişini görmek istiyorum" ihtiyacını karşılar.
- **`@OneToMany` yok.** Project'in todo listesi sınırsız büyüyebilir. Entity'de bir koleksiyon olarak tutmak, ya yanlışlıkla
  hepsini yüklemeye ya da sayfalanamayan bir listeye götürür. Liste bir sorgunun işidir.
- **Arşivlenmiş adlar tekilliği tutar.** Daha basit: tekillik bir koşula bağlı değil. Alternatif (yalnız aktifler arasında
  tekillik) "kısmi tekillik" gerektirir. Gerçek bir ihtiyaç doğarsa yeni bir migration ile değiştirilir.
- **`(id, sahip)` tekilliği.** `id` zaten tekil olduğu için bu çift de kendiliğinden tekildir. Yeni bir kısıt gibi görünse
  de tek amacı todo'nun composite FK'sine hedef olmaktır.

---

## 6. Tag ve etiketleme (TodoTag)

> Adımlar: **J4**, **J5** (filtre ve yükleme), **S6** (sahiplik ve aynı sahip FK'leri). Genel kurallar: §1.

### Tag

#### Nedir

Todo'lara takılan serbest etiket: "acil", "okul", "beklemede". Project'ten farkı: bir todo'nun **birden çok** tag'i olabilir,
tag bir klasör değil bir işarettir.

#### Yaşam döngüsü

Oluşturulur, adı ya da rengi değişebilir, **gerçekten silinir**. Silinince bağlantıları da gider, todo'lar kalır.

#### Kurallar

| # | Kural |
|---|---|
| TG1 | Ad kırpılır; kırpıldıktan sonra 1–50 karakterdir |
| TG2 | Aynı sahibin iki tag'i aynı adı taşıyamaz; büyük / küçük harf duyarsız |
| TG3 | Renk isteğe bağlıdır. Verilirse `#` + 6 onaltılık rakamdır (`#E11D48`). Büyük harfe çevrilerek saklanır (`#e11d48` → `#E11D48`) |
| TG4 | Tag silinince bütün todo bağlantıları aynı transaction'da kaldırılır, sonra tag silinir. Todo'lar etkilenmez |
| TG5 | Listeleme ada göre sıralı, sayfasız (bir kullanıcının tag sayısı küçük kabul edilir) |
| TG6 | Sahiplik: başkasının tag'i her uçta 404 |

#### Alanlar

| Alan | Anlamı | Tip | Zorunlu | Kural | Geçerli | Geçersiz |
|---|---|---|:-:|---|---|---|
| `id` | Kimlik | UUID | ✓ | v7 | | |
| `ownerId` | Sahip | UUID | ✓ | değişmez | | |
| `name` | Ad | metin ≤ 50 | ✓ | TG1, TG2 | `acil` | `""`; `acil` varken `ACİL` |
| `color` | Renk | metin = 7 | | TG3 | `#E11D48` | `red`, `#E11`, `E11D48` |
| `createdAt`, `updatedAt` | | Instant | ✓ | auditing | | |

#### Kural nerede korunur

| Kural | İstek | Domain | DB |
|---|:-:|:-:|:-:|
| Ad boş olamaz, uzunluk (TG1) | ✓ | ✓ kırpma | ✓ "boşluk dışı en az bir karakter" kısıtı |
| Ad tekil (TG2) | | ✓ ön kontrol → 409 | ✓ "sahip + küçük harfe çevrilmiş ad" tekilliği |
| Renk biçimi (TG3) | ✓ regex | ✓ büyük harf | ✓ biçim kısıtı |
| Silmede bağlantılar (TG4) | | ✓ tek transaction | ✓ FK, bağlantı varken tag silinemez (sessiz cascade yok) |

### Etiketleme (TodoTag)

#### Nedir

"Şu tag şu todo'ya takılı" kaydı. Todo ile Tag arasındaki çoka-çok ilişkinin kendisi.

#### Kurallar

| # | Kural |
|---|---|
| TT1 | Todo ve tag **aynı sahibe** ait olmalıdır. Başkasının todo'su ya da tag'i 404 |
| TT2 | Aynı tag aynı todo'ya bir kez takılır. Takılı tag'i yeniden takmak hata değildir (204, ikinci kayıt oluşmaz) |
| TT3 | Takılı olmayan tag'i çıkarmak hata değildir (204). Ama todo ya da tag yoksa / başkasınınsa 404 |
| TT4 | Bağlantının kaldırılması todo'yu da tag'i de silmez |
| TT5 | Takılma zamanı (`addedAt`) tutulur |

TT2 ve TT3 **idempotent**'tir: aynı istek kaç kez gelirse gelsin sonuç aynıdır. Bu yüzden ekleme `PUT` (bir kaynağı
"olsun" diye koyar), çıkarma `DELETE`'tir (§7).

#### Alanlar

| Alan | Anlamı | Tip | Zorunlu | Kural |
|---|---|---|:-:|---|
| `id` | Kimlik | UUID | ✓ | v7 |
| `ownerId` | Sahip | UUID | ✓ | todo'nun ve tag'in sahibiyle aynı (TT1) |
| `todo` | Todo | Todo | ✓ | `@ManyToOne`, LAZY |
| `tag` | Tag | Tag | ✓ | `@ManyToOne`, LAZY |
| `addedAt` | Takılma zamanı | Instant | ✓ | TT5 |

`createdAt` / `updatedAt` yok. Bağlantı değişmez, ya vardır ya yoktur. `addedAt` "oluşturma zamanı"nın domain'deki adı.

#### Kural nerede korunur

| Kural | Domain | Sorgu | DB |
|---|:-:|:-:|:-:|
| Aynı sahip (TT1) | ✓ todo ve tag sahibe göre aranır → 404 | ✓ | ✓ iki composite FK: (todo, sahip) ve (tag, sahip) çiftleri |
| Tek bağlantı (TT2) | ✓ var mı kontrolü → değişiklik yok | | ✓ (todo, tag) tekilliği (yarış) |

TT2'deki yarış: aynı anda iki "tak" isteği "yok" görüp ikisi de eklemeye çalışırsa, DB tekilliği ikincisini reddeder.
İdempotent bir uçta bu hata kullanıcıya 409 olarak değil, başarı (204) olarak dönmelidir: sonuçta tag takılıdır.

#### İlişkiler ve navigasyon

```text
Todo  1 ──── N  TodoTag  N ──── 1  Tag
```

- TodoTag → Todo ve TodoTag → Tag: `@ManyToOne`, LAZY. İlişkinin sahibi (FK'ler) TodoTag'dedir.
- Todo → TodoTag koleksiyonu (`@OneToMany(mappedBy)`): todo cevabında tag'ler gösterildiği için **gerekebilir**. Tutup
  tutmamak J4'ün karar noktasıdır. Tutulursa nasıl yükleneceği (N+1) J5'in konusudur.
- Tag → TodoTag koleksiyonu: tutulmaz. "Bu tag hangi todo'larda" sorusu todo listesinin `tagId` filtresinin işi.

### Kararlar

- **`@ManyToMany` değil, açık entity.** `@ManyToMany`'de join tablosu görünmezdir: `addedAt` ve sahip kolonu eklenemez,
  bağlantının yaşam döngüsü (idempotent ekleme, silmede temizlik) kontrol edilemez, Hibernate koleksiyon değişikliğinde
  bütün satırları silip yeniden ekleyebilir. Açık entity'de her şey görünür.
- **Sessiz cascade yok.** DB'de "tag silinince bağlantıları da sil" kuralı tanımlanabilirdi. Seçilmedi: silme, service'te
  açıkça ve aynı transaction'da yapılır. Biri bunu unutursa FK hatası alır, veri sessizce kaybolmaz.
- **Bağlantıda sahip kolonu.** Sahip, todo'dan da tag'den de bulunabilir; tekrar ediyor gibi görünür. Ama "iki taraf aynı
  sahipte" kuralını DB'de kurmanın tek yolu, iki composite FK'nin aynı sahip kolonunu paylaşmasıdır.
- **Tag silinir, project arşivlenir.** Tag hafif bir işarettir; silinmesi todo'lardan bir etiketi kaldırır, başka bir şey
  kaybolmaz. Project ise todo'ların bağlamıdır (§5).

---

## 7. API ve hata sözleşmesi

> Hata sözleşmesi **W2**'de kurulur; uçlar ilgili adımlarda eklenir. Kurallar entity bölümlerinde, burada yalnız şekiller.

### Genel

- Kök yol `/api`. İstek ve cevap gövdeleri JSON, alan adları camelCase.
- Zamanlar ISO-8601 UTC: `"2026-11-12T10:15:30Z"`. Saatsiz ya da saat dilimsiz tarih kabul edilmez (400).
- Id'ler UUID metni. Path'teki id UUID değilse 400.
- Oluşturma uçları 201 ve `Location: /api/<kaynak>/<id>` döner, gövdede oluşturulan kayıt durur.
- `/api/auth/register`, `/login`, `/refresh`, `/logout` ve `/actuator/health` dışındaki her uç
  `Authorization: Bearer <access token>` ister (S1'den itibaren).
- Cevapta entity yoktur; her cevap bir DTO'dur (record).

### Uçlar

#### Auth ve kullanıcı (§3)

| Uç | İstek gövdesi | Başarı | Hatalar |
|---|---|---|---|
| `POST /api/auth/register` | `email`, `password`, `displayName` | 201 `User` (Location yok) | 400, 409 email kayıtlı |
| `POST /api/auth/login` | `email`, `password` | 200 `Login` + `Set-Cookie` | 400, 401 |
| `POST /api/auth/refresh` | — (cookie) | 200 `Login` + `Set-Cookie` | 401 |
| `POST /api/auth/logout` | — (cookie) | 204 + cookie silme | — |
| `GET /api/users/me` | — | 200 `User` | 401 |
| `DELETE /api/users/me` | — | 204 + cookie silme | 401 |

#### Todo (§4)

| Uç | İstek | Başarı | Hatalar |
|---|---|---|---|
| `POST /api/todos` | `title`, `description?`, `priority?`, `dueAt?`, `projectId?` | 201 `Todo` | 400; 404 project; 409 arşivli project |
| `GET /api/todos` | query: §4 "Listeleme ve filtreleme" | 200 `Page<Todo>` | 400 geçersiz sort / parametre |
| `GET /api/todos/{id}` | — | 200 `Todo` | 404 |
| `PATCH /api/todos/{id}` | `version` + değişen alanlar (`title`, `description`, `status`, `priority`, `dueAt`, `projectId`) | 200 `Todo` | 400; 404; 409 geçersiz geçiş / version / arşivli project |
| `DELETE /api/todos/{id}` | — | 204 | 404 |
| `PUT /api/todos/{id}/tags/{tagId}` | — | 204 | 404 todo ya da tag |
| `DELETE /api/todos/{id}/tags/{tagId}` | — | 204 | 404 todo ya da tag |

#### Project (§5)

| Uç | İstek | Başarı | Hatalar |
|---|---|---|---|
| `POST /api/projects` | `name`, `description?` | 201 `Project` | 400, 409 ad |
| `GET /api/projects` | `?status=ACTIVE\|ARCHIVED` (varsayılan ACTIVE) | 200 `Project[]` | |
| `GET /api/projects/{id}` | — | 200 `Project` | 404 |
| `PATCH /api/projects/{id}` | `name?`, `description?` | 200 `Project` | 400, 404, 409 ad / arşivli |
| `POST /api/projects/{id}/archive` | — | 204 | 404 |

#### Tag (§6)

| Uç | İstek | Başarı | Hatalar |
|---|---|---|---|
| `POST /api/tags` | `name`, `color?` | 201 `Tag` | 400, 409 ad |
| `GET /api/tags` | — | 200 `Tag[]` | |
| `PATCH /api/tags/{id}` | `name?`, `color?` | 200 `Tag` | 400, 404, 409 ad |
| `DELETE /api/tags/{id}` | — | 204 | 404 |

### Cevap şekilleri

```text
User     { id, email, displayName, createdAt }
Login    { accessToken, tokenType: "Bearer", expiresIn: 900 }
Todo     { id, title, description, status, priority, dueAt, completedAt,
           project: { id, name } | null,
           tags: [ { id, name, color } ],          ← ada göre sıralı
           version, createdAt, updatedAt }
Project  { id, name, description, status, createdAt, updatedAt }
Tag      { id, name, color, createdAt, updatedAt }
Page<T>  { content: [T], page, size, totalElements, totalPages }
```

- `User` cevabında `passwordHash` ve `status` yok.
- `Page<T>` **kendi DTO'muzdur**. Spring Data'nın `Page` nesnesini doğrudan JSON'a yazmak, iç yapısını (sort,
  pageable alanları) API sözleşmesi yapar; Spring Data bunun için uyarı da loglar.
- Değeri olmayan isteğe bağlı alanlar `null` olarak yazılır (alan atlanmaz). İstemci her zaman aynı şekli görür.

### Hata sözleşmesi (W2, S1)

Her hata `application/problem+json` ve `ProblemDetail` şeklindedir:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "A project named 'Tez' already exists",
  "instance": "/api/projects"
}
```

Validation hatalarında ayrıca hangi alanın neden hatalı olduğu:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/todos",
  "errors": [
    { "field": "title", "message": "must not be blank" },
    { "field": "dueAt", "message": "must be an ISO-8601 instant" }
  ]
}
```

| Durum | Status | Kim üretir |
|---|---|---|
| Validation, bozuk JSON, geçersiz parametre / UUID / enum | 400 | MVC + `@RestControllerAdvice` |
| Token yok / geçersiz / süresi dolmuş | 401 + `WWW-Authenticate: Bearer …` | Security filter zinciri |
| Login başarısız, geçersiz refresh token | 401 | auth uçlarının handler'ı |
| Kayıt yok ya da başkasının | 404 | `@RestControllerAdvice` (domain exception) |
| Yanlış method (`GET /api/auth/login`) | 405 | MVC |
| Tekillik, geçersiz geçiş, arşivli project, version çakışması | 409 | `@RestControllerAdvice` |
| Beklenmeyen hata | 500, `detail` genel bir cümle | `@RestControllerAdvice` |

Kurallar:
- **Domain exception'ları HTTP bilmez.** Service `ResourceNotFoundException` fırlatır, 404'e çeviren advice'tır. Service
  bir gün HTTP olmayan bir yerden (bir consumer, bir job) çağrılırsa aynı exception anlamlı kalır.
- 500'de stack trace, SQL ya da sınıf adı **dışarı sızmaz**; ayrıntı yalnız log'dadır.
- DB tekillik ihlali (yarış) 409'a çevrilirken constraint adına bakılır. Her `DataIntegrityViolationException` 409 değildir;
  tanınmayan bir ihlal 500'dür.
- Filter zincirindeki hatalar (401 / 403) MVC'ye ulaşmadan oluşur. `@RestControllerAdvice` onları göremez; Security'nin
  kendi handler'ı aynı `ProblemDetail` şeklini üretir (S1).

---

## 8. Kalite: test, loglama, performans, bitti tanımı

> Adımlar: **Q1–Q3**. Kurallar her adımda geçerli; testlerin çoğu Q1'de yazılır.

### Test

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

### Loglama

- **Asla loglanmaz:** parola, parola hash'i, access token, refresh token (ham ya da hash), `Authorization` ve `Cookie` header'ları.
- Login başarısızlığının gerçek sebebi (bilinmeyen email / yanlış parola / kapalı hesap) DEBUG seviyesinde, email ile birlikte.
- Beklenmeyen 500'ler ERROR seviyesinde, stack trace ile.
- SQL loglaması yalnız local profilde ve gerektiğinde açılır.

### Performans

- Todo listesi tek sayfa için sabit sayıda sorgu çalıştırır. Tag'ler ve project adı için todo başına ayrı sorgu (N+1) yok.
- Sayfa boyutu en fazla 100.
- Liste sorgusunun en sık kullanılan yolu (sahip + sıralama) index'le desteklenir. Hangi index'in gerektiğine
  `EXPLAIN ANALYZE` çıktısıyla karar verilir, tahminle değil (J5).
- Hikari bağlantı havuzu varsayılan boyutta kalır; ölçmeden değiştirilmez.

### Konfigürasyon

| Ayar | Local | Production |
|---|---|---|
| DB bağlantısı | `compose.yaml` varsayılanları | ortam değişkeni |
| JWT key'leri | `secrets/` dosyaları | ortam değişkeni / secret |
| JWT issuer | `http://localhost:8080` | public adres |
| Refresh cookie `Secure` | kapalı | açık |
| Actuator | health + gözlem endpoint'leri (local profili) | yalnız health |

Hiçbir parola, key ya da token repoya girmez.

### Bitti tanımı (v1)

1. `docker compose up` app ile PostgreSQL'i birlikte açar; app health `UP`.
2. OpenAPI arayüzünden bütün uçlar Bearer token ile denenebilir.
3. Yukarıdaki test tablosunun tamamı yeşil (`./mvnw verify`).
4. README: projeyi çalıştırma, mimari özet, önemli tasarım kararları (bu spec'in "Kararlar" bölümlerinden).
5. Spec ile kod çelişmiyor. Çelişki varsa ya kod ya spec düzeltilmiş.
