# DeepToDo Spec — 04 Project

> Adım: **T3**. Todo ile bağlantısı: [03-todo](03-todo.md) TD7–TD8. Genel kurallar: [00-genel-bakis](00-genel-bakis.md).

## Nedir

Todo'ları gruplayan klasör: "Tez", "Ev", "İş başvuruları". Bir todo en fazla bir project'te olur.

## Yaşam döngüsü

```text
 oluştur              arşivle (POST /api/projects/{id}/archive)
─────────▶ ACTIVE ─────────────────────────────────────────▶ ARCHIVED
```

- Project **silinmez**, arşivlenir. v1'de arşivden çıkarma yok.
- Arşivlenmiş project **salt okunurdur**: adı ve açıklaması değişmez, yeni todo alamaz. İçindeki todo'lar yaşamaya devam eder.

## Kurallar

| # | Kural |
|---|---|
| PR1 | Ad kırpılır; kırpıldıktan sonra 1–120 karakterdir |
| PR2 | Aynı sahibin iki project'i aynı adı taşıyamaz; büyük / küçük harf duyarsız. **Arşivlenmiş project'ler de adlarını tutar** ("Tez" arşivdeyken yeni bir "tez" açılamaz) |
| PR3 | Açıklama isteğe bağlı, en fazla 1000 karakter; boş string `null` sayılır |
| PR4 | Yeni project `ACTIVE` başlar |
| PR5 | Arşivlenmiş project'e todo eklenemez ya da taşınamaz → 409 ([03-todo](03-todo.md) TD7) |
| PR6 | Arşivlenmiş project güncellenemez → 409. Zaten arşivlenmiş project'i arşivlemek hata değildir (204, değişiklik yok) |
| PR7 | Arşivleme, project'in todo'larına dokunmaz |
| PR8 | Listeleme varsayılan olarak yalnız `ACTIVE` project'leri döner; `?status=ARCHIVED` ile arşiv görülür. Sıra: ada göre |
| PR9 | Sahiplik: başkasının project'i her uçta 404 |

## Alanlar

| Alan | Anlamı | Tip | Zorunlu | Kural | Geçerli | Geçersiz |
|---|---|---|:-:|---|---|---|
| `id` | Kimlik | UUID | ✓ | v7 | | |
| `ownerId` | Sahip | UUID | ✓ | değişmez | | |
| `name` | Ad | metin ≤ 120 | ✓ | PR1, PR2 | `Tez` | `""`; aynı sahipte `tez` varken `TEZ` |
| `description` | Açıklama | metin ≤ 1000 | | PR3 | | |
| `status` | Durum | enum `ProjectStatus` | ✓ | `ACTIVE`, `ARCHIVED` | | `DELETED` |
| `createdAt`, `updatedAt` | | Instant | ✓ | auditing | | |

## Kural nerede korunur

| Kural | İstek | Domain | Sorgu | DB |
|---|:-:|:-:|:-:|:-:|
| Ad boş olamaz, uzunluk (PR1) | ✓ | ✓ kırpma | | ✓ "boşluk dışı en az bir karakter" kısıtı |
| Ad tekil, harf duyarsız (PR2) | | ✓ ön kontrol → 409 | | ✓ "sahip + küçük harfe çevrilmiş ad" tekilliği |
| Status değerleri | | enum | | ✓ izin verilen değerler |
| Arşivliyken salt okunur (PR5, PR6) | | ✓ entity metotları (`archive`, `rename`) durumu kontrol eder | | |
| Sahiplik (PR9) | | | ✓ `id + ownerId` | ✓ FK |
| Todo'nun project'i aynı sahipte | | | | ✓ project'te `(id, sahip)` çifti tekil; todo buna bağlanır |

PR2'deki DB tekilliği bir **ifade üzerinde** kurulur (sütunun kendisi değil, küçük harfe çevrilmiş hali). Bunun nasıl
yapıldığı T3'ün DB konusu. Burada ad olduğu gibi saklanır ("Tez"); kullanıcının yazdığı biçim korunur, yalnız tekillik harf
duyarsızdır. Email'den farkı bu: orada saklanan değer zaten küçük harfti ([01-kullanici](01-kullanici.md)).

## İlişkiler

- Project → sahip: `ownerId` (UUID), navigasyon yok.
- Project → Todo'lar: **navigasyon yok** (`List<Todo>` tutulmaz). Bir project'in todo'ları, todo listesinin `projectId`
  filtresiyle gelir. İlişkinin sahibi todo tarafıdır (FK todo tablosunda).

## Kararlar

- **Silmek yerine arşivlemek.** Silmek, todo'ların ne olacağı sorusunu açar: onlar da mı silinsin, yoksa project'siz mi
  kalsın? Arşivleme bu soruyu ortadan kaldırır ve "bitti ama geçmişini görmek istiyorum" ihtiyacını karşılar.
- **`@OneToMany` yok.** Project'in todo listesi sınırsız büyüyebilir. Entity'de bir koleksiyon olarak tutmak, ya yanlışlıkla
  hepsini yüklemeye ya da sayfalanamayan bir listeye götürür. Liste bir sorgunun işidir.
- **Arşivlenmiş adlar tekilliği tutar.** Daha basit: tekillik bir koşula bağlı değil. Alternatif (yalnız aktifler arasında
  tekillik) "kısmi tekillik" gerektirir. Gerçek bir ihtiyaç doğarsa yeni bir migration ile değiştirilir.
- **`(id, sahip)` tekilliği.** `id` zaten tekil olduğu için bu çift de kendiliğinden tekildir. Yeni bir kısıt gibi görünse
  de tek amacı todo'nun composite FK'sine hedef olmaktır.
