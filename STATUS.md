# DeepToDo — Status

> [SPEC.md](SPEC.md) hedefi, bu dosya repository'nin şu anki gerçek durumunu gösterir. Her adımın review'ı bitince güncellenir.

## Last updated

2026-11-23

## Current phase

**Sıfırdan, küçük proje (spec v8, `SPEC.md`).** Karar (2026-11-11): proje küçültüldü (workspace / üyelik / atama yok,
kişisel todo + project + tag + auth) ve repo tamamen boşaltıldı. **Karar (2026-11-23, kilitli): kodu Claude yazar, Onat aktif
review ile öğrenir;** dört çekirdek parçayı (C2, W2, S1, S7) Onat yazar. Ayrıntı `CLAUDE.md` → "Roller". 2026-11-12: spec domain'i anlatan, SQL'siz, entity başına bir dosyalık bir dokümana dönüştü
(2026-11-13'ten beri tek dosya: `SPEC.md`, Obsidian'da kopyası).

## Active task

**C1** — kurulum (DB'siz Boot iskeleti). Plan anlatıldı; Boot 4.2.0 seçildi (karar 2026-11-23). Onat Spring Core kursunu okuyor (1, 4, 5, 6; 9 review için); okuyunca onay verecek.

Sıra (karar 2026-11-13): Spring Core → Web → Data JPA → Security (ROADMAP C → W → J → S).

## Completed steps

_Yok._

## Local environment

- PostgreSQL (`compose.yaml`) host'ta `127.0.0.1:5433` → container 5432. Host 5432 bu makinede başka bir projede (`library-postgres`).
- **Dikkat:** local DB volume'ünde önceki sürümden bir `users` tablosu ve `flyway_schema_history` duruyor. İlk migration'dan
  (J1) önce volume sıfırlanmalı (`docker compose down -v && docker compose up -d`), yoksa Flyway eski V1'in checksum'ıyla çakışır.
- `secrets/` (gitignore) içinde önceki sürümden 2048-bit JWT key çifti var; S4'te kullanılabilir ya da yeniden üretilir.

## Applied migrations

_Yok._

## Known technical debt

_Yok._

## Locked scope decisions

Uygulama sırasında yeniden açılmaz (değişiklik Onat'ın kararıdır):

- kişisel veri: her kayıt bir sahibe ait, paylaşım / workspace / rol yok
- başkasının kaydı 404 (403 değil)
- Todo → Project opsiyonel; Tag ilişkisi açık `TodoTag` entity'si
- User kapatılır (silinmez); Project arşivlenir; Todo ve Tag hard delete
- auth uygulamada, Spring Security ile; Keycloak / Redis / Kafka / deploy yalnız opsiyonel O bölümünde
