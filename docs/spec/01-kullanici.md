# DeepToDo Spec — 01 Kullanıcı (User)

> Adımlar: **S2** (tablo, entity, parola, register), **S3** (login), **S8** (hesap kapatma). Genel kurallar: [00-genel-bakis](00-genel-bakis.md).

## Nedir

Sisteme kayıtlı kişi. Bütün todo, project, tag ve oturumların sahibidir. Kimliği **email** ile tanınır, **görünen adı**
(displayName) ile hitap edilir.

## Yaşam döngüsü

```text
  register            hesap kapatma (DELETE /api/users/me)
 ─────────▶ ACTIVE ─────────────────────────────────────▶ DEACTIVATED
```

- Kayıtla `ACTIVE` başlar.
- Hesap kapatılınca `DEACTIVATED` olur. v1'de geri dönüş yok.
- Kullanıcı **silinmez**. Kapalı hesabın verisi DB'de durur ama kimse erişemez: sahibi giriş yapamaz, başkası zaten göremez.

## Kurallar

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

## Alanlar

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

## Kural nerede korunur

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

## İlişkiler

- Todo, Project, Tag ve RefreshToken kullanıcıya bağlıdır (sahip). Kullanıcı bunlara **navigasyon tutmaz**: `User`
  entity'sinde `List<Todo>` yoktur. Kullanıcının todo'ları todo sorgusuyla gelir.
- Bağlı kayıtlar kullanıcıyı UUID olarak (`ownerId`) tutar ve DB'de FK ile kullanıcıya bağlanır.

## Kararlar

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
