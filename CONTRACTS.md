# CONTRACTS.md — Kapas Ki Sehat (Android App)

> **Source of truth for the Android (Kotlin/Compose) client** of the Kapas Ki Sehat
> cotton pest-detection system. Documents every external surface the app touches:
> Supabase, the FastAPI inference backend, data models, enums, language codes, and
> hardcoded configuration.
>
> **How this file relates to `MASTER-CONTRACTS.md`:** `MASTER-CONTRACTS.md` is a
> manually-assembled aggregate of the CONTRACTS.md from all three repos (app, backend,
> dashboard) and is periodically replaced wholesale. **This file is the authoritative
> reference for Android work.** When MASTER changes (because the backend/dashboard
> changed), reconcile the relevant updates *into* this file — don't defer to MASTER
> directly.
>
> **Reading convention:** Each shared surface describes the canonical contract and the
> current code behaviour. Where the two still differ a 🟡 note is inline. Resolved
> divergences are tracked in §10.
>
> **Generated:** 2026-06-01 · last reconciled with MASTER-CONTRACTS.md: 2026-06-03 (v4)
> · gradle `versionName 1.0` · `applicationId` `com.aistudio.kapaskisehat.kzhfpx`
> · `namespace` `com.example`

---

## 1. Supabase

### Client configuration
Defined in [CottonAceApplication.kt](app/src/main/java/com/example/CottonAceApplication.kt):

| Setting | Source | Notes |
|---|---|---|
| `supabaseUrl` | `BuildConfig.SUPABASE_URL` | from `.env` via Secrets plugin |
| `supabaseKey` | `BuildConfig.SUPABASE_ANON_KEY` | JWT-format anon key; `sb_publishable_` rejected by Storage |
| Installed modules | `Postgrest`, `Storage` | Auth/Realtime not installed |

> Keys must be the JWT (`eyJh…`) format — see `.env.example` for required key names.

### Tables the app writes (Postgrest)
All Supabase writes happen in [DataSyncWorker.kt](app/src/main/java/com/example/network/DataSyncWorker.kt) (background `WorkManager` job). The app **only writes**; it does not read any Supabase table.

| Table | Operation | Notes |
|---|---|---|
| `farmers_profiles` | `upsert` | Failure is logged but non-fatal (sync continues). |
| `diagnostic_logs` | `insert` (batch) | One insert with all pending unsynced scans. Supabase fires the backend webhook on INSERT. |

#### `diagnostic_logs` — canonical schema
Schema is managed manually in the Supabase SQL editor; no component owns migrations.

| Column | Type | Nullable | Owner | Notes |
|---|---|---|---|---|
| `id` | `uuid` | NO | Supabase | PK, auto |
| `device_id` | `varchar` | YES | App | FK → `farmers_profiles.device_id` |
| `timestamp` | `timestamptz` | YES | App | Client event time, ISO-8601 UTC |
| `district` | `varchar` | **NO** | App | Required. Currently hardcoded `"Multan Belt"` (🟡 A2) |
| `whitefly_count` | `integer` | **NO** | App | Required. Real value from ML — ⚠️ backend stubs `12` (§11) |
| `risk_level` | `varchar` | **NO** | App | Required — enum §4 |
| `confidence_score` | `numeric` | **NO** | App | Required — real `ScanResponse.confidence`, 0.0–1.0 |
| `inference_time_ms` | `integer` | **NO** | App | Required — measured round-trip (includes network) |
| `image_storage_path` | `text` | YES | App | Bare object key `{device_id}/{epoch_ms}.jpg` — no bucket prefix, no leading slash. Null if upload failed |
| `latitude` | `double precision` | YES | App | GPS — `null` if unavailable, **not `0.0`** |
| `longitude` | `double precision` | YES | App | GPS — `null` if unavailable, **not `0.0`** |
| `agricultural_belt` | `varchar` | YES | App | e.g. `"Southern Punjab"` |
| `created_at` | `timestamptz` | YES | Supabase | Auto |

> ❌ There is **no `status`** column and **no `image_url`** column. Use `image_storage_path`.

**Current code** — `DiagnosticLogPayload` ([DataSyncWorker.kt](app/src/main/java/com/example/network/DataSyncWorker.kt)) conforms to the canonical schema:
```kotlin
@Serializable
data class DiagnosticLogPayload(
    val device_id: String,
    val district: String,
    val whitefly_count: Int,              // real value from ScanResponse
    val risk_level: String,
    val confidence_score: Float,          // real ScanResponse.confidence, 0.0–1.0
    val timestamp: String,                // ISO-8601 UTC
    val inference_time_ms: Int,           // measured round-trip (includes network)
    val image_storage_path: String? = null, // bare object key; null if upload failed
    val latitude: Double? = null,         // null when GPS unavailable — never 0.0
    val longitude: Double? = null,
    val agricultural_belt: String? = null // 🟡 null placeholder; future: derive from district
)
```
All values are real (Phase 3). `agricultural_belt` is intentionally null pending derivation logic.

#### `farmers_profiles` — canonical schema

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `uuid` | NO | PK |
| `device_id` | `varchar` | **NO** | Unique device identity (SHA-256 hash) |
| `registered_at` | `timestamptz` | YES | |
| `last_active_at` | `timestamptz` | YES | |
| `app_version` | `varchar` | **NO** | gradle `versionName`, currently `"1.0"` |
| `preferred_language` | `varchar` | **NO** | language **code** §5 (`"ur"`, not `"URDU"`) |

**Current code** — `ProfilePayload` sends `device_id`, `app_version = BuildConfig.VERSION_NAME` (`"1.0"`), `preferred_language = "ur"`. Omits `registered_at`/`last_active_at` (nullable — acceptable). The `"ur"` code is currently hardcoded and does not yet reflect the user's live language selection (🟡 §10 #6 follow-up).

#### Tables the app does NOT touch (owned elsewhere; listed for cross-repo awareness)
`model_deployments`, `harvested_images_pool`, `system_health_telemetry` — read/written by backend & dashboard. The Android app has no contract with these today. (`system_health_telemetry.log_level` ∈ `INFO|WARN|ERROR` if the app ever reports telemetry.)

---

## 2. Supabase Storage

| Bucket | App role | Visibility | Purpose |
|---|---|---|---|
| `leaf-images` | **writes** | **Private** | Captured leaf JPEGs, downloaded by the backend gatekeeper for ML re-verification |

**Object path format (FROZEN):** `{device_id}/{epoch_ms}.jpg` — bare object key, no bucket prefix, no leading slash.

**MIME restriction (FROZEN): `image/*`** — do NOT narrow to `image/jpeg`. The `supabase-kt` Storage client uploads `ByteArray` as `application/octet-stream`, which only the `image/*` wildcard accepts. Narrowing it silently breaks uploads with no error surfaced in logcat.

**Canonical app flow:**
1. After receiving `ScanResponse` from `/api/v1/scan`, upload the JPEG to `leaf-images/{device_id}/{epoch_ms}.jpg`.
2. Store the bare object key in `diagnostic_logs.image_storage_path`.
3. Backend skips re-verification when `image_storage_path` is null/empty — do not overwrite edge values.

**Current code** — upload is implemented and verified working in production (2026-06-03). After receiving `ScanResponse`, the captured JPEG is uploaded using the `storage-kt` module installed in `CottonAceApplication`. The bare object key is stored as `imageStoragePath` in `ScanHistoryEntity` and synced as `image_storage_path` in `diagnostic_logs`. Upload is non-fatal: failure is logged at WARN and `image_storage_path` is sent as `null`; the backend gatekeeper skips re-verification in that case.

---

## 3. Backend API (FastAPI inference server)

Client: `ApiClient` ([NetworkUtil.kt](app/src/main/java/com/example/network/NetworkUtil.kt)), Ktor `Android` engine, JSON with `ignoreUnknownKeys`/`isLenient`, `Logging` INFO. Cleartext allowed via `android:usesCleartextTraffic="true"`.

| Field | Value |
|---|---|
| **Base URL** | `BuildConfig.BACKEND_BASE_URL` (from `.env` via Secrets plugin — see §7) |

### 3.1 `POST /api/v1/scan` — used by the app
Called from `ScannerScreen` after capture ([MainActivity.kt:783](app/src/main/java/com/example/MainActivity.kt:783)).

**Request** — `multipart/form-data`:

| Part | Type | Required | Canonical notes |
|---|---|---|---|
| `file` | binary `image/jpeg` | YES | `filename="scan_{epochMillis}.jpg"` |
| `latitude` | float | NO | `null` if unavailable — **not `0.0`** (app omits field when null; `0.0` seen in backend logs is the backend's own default, not what the app sends) |
| `longitude` | float | NO | `null` if unavailable — **not `0.0`** |

**Success response (`200 OK`) — canonical shape:**
```jsonc
{
  "status": "success",
  "prediction": "Yellowish_Leaf",   // one of CLASSES, §8
  "confidence": 0.87,               // 0.0–1.0
  "confidence_score": 0.87,         // duplicate of confidence
  "pest_type": "Whitefly",          // "Whitefly" | "None"
  "whitefly_count": 12,             // ⚠️ HARDCODED STUB in backend — the §11 work item
  "action_protocol": "…",           // English guidance (== recommendation_en)
  "recommendation_en": "…",         // ⚠️ wording differs from §6 canonical (held; bundle with §11)
  "recommendation_ur": "…",
  "latitude": 30.157,               // echoed
  "longitude": 71.524
}
```
**Error response:** `{ "status": "error", "message": "<text>" }`

> `/api/v1/scan` does **not** write `diagnostic_logs`. The app performs the INSERT after receiving this response.

**Current code** — `ScanResponse` ([NetworkUtil.kt](app/src/main/java/com/example/network/NetworkUtil.kt)) deserializes all relevant fields with safe defaults:
```kotlin
@Serializable
data class ScanResponse(
    val status: String? = null,          // "success" | "error"
    val pest_type: String = "Unknown",
    val confidence: Float = 0f,          // 0.0–1.0
    val confidence_score: Float = 0f,    // duplicate of confidence (backend sends both)
    val whitefly_count: Int = 0,
    val recommendation_ur: String = "",
    val recommendation_en: String = ""
)
```
All fields have defaults — a missing field never crashes deserialization. `ignoreUnknownKeys = true` tolerates additional backend fields (e.g. `prediction`, echoed lat/lon) without throwing.

### 3.2 Backend endpoints NOT yet called by the app (defined cross-repo)
- `POST /api/v1/supabase-webhook` — Supabase→Backend only; app is not involved. Gatekeeper verified firing live 2026-06-03.
- `GET /api/v1/risk-metrics` — conforms. Returns:
  ```jsonc
  { "district": "MULTAN", "temperature": 37.0, "humidity": 42.0,
    "wind_speed": 14.0, "risk_level": "CRITICAL",
    "alert_text_en": "…", "alert_text_ur": "…" }
  ```
  Home screen still shows hardcoded values; wiring to this endpoint is 🟡 A4.
- `POST /api/v1/chat` — conforms (JSON body). Request: `{ "message": "…", "language": "ur" }` → `{ "reply": "…" }`. Expert screen is a static stub; wiring is 🟡 A4.

---

## 4. Risk Level Enum (canonical)

> All components use exactly these four values: **uppercase, no spaces.**

| Value | Meaning | Whitefly band |
|---|---|---|
| `LOW` | Healthy / below threshold | 0–4 |
| `MEDIUM` | Monitor; localized presence | 5–8 |
| `HIGH` | Action recommended | 9–15 |
| `CRITICAL` | Outbreak; immediate mitigation | 16+ |

Android rule: derive from `whitefly_count` using the bands above via `deriveRiskLevel()`:
```kotlin
fun deriveRiskLevel(whiteflyCount: Int): String = when {
    whiteflyCount >= 16 -> "CRITICAL"
    whiteflyCount >= 9  -> "HIGH"
    whiteflyCount >= 5  -> "MEDIUM"
    else                -> "LOW"
}
```
Called at save-time in `DiagnosisScreen` with `ScanResponse.whitefly_count`. All four values are emitted and verified in production (LOW, HIGH observed in `diagnostic_logs`).

> ⚠️ **Live consequence:** the backend currently hardcodes `whitefly_count = 12` in `/scan`. `deriveRiskLevel(12)` always returns `HIGH` for any pest and `LOW` for healthy. `MEDIUM` and `CRITICAL` cannot occur in practice until the backend ships real counting (§11 work item). The derivation logic is correct everywhere — it is starved of varied input.

### UI mapping — History badge ([MainActivity.kt](app/src/main/java/com/example/MainActivity.kt))
| `riskLevel` | Color |
|---|---|
| `CRITICAL` | `DangerRed` |
| `HIGH` | `DangerRed` |
| `MEDIUM` | `WarningAmber` |
| `LOW` | `SuccessGreen` |
| unknown | `TextSecondary` (neutral — never implies healthy) |

---

## 5. Language codes (canonical) & multilingual strings

| Language | **Code** | `AppLanguage` enum | Display |
|---|---|---|---|
| Urdu | `ur` | `URDU` (default) | اردو |
| Punjabi | `pa` | `PUNJABI` | پنجابی |
| Saraiki | `skr` | `SARAIKI` | سرائیکی |
| English | `en` | `ENGLISH` | EN |

> ✅ Use the **codes** (`ur/pa/skr/en`) on the wire: `farmers_profiles.preferred_language`, `/chat` `language`.
> ❌ Do not send full names (`"URDU"`).

The sync sends `"ur"` (fixed). Still hardcoded — does not yet reflect the user's live language selection (a local follow-up, §10 #6).

### String keys — `object LocalizationData` ([LocalizationData.kt](app/src/main/java/com/example/localization/LocalizationData.kt))
Each key is `Map<AppLanguage, String>` (or `…List<String>>`). Language is in-memory UI state (not persisted, not Android resource locales). RTL via `UrduTextStyle` + bundled `noto_nastaliq_urdu.ttf`.

| Key | Used where |
|---|---|
| `greetings` | Home welcome line |
| `criticalWhiteflyRiskTitle` / `criticalWhiteflyRiskDesc` | Home district-risk alert |
| `diagnosisTitles`, `confidenceMetrics`, `actionProtocols`, `scanCropMain` | Defined but **not currently referenced** in composables |

> Many UI strings are inlined directly in composables rather than centralized here; `res/values/strings.xml` holds only `app_name`. Missing-key reads fall back to `?: ""`.

---

## 6. Recommendation / Action protocol

Returned by `/api/v1/scan` as `recommendation_ur` / `recommendation_en`:

| Condition | `recommendation_ur` | `recommendation_en` |
|---|---|---|
| Whitefly detected | سفید مکھی کے تدارک کے لیے متعلقہ اسپرے صبح یا شام کے وقت کریں۔ | Apply targeted mitigation spray in morning or evening. |
| Healthy | کپاس کی فصل صحت مند ہے۔ کسی اسپرے کی ضرورت نہیں ہے۔ | Crop is healthy. No spray required. |

The Diagnosis screen renders `recommendation_ur` in the Action Protocol card.

---

## 7. Hardcoded values, secrets & env references

| Value | Location | Status |
|---|---|---|
| `BACKEND_BASE_URL` | `.env` → `BuildConfig.BACKEND_BASE_URL` | ✅ externalised; edit `.env` to rotate ngrok |
| `SUPABASE_URL` | `.env` → `BuildConfig.SUPABASE_URL` | ✅ externalised |
| `SUPABASE_ANON_KEY` | `.env` → `BuildConfig.SUPABASE_ANON_KEY` | ✅ externalised; JWT format required |
| Scan path `/api/v1/scan` | NetworkUtil.kt | constant — only changes if backend renames it |
| Device-ID salt `KapasKiSehat2026_SecureSalt` | DeviceIdentity.kt | hardcoded; changing it invalidates all stored device IDs |
| `district = "Multan Belt"` | MainActivity.kt | 🟡 hardcoded; future: derive from GPS reverse-geocode |
| `agricultural_belt = null` | DataSyncWorker.kt | 🟡 null placeholder; future: derive from district |
| `preferred_language = "ur"` | DataSyncWorker.kt | 🟡 correct code but hardcoded; doesn't track user's live selection |
| Room DB `cotton_ace.db` | CottonAceApplication.kt | constant |
| WorkManager name `CottonAceDataSync` | MainActivity.kt | `ExistingWorkPolicy.REPLACE` |
| `GEMINI_API_KEY` | .env.example | declared via Secrets plugin but unused by app code |

> ⚠️ `.env` lines must be `KEY=value` with no duplicated key prefix. A doubled `BACKEND_BASE_URL=BACKEND_BASE_URL=https://…` entry makes `BuildConfig.BACKEND_BASE_URL` equal to the literal string `BACKEND_BASE_URL=https://…` — a URL-parse crash. Verified and resolved 2026-06-03. After any `.env` edit, Gradle sync is required — Secrets values bake into `BuildConfig` at compile time.

---

## 8. ML model constants (reference)

**Class labels (`CLASSES`)** — exact `prediction` strings from the backend:
```
Fresh_Leaf                 → healthy,  pest_type = "None"
Leaf_Reddening             → disease,  pest_type = "Whitefly"
Leaf_Spot_Bacterial_Blight → disease,  pest_type = "Whitefly"
Yellowish_Leaf             → disease,  pest_type = "Whitefly"
```
- **Model version:** `Flee-v1.0.4-stb`
- **Confidence threshold:** `0.75` (backend gatekeeper re-verifies below this)
- **Confidence scale:** always `0.0–1.0`, never `0–100`
- **The model is a classifier** — it does NOT count whiteflies. `whitefly_count` in the `/scan` response is currently hardcoded to `12` by the backend (§11 work item). See §4 live consequence note.

---

## 9. Data models / classes defined in this repo

| Class | File | Role |
|---|---|---|
| `ScanResponse` | NetworkUtil.kt | `/api/v1/scan` response DTO (`@Serializable`) |
| `DiagnosticLogPayload` | DataSyncWorker.kt | `diagnostic_logs` row DTO |
| `ProfilePayload` | DataSyncWorker.kt | `farmers_profiles` row DTO |
| `ScanHistoryEntity` | database/ScanHistoryEntity.kt | Room entity `scan_history` (v2) |
| `ScanHistoryDao` | database/ScanHistoryDao.kt | Room DAO |
| `AppDatabase` | database/AppDatabase.kt | Room DB v2, `exportSchema=false`, `fallbackToDestructiveMigration` |
| `ScanSession` | MainActivity.kt | data carrier across Scanner→Diagnosis (replaces two loose ViewModel fields) |
| `SharedViewModel` | MainActivity.kt | holds `StateFlow<ScanSession?>` |
| `DeviceIdentity` (object) | DeviceIdentity.kt | SHA-256(ANDROID_ID + salt) — shared by Scanner + Worker |
| `CottonAceApplication` | CottonAceApplication.kt | holds `database` + `supabaseClient` (Postgrest + Storage) |
| `AppLanguage` (enum) | localization/LocalizationData.kt | UI language |
| `LocalizationData` (object) | localization/LocalizationData.kt | static string maps |
| `ApiClient` (object) | NetworkUtil.kt | Ktor backend client |
| `DataSyncWorker` | network/DataSyncWorker.kt | `CoroutineWorker` Supabase sync |

### `ScanHistoryEntity` (Room table `scan_history`, local-only, v2)
```kotlin
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val imagePath: String,          // local file path of captured JPEG
    val whiteflyCount: Int,         // real value from ScanResponse
    val riskLevel: String,          // derived via deriveRiskLevel(whiteflyCount)
    val district: String,           // still hardcoded "Multan Belt"
    val syncState: Int = 0,         // 0 = pending, 1 = synced
    val confidenceScore: Float = 0f,
    val inferenceTimeMs: Long = 0L,
    val imageStoragePath: String? = null,  // bare object key in leaf-images
    val latitude: Double? = null,
    val longitude: Double? = null
)
```
DAO: `insertScan` (REPLACE) · `getAllScans()` (`ORDER BY timestamp DESC`, Flow) · `getPendingSyncScans()` (`WHERE syncState = 0`, Flow) · `updateSyncStatus(id, status)`.

---

## 10. Open divergences & unhandled states (Android TODOs)

Code-vs-contract gaps, highest impact first.

Status key: 🟠/🟡 = open · ✅ = fixed.

| # | Sev | Issue |
|---|---|---|
| 1 | ✅ | ~~No Storage upload~~ → JPEG uploaded to `leaf-images/{device_id}/{epoch_ms}.jpg` after `/scan`. Path synced as `image_storage_path`. Non-fatal: gatekeeper skips if null. |
| 2 | ✅ | ~~`confidence_score` hardcoded 0.95f~~ → real `ScanResponse.confidence` stored and synced. |
| 3 | ✅ | ~~`whitefly_count` fabricated~~ → real `ScanResponse.whitefly_count` stored and synced. |
| 4 | ✅ | ~~`inference_time_ms` hardcoded 150~~ → measured round-trip around `/scan` call. |
| 5 | ✅ | ~~`DiagnosticLogPayload` missing columns~~ → `image_storage_path`, `latitude`, `longitude`, `agricultural_belt` present. `agricultural_belt` still `null` (🟡 future: derive from district). |
| 6 | ✅ | ~~`preferred_language` sends `"URDU"`~~ → sends `"ur"`. 🟡 Still hardcoded; doesn't reflect user's live selection. |
| 7 | ✅ | ~~`app_version` sends `"2.4"`~~ → `BuildConfig.VERSION_NAME` (`"1.0"`). |
| 8 | ✅ | ~~GPS defaults to `0.0/0.0`~~ → `Double?`, null when no fix; omitted from form entirely. |
| 9 | ✅ | ~~History badge mis-colors HIGH~~ → all 4 levels render correctly. ~~App only emits CRITICAL/MEDIUM~~ → `deriveRiskLevel(whiteflyCount)` emits `LOW/MEDIUM/HIGH/CRITICAL` per §4 bands. |
| 10 | ✅ | ~~`imagePath` mock path~~ → real captured file path via `ScanSession`. |
| 11 | ✅ | ~~`ScanResponse` non-nullable crash~~ → all fields have safe defaults; extended with `confidence_score`, `whitefly_count`, `recommendation_en`. |
| 12 | ✅ | ~~No HTTP status check / no timeout~~ → `status.isSuccess()` + `{status:"error"}` rejection + `HttpTimeout` 30s/15s. |
| 13 | ✅ | ~~Silent scan/capture failure~~ → bilingual Toast on both error paths. |
| 14 | ✅ | ~~`syncState=1` before confirming insert~~ → `updateSyncStatus` only reached after `insert` returns without throwing. |
| 15 | ✅ | ~~`farmers_profiles` upsert swallows exceptions~~ → logged at WARN with throwable. |
| 16 | ✅ | ~~`BASE_URL` hardcoded~~ → `BuildConfig.BACKEND_BASE_URL` via Secrets plugin; rotate ngrok via `.env` only. |
| 17 | ✅ | ~~Supabase URL/key hardcoded~~ → `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY` via `.env`. |
| 18 | 🟡 | Home weather + alert hardcoded; Expert chat is a stub. Wire to `/risk-metrics` + `/chat`. Both endpoints conform. *(A4 — next local work item after §11 lands)* |

**Next cross-repo work item (MASTER v4 §11): real `whitefly_count`**
The backend hardcodes `12` in `/scan`. The app faithfully syncs whatever value it receives, so no Android code changes are needed for Options A or B. Android guardrail: do not re-introduce any hardcoded count. If Option C is chosen (decouple risk from count), `deriveRiskLevel` must not be changed until §4 is rewritten in MASTER first.
MASTER v4 issue refs: W1 🔴 (backend primary), A1/A2/A3 🟡 (Android local follow-ups).

---

## 11. External-surface summary

```
┌─ Android app (this repo) ──────────────────────────────────────────────┐
│  Camera ─► POST http://<backend>/api/v1/scan                            │
│            (multipart: file[jpeg], latitude?, longitude?)               │
│              └─► ScanResponse{status, pest_type, confidence,            │
│                               whitefly_count, recommendation_ur/en, …}  │
│                                                                          │
│  Upload JPEG to Supabase Storage "leaf-images"                           │
│    → {device_id}/{epoch_ms}.jpg (bare object key, no bucket prefix)      │
│    → image_storage_path stored in ScanHistoryEntity + diagnostic_logs    │
│                                                                          │
│  Save ─► Room "scan_history" ─► WorkManager "CottonAceDataSync"         │
│                                    ├─► Supabase upsert farmers_profiles  │
│                                    └─► Supabase insert  diagnostic_logs  │
│                                          └─(webhook)─► backend gatekeeper│
└──────────────────────────────────────────────────────────────────────────┘
```
