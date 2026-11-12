# DeepToDo Spec — 05 Tag ve etiketleme (TodoTag)

> Adım: **T4** (filtre ve yükleme T5'te). Genel kurallar: [00-genel-bakis](00-genel-bakis.md).

## Tag

### Nedir

Todo'lara takılan serbest etiket: "acil", "okul", "beklemede". Project'ten farkı: bir todo'nun **birden çok** tag'i olabilir,
tag bir klasör değil bir işarettir.

### Yaşam döngüsü

Oluşturulur, adı ya da rengi değişebilir, **gerçekten silinir**. Silinince bağlantıları da gider, todo'lar kalır.

### Kurallar

| # | Kural |
|---|---|
| TG1 | Ad kırpılır; kırpıldıktan sonra 1–50 karakterdir |
| TG2 | Aynı sahibin iki tag'i aynı adı taşıyamaz; büyük / küçük harf duyarsız |
| TG3 | Renk isteğe bağlıdır. Verilirse `#` + 6 onaltılık rakamdır (`#E11D48`). Büyük harfe çevrilerek saklanır (`#e11d48` → `#E11D48`) |
| TG4 | Tag silinince bütün todo bağlantıları aynı transaction'da kaldırılır, sonra tag silinir. Todo'lar etkilenmez |
| TG5 | Listeleme ada göre sıralı, sayfasız (bir kullanıcının tag sayısı küçük kabul edilir) |
| TG6 | Sahiplik: başkasının tag'i her uçta 404 |

### Alanlar

| Alan | Anlamı | Tip | Zorunlu | Kural | Geçerli | Geçersiz |
|---|---|---|:-:|---|---|---|
| `id` | Kimlik | UUID | ✓ | v7 | | |
| `ownerId` | Sahip | UUID | ✓ | değişmez | | |
| `name` | Ad | metin ≤ 50 | ✓ | TG1, TG2 | `acil` | `""`; `acil` varken `ACİL` |
| `color` | Renk | metin = 7 | | TG3 | `#E11D48` | `red`, `#E11`, `E11D48` |
| `createdAt`, `updatedAt` | | Instant | ✓ | auditing | | |

### Kural nerede korunur

| Kural | İstek | Domain | DB |
|---|:-:|:-:|:-:|
| Ad boş olamaz, uzunluk (TG1) | ✓ | ✓ kırpma | ✓ "boşluk dışı en az bir karakter" kısıtı |
| Ad tekil (TG2) | | ✓ ön kontrol → 409 | ✓ "sahip + küçük harfe çevrilmiş ad" tekilliği |
| Renk biçimi (TG3) | ✓ regex | ✓ büyük harf | ✓ biçim kısıtı |
| Silmede bağlantılar (TG4) | | ✓ tek transaction | ✓ FK, bağlantı varken tag silinemez (sessiz cascade yok) |

## Etiketleme (TodoTag)

### Nedir

"Şu tag şu todo'ya takılı" kaydı. Todo ile Tag arasındaki çoka-çok ilişkinin kendisi.

### Kurallar

| # | Kural |
|---|---|
| TT1 | Todo ve tag **aynı sahibe** ait olmalıdır. Başkasının todo'su ya da tag'i 404 |
| TT2 | Aynı tag aynı todo'ya bir kez takılır. Takılı tag'i yeniden takmak hata değildir (204, ikinci kayıt oluşmaz) |
| TT3 | Takılı olmayan tag'i çıkarmak hata değildir (204). Ama todo ya da tag yoksa / başkasınınsa 404 |
| TT4 | Bağlantının kaldırılması todo'yu da tag'i de silmez |
| TT5 | Takılma zamanı (`addedAt`) tutulur |

TT2 ve TT3 **idempotent**'tir: aynı istek kaç kez gelirse gelsin sonuç aynıdır. Bu yüzden ekleme `PUT` (bir kaynağı
"olsun" diye koyar), çıkarma `DELETE`'tir ([06-api](06-api.md)).

### Alanlar

| Alan | Anlamı | Tip | Zorunlu | Kural |
|---|---|---|:-:|---|
| `id` | Kimlik | UUID | ✓ | v7 |
| `ownerId` | Sahip | UUID | ✓ | todo'nun ve tag'in sahibiyle aynı (TT1) |
| `todo` | Todo | Todo | ✓ | `@ManyToOne`, LAZY |
| `tag` | Tag | Tag | ✓ | `@ManyToOne`, LAZY |
| `addedAt` | Takılma zamanı | Instant | ✓ | TT5 |

`createdAt` / `updatedAt` yok. Bağlantı değişmez, ya vardır ya yoktur. `addedAt` "oluşturma zamanı"nın domain'deki adı.

### Kural nerede korunur

| Kural | Domain | Sorgu | DB |
|---|:-:|:-:|:-:|
| Aynı sahip (TT1) | ✓ todo ve tag sahibe göre aranır → 404 | ✓ | ✓ iki composite FK: (todo, sahip) ve (tag, sahip) çiftleri |
| Tek bağlantı (TT2) | ✓ var mı kontrolü → değişiklik yok | | ✓ (todo, tag) tekilliği (yarış) |

TT2'deki yarış: aynı anda iki "tak" isteği "yok" görüp ikisi de eklemeye çalışırsa, DB tekilliği ikincisini reddeder.
İdempotent bir uçta bu hata kullanıcıya 409 olarak değil, başarı (204) olarak dönmelidir: sonuçta tag takılıdır.

### İlişkiler ve navigasyon

```text
Todo  1 ──── N  TodoTag  N ──── 1  Tag
```

- TodoTag → Todo ve TodoTag → Tag: `@ManyToOne`, LAZY. İlişkinin sahibi (FK'ler) TodoTag'dedir.
- Todo → TodoTag koleksiyonu (`@OneToMany(mappedBy)`): todo cevabında tag'ler gösterildiği için **gerekebilir**. Tutup
  tutmamak T4'ün karar noktasıdır. Tutulursa nasıl yükleneceği (N+1) T5'in konusudur.
- Tag → TodoTag koleksiyonu: tutulmaz. "Bu tag hangi todo'larda" sorusu todo listesinin `tagId` filtresinin işi.

## Kararlar

- **`@ManyToMany` değil, açık entity.** `@ManyToMany`'de join tablosu görünmezdir: `addedAt` ve sahip kolonu eklenemez,
  bağlantının yaşam döngüsü (idempotent ekleme, silmede temizlik) kontrol edilemez, Hibernate koleksiyon değişikliğinde
  bütün satırları silip yeniden ekleyebilir. Açık entity'de her şey görünür.
- **Sessiz cascade yok.** DB'de "tag silinince bağlantıları da sil" kuralı tanımlanabilirdi. Seçilmedi: silme, service'te
  açıkça ve aynı transaction'da yapılır. Biri bunu unutursa FK hatası alır, veri sessizce kaybolmaz.
- **Bağlantıda sahip kolonu.** Sahip, todo'dan da tag'den de bulunabilir; tekrar ediyor gibi görünür. Ama "iki taraf aynı
  sahipte" kuralını DB'de kurmanın tek yolu, iki composite FK'nin aynı sahip kolonunu paylaşmasıdır.
- **Tag silinir, project arşivlenir.** Tag hafif bir işarettir; silinmesi todo'lardan bir etiketi kaldırır, başka bir şey
  kaybolmaz. Project ise todo'ların bağlamıdır ([04-project](04-project.md)).
