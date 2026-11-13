# DeepToDo Spec — 03 Todo

> Adımlar: **C2** (domain sınıfı, durum makinesi, bellekte), **W1** (REST), **W3** (PATCH), **J1** (tablo, entity),
> **J2** (sayfalama, `@Version`), **J3** (project), **J4** (tag'ler), **J5** (filtreleme, N+1), **S6** (sahiplik). Genel kurallar: [00-genel-bakis](00-genel-bakis.md).

## Nedir

Yapılacak bir iş. Projenin merkezi: project ve tag, todo'ları düzenlemek için var.

## Yaşam döngüsü: durum makinesi

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

## Kurallar

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

## Alanlar

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

## Kural nerede korunur

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

## Güncelleme anlamı (PATCH)

`PATCH /api/todos/{id}` kısmi güncellemedir (JSON Merge Patch anlamı):

| İstekte alan | Anlamı |
|---|---|
| hiç yok | değişmez |
| değer var | o değere değişir |
| `null` | temizlenir. Yalnız isteğe bağlı alanlarda: `description`, `dueAt`, `projectId`. Zorunlu alana (`title`, `status`, `priority`) `null` → 400 |
| `version` | **zorunlu**; tutmazsa 409 |

"Alan hiç yok" ile "alan `null`" farkı Java'da kendiliğinden görünmez: Jackson ikisini de `null` alana çevirir. Bu farkı
yakalamak W3'ün konusudur (seçenekler görev kartında).

## Listeleme ve filtreleme (W1, J2, J5)

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

## İlişkiler

| İlişki | Tip | Neden |
|---|---|---|
| Todo → sahip | `ownerId` (UUID), navigasyon yok | Todo'dan kullanıcı nesnesine gitmeye hiç ihtiyaç yok; yalnız sorgu filtresi |
| Todo → Project | `@ManyToOne`, LAZY, isteğe bağlı | Cevapta project'in adı da gösterilebilir. Projenin `@ManyToOne` örneği |
| Todo → Tag'ler | `TodoTag` üzerinden; detay [05-tag](05-tag.md) | Join kaydının kendi verisi ve kuralları var |

## Kararlar

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
