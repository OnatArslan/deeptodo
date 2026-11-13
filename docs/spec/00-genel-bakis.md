# DeepToDo Spec — 00 Genel bakış

> Spec v7 (2026-11-12). SQL içermez; her alanın sağlaması gereken kuralları ve nedenlerini anlatır.
> Ana kopya repoda (`docs/spec/`), aynısı Obsidian'da (`notes/plan/java-spring/deeptodo-spec/`).

## Bu spec nasıl okunur

| Dosya | İçerik | Kullanan adımlar |
|---|---|---|
| [00-genel-bakis](00-genel-bakis.md) | ürün, sözlük, entity haritası, bütün entity'lerin ortak kuralları | hepsi |
| [01-kullanici](01-kullanici.md) | User | S2, S3, S8 |
| [02-kimlik-dogrulama](02-kimlik-dogrulama.md) | register, login, token'lar, oturum, RefreshToken | S1–S8 |
| [03-todo](03-todo.md) | Todo, durum makinesi, listeleme | C2, W1, W3, J1, J2, J5, S6 |
| [04-project](04-project.md) | Project | J3, S6 |
| [05-tag](05-tag.md) | Tag ve TodoTag (etiketleme) | J4, J5, S6 |
| [06-api](06-api.md) | uçlar, istek / cevap şekilleri, hata sözleşmesi | W1–W3 ve her adımın uçları |
| [07-kalite](07-kalite.md) | testler, loglama, performans, bitti tanımı | Q1–Q3 |

Her entity dosyası aynı sırayı izler:

1. **Nedir**
2. **Yaşam döngüsü**
3. **Kurallar:** numaralı ve test edilebilir.
4. **Alanlar**
5. **Kural nerede korunur**
6. **İlişkiler**
7. **Kararlar:** neden böyle, seçilmeyen alternatif.

Bir adıma başlarken önce o adımın dosyasını oku. Görev kartı bu dosyadaki kuralları koda dökme işidir.

**Yapım sırası** (ROADMAP): Spring Core → Web → Data JPA → Security. Spec projenin **son halini** anlatır; yol üstünde
bazı kurallar henüz geçerli değildir:
- J1'e kadar veri bellekte tutulur (DB yok); `createdAt` / `updatedAt` ve `version` JPA ile gelir.
- S6'ya kadar kayıtların sahibi yoktur: herkes her şeyi görür, sahiplik kuralları (404, aynı sahip FK'leri) S6'da eklenir.
- S1'e kadar bütün uçlar açıktır.

## Ürün

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

## Sözlük

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

## Entity haritası

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
  Bu kural hem kodda hem DB'de korunur (bkz. [04-project](04-project.md), [05-tag](05-tag.md)).

## Temel ilke: sahiplik

Sistemde rol yok (admin, editor…). Yetki tek bir soruya iner: **bu kayıt isteği yapan kullanıcının mı?**

- Bütün okuma ve yazmalar sahibin kimliğiyle sınırlanır. Kayıt, "bu id" **ve** "bu sahip" ile aranır. Önce id ile bulup
  sonra sahibi kontrol etmek ikinci tercihtir: unutulmaya açıktır, ayrıca iki ayrı hata yolu üretir.
- Başkasının kaydı **404** döner, 403 değil. 403, "kayıt var ama senin değil" demektir; kaydın varlığını sızdırır.
- Sahiplik bilgisi token'a yazılmaz. Token yalnız "sen kimsin"i söyler; "neyin sahibisin" her istekte DB'den gelir.

## Ortak kurallar (bütün entity'ler)

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

## Kural katmanları: bir kural nerede korunur?

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

## Kapsam dışı

Workspace, paylaşım, rol ve admin; e-posta doğrulama ve parola sıfırlama; dosya eki, bildirim, hatırlatma, tekrarlayan görev;
frontend; microservice. Keycloak, Redis, Kafka ve public deploy yalnız ROADMAP'in opsiyonel O bölümünde, v1'den sonra.

## Teknoloji

| Alan | Seçim |
|---|---|
| Dil / build | Java 21, Maven |
| Framework | Spring Boot 4.1 (Framework 7, Security 7) |
| Web | Spring Web MVC, Bean Validation, `ProblemDetail` |
| Persistence | PostgreSQL 17, Flyway, Spring Data JPA (Hibernate 7) |
| Security | Spring Security, OAuth2 Resource Server (JWT, RS256), Argon2id |
| Test | JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL) |
| Paket | springdoc-openapi, Docker |
