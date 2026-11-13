# DeepToDo Spec — 02 Kimlik doğrulama ve oturum

> Adımlar: **S1–S8** (sahiplik S6'da, [00-genel-bakis](00-genel-bakis.md)). Kullanıcı kuralları: [01-kullanici](01-kullanici.md). Uçların istek / cevap şekilleri: [06-api](06-api.md).

## Kavramlar

- **Authentication (kimlik doğrulama):** "Sen kimsin?" Login'de parola ile, sonraki isteklerde access token ile yapılır.
- **Authorization (yetki):** "Bunu yapabilir misin?" Bu projede rol yok, sahiplik var ([00-genel-bakis](00-genel-bakis.md)).
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

## Akışlar

### Register — `POST /api/auth/register` (S2)

1. İstek doğrulanır: email biçimi, parola uzunluğu, görünen ad (U3–U5). Hatalıysa 400.
2. Email normalize edilir (U2). Kayıtlıysa 409.
3. Parola hash'lenir (Argon2id). Hash'leme yavaştır (~50 ms) ve kasıtlıdır; DB transaction'ının **dışında** yapılır.
4. Kullanıcı `ACTIVE` olarak kaydedilir. Yarışta DB tekilliği yakalarsa yine 409.
5. Cevap 201 ve kullanıcı bilgisi. **Token dönmez**: kayıt ile giriş ayrı işlerdir, istemci ayrıca login olur.

### Login — `POST /api/auth/login` (S3, S4, S7)

1. Email normalize edilir. Kullanıcı yüklenir, parola hash'e karşı doğrulanır.
2. Bilinmeyen email, yanlış parola ve kapalı hesap → **aynı 401, aynı gövde** (`Invalid email or password`). Gerçek sebep
   yalnız DEBUG log'a yazılır.
3. Bilinmeyen email'de de parola doğrulaması kadar zaman harcanır (Spring'in `DaoAuthenticationProvider`'ı bunu yapar);
   cevap süresi, email'in kayıtlı olup olmadığını ele vermez.
4. Başarıda: access token üretilir. Yeni bir oturum (aile) için refresh token üretilir ve hash'i DB'ye yazılır.
5. Cevap 200. Gövdede access token, `Set-Cookie`'de refresh token.

### Korumalı istek (S5)

1. İstemci `Authorization: Bearer <access token>` gönderir.
2. Spring Security token'ı doğrular: imza (bizim public key), `alg` yalnız RS256, `iss`, `aud`, `exp` (60 sn saat farkı payı).
3. `sub` claim'i UUID'ye çevrilir ve isteğin kullanıcısı (`CurrentUser`) olur. **DB'ye gidilmez.**
4. Token yok ya da geçersizse 401 + `WWW-Authenticate: Bearer …`.

### Refresh — `POST /api/auth/refresh` (S7)

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

### Reuse detection (S8)

Kullanılmış bir refresh token tekrar gelirse token çalınmış sayılır: ya saldırgan ya da gerçek kullanıcı eski kopyayı
kullanıyordur, hangisi olduğu bilinemez. **Bütün aile iptal edilir**, istek 401 alır. İki taraf da yeniden login olmak
zorundadır.

Bilinen yanlış pozitif: iki sekme aynı anda yenileme yaparsa ikinci sekme "reuse" görünür ve oturum kapanır. Basitlik ve
güvenlik karşılığında kabul edilir (grace window yok).

### Logout — `POST /api/auth/logout` (S8)

Cookie'deki token'ın ailesi iptal edilir. Cookie silinir (`Max-Age=0`). Cevap 204.

Token yoksa ya da zaten geçersizse de 204 döner: logout her durumda "artık çıkmış durumdasın" sonucunu verir. Access token
süresi dolana kadar çalışmaya devam eder (U8 ile aynı pencere).

### Hesap kapatma — `DELETE /api/users/me` (S8)

Access token gerekir. Kullanıcı `DEACTIVATED` olur, bütün aileleri iptal edilir (U7), cookie silinir. Cevap 204.

## Access token içeriği (S4)

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

## Key yönetimi (S4)

- 2048 bit RSA key çifti. Local'de `secrets/` (gitignore), production'da ortam değişkeni ya da secret.
- Uygulama açılırken key'ler okunur. Key eksikse, 2048 bitten kısaysa ya da public key private key'e ait değilse uygulama
  **açılmaz** ve sebebi söyler.
- Rotation: yeni key çifti + yeniden başlatma. Eski key'le imzalı access token'lar 401 alır, istemci refresh ile yenisini alır.

## RefreshToken (entity)

### Nedir

Bir oturumun bir halkası. Ham değeri yalnız istemcinin cookie'sinde durur. DB'de onu tanımaya yetecek hash'i ve
durumu tutulur.

### Yaşam döngüsü

```text
             ┌──────────────▶ KULLANILMIŞ  (refresh'te; yerine halef üretildi)
  üretildi ──┼──────────────▶ İPTAL        (logout, reuse, hesap kapatma)
   (aktif)   └──────────────▶ SÜRESİ DOLMUŞ (expiresAt geçti; satır değişmez, zaman karar verir)
```

Bir token **aktif** ise kullanılmamış, iptal edilmemiş ve süresi dolmamıştır. Aktif olmayan token bir daha aktif olmaz.

### Kurallar

| # | Kural |
|---|---|
| RT1 | Ham token 32 byte güvenli rastgele değerdir (`SecureRandom`), base64url metin olarak cookie'ye yazılır |
| RT2 | DB'de yalnız SHA-256 hash'i (64 karakter hex) durur. Hash sistem genelinde tekildir |
| RT3 | Her login yeni bir aile başlatır. Her refresh, aynı ailede bir halef üretir |
| RT4 | Bir token **en fazla bir kez** kullanılabilir. Aynı token'la eşzamanlı iki istekten yalnız biri kazanır (koşullu güncelleme) |
| RT5 | Kullanılmış token tekrar gelirse ailenin bütün aktif token'ları iptal edilir |
| RT6 | Bitiş zamanı oluşturma zamanından sonradır (14 gün) |
| RT7 | Token sahibinin `ACTIVE` olması gerekir (U6) |

### Alanlar

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

### Kural nerede korunur

| Kural | Domain / service | DB |
|---|:-:|:-:|
| Hash tekil (RT2) | | ✓ tekillik |
| Tek kullanım (RT4) | ✓ koşullu güncellemenin sonucu (1 / 0) | ✓ güncellemenin koşulu atomik çalışır |
| Bitiş > oluşturma (RT6) | ✓ | ✓ CHECK |
| Sahip var (FK) | | ✓ FK |

### Cookie

| Özellik | Değer | Neden |
|---|---|---|
| Ad | `refresh_token` | |
| `HttpOnly` | var | JavaScript okuyamaz; XSS token'ı çalamaz |
| `SameSite` | `Strict` | Başka siteden gelen isteğe eklenmez; CSRF koruması bunun üzerine kurulu |
| `Path` | `/api/auth` | Yalnız auth uçlarına gider, her API isteğinde taşınmaz |
| `Max-Age` | 14 gün | |
| `Secure` | production'da var, local'de yok (config) | Yalnız HTTPS'te gönderilir; local HTTP'de olsaydı cookie hiç gitmezdi |

### Kararlar

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

## Hata modları (S adımlarında denenerek gösterilir)

| Durum | Sonuç |
|---|---|
| DB çöktü, istek access token ile geliyor | Kimlik doğrulama geçer (DB'siz). Veri isteyen uç 500 / 503 |
| DB çöktü, login | 500 (401 değil: "yanlış parola" demek yalan olur) |
| `alg=none` ya da HS256'ya çevrilmiş token | 401 |
| Başka key ile imzalanmış token, aynı `kid` | 401 (imza tutmaz) |
| Süresi 30 sn önce dolmuş token | 200 (60 sn saat farkı payı) |
| Süresi 2 dk önce dolmuş token | 401 |
| Aynı refresh token ile 10 paralel istek | 1 × 200, 9 × 401; ailede 1 halef |
