# DeepToDo — Status

> [docs/spec](docs/spec/00-genel-bakis.md) hedefi, bu dosya repository'nin şu anki gerçek durumunu gösterir. Her adımın review'ı bitince güncellenir.

## Last updated

2026-11-13

## Current phase

**Sıfırdan, Onat'ın yazdığı küçük proje (spec v7, `docs/spec/`).** Karar (2026-11-11): proje küçültüldü (workspace / üyelik / atama yok,
kişisel todo + project + tag + auth) ve repo tamamen boşaltıldı. Kodun tamamını Onat yazar; Claude görev kartı verir ve
review eder. 2026-11-12: spec domain'i anlatan, SQL'siz, entity başına bir dosyalık bir dokümana dönüştü
(`docs/spec/`, Obsidian'da kopyası).

## Active task

**C1** — kurulum (DB'siz Boot iskeleti). Onat'ın başlatmasını bekliyor ("sıradaki" → görev kartı).

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
