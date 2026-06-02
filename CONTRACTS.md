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
> **Reading convention:** Each shared surface lists the **Canonical contract** (what the
> app must conform to) and, where the current code differs, a **⚠️ Current code** note.
> Open divergences are collected in §10.
>
> **Generated:** 2026-06-01 · last reconciled with MASTER-CONTRACTS.md: 2026-06-01
> · gradle `versionName 1.0` · `applicationId` `com.aistudio.kapaskisehat.kzhfpx`
> · `namespace` `com.example`

---

## 1. Supabase

### Client configuration
Defined in [CottonAceApplication.kt](app/src/main/java/com/example/CottonAceApplication.kt):

| Setting | Value |
|---|---|
| `supabaseUrl` | `https://wmfqxrzoploggezfmnjn.supabase.co` |
| `supabaseKey` | `sb_publishable_flQOih4VRvMCs67leUY3Zg_GFeGcNf-` (publishable/anon key) |
| Installed modules | `Postgrest` only (no Auth/Storage/Realtime modules installed) |

> ⚠️ Both URL and key are hardcoded in source (committed to git). See §6 and §10.

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
| `district` | `varchar` | **NO** | App | Required |
| `whitefly_count` | `integer` | **NO** | App | Required — from ML response |
| `risk_level` | `varchar` | **NO** | App | Required — enum §4 |
| `confidence_score` | `numeric` | **NO** | App | Required — real `ScanResponse.confidence`, 0.0–1.0 |
| `inference_time_ms` | `integer` | **NO** | App | Required — measure actual duration |
| `image_storage_path` | `text` | YES | App | Path in `leaf-images` bucket (§2) |
| `latitude` | `double precision` | YES | App | GPS — `null` if unavailable, **not `0.0`** |
| `longitude` | `double precision` | YES | App | GPS — `null` if unavailable, **not `0.0`** |
| `agricultural_belt` | `varchar` | YES | App | e.g. `"Southern Punjab"` |
| `created_at` | `timestamptz` | YES | Supabase | Auto |

> ❌ There is **no `status`** column and **no `image_url`** column. Use `image_storage_path`.

**⚠️ Current code** — `DiagnosticLogPayload` ([DataSyncWorker.kt](app/src/main/java/com/example/network/DataSyncWorker.kt)) sends only:
```kotlin
@Serializable
data class DiagnosticLogPayload(
    val device_id: String, val district: String, val whitefly_count: Int,
    val risk_level: String, val confidence_score: Float,   // hardcoded 0.95f
    val timestamp: String, val inference_time_ms: Int       // hardcoded 150
)
```
Missing vs canonical: `image_storage_path`, `latitude`, `longitude`, `agricultural_belt`. And `confidence_score`/`inference_time_ms`/`whitefly_count` are fabricated, not real. → §10.

#### `farmers_profiles` — canonical schema

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `uuid` | NO | PK |
| `device_id` | `varchar` | **NO** | Unique device identity (SHA-256 hash) |
| `registered_at` | `timestamptz` | YES | |
| `last_active_at` | `timestamptz` | YES | |
| `app_version` | `varchar` | **NO** | gradle `versionName`, currently `"1.0"` |
| `preferred_language` | `varchar` | **NO** | language **code** §5 (`"ur"`, not `"URDU"`) |

**⚠️ Current code** — `ProfilePayload` sends `device_id`, `app_version = "2.4"` (wrong), `preferred_language = "URDU"` (wrong, should be `"ur"`); omits `registered_at`/`last_active_at` (nullable, acceptable). → §10.

#### Tables the app does NOT touch (owned elsewhere; listed for cross-repo awareness)
`model_deployments`, `harvested_images_pool`, `system_health_telemetry` — read/written by backend & dashboard. The Android app has no contract with these today. (`system_health_telemetry.log_level` ∈ `INFO|WARN|ERROR` if the app ever reports telemetry.)

---

## 2. Supabase Storage

| Bucket | App role | Purpose |
|---|---|---|
| `leaf-images` | **writes** | Captured leaf JPEGs, downloaded by the backend gatekeeper for ML re-verification |

**Canonical app flow:**
1. After receiving `ScanResponse` from `/api/v1/scan`, upload the JPEG to
   `leaf-images/{device_id}/{epoch_ms}.jpg`.
2. Store the returned path in `diagnostic_logs.image_storage_path`.
3. Backend skips re-verification when `image_storage_path` is null/empty.

**⚠️ Current code** — the app **never uploads to Storage** (no Storage module installed). `image_storage_path` is always absent, so the backend gatekeeper never runs. → §10 (#1, highest priority).

---

## 3. Backend API (FastAPI inference server)

Client: `ApiClient` ([NetworkUtil.kt](app/src/main/java/com/example/network/NetworkUtil.kt)), Ktor `Android` engine, JSON with `ignoreUnknownKeys`/`isLenient`, `Logging` INFO. Cleartext allowed via `android:usesCleartextTraffic="true"`.

| Field | Value |
|---|---|
| **Base URL** | `http://192.168.18.11:8000` ⚠️ hardcoded LAN IP — see §7 |

### 3.1 `POST /api/v1/scan` — used by the app
Called from `ScannerScreen` after capture ([MainActivity.kt:783](app/src/main/java/com/example/MainActivity.kt:783)).

**Request** — `multipart/form-data`:

| Part | Type | Required | Canonical notes |
|---|---|---|---|
| `file` | binary `image/jpeg` | YES | `filename="scan_{epochMillis}.jpg"` |
| `latitude` | float | NO | `null` if unavailable — **not `0.0`** |
| `longitude` | float | NO | `null` if unavailable — **not `0.0`** |

**Success response (`200 OK`) — canonical shape:**
```jsonc
{
  "status": "success",
  "prediction": "Yellowish_Leaf",   // one of CLASSES, §8
  "confidence": 0.87,               // 0.0–1.0
  "confidence_score": 0.87,         // duplicate of confidence
  "pest_type": "Whitefly",          // "Whitefly" | "None"
  "whitefly_count": 12,             // ⚠️ backend currently hardcodes 12
  "action_protocol": "…",           // English guidance
  "recommendation_en": "…",         // English (== action_protocol)
  "recommendation_ur": "…",         // Urdu guidance
  "latitude": 30.157,               // echoed
  "longitude": 71.524
}
```
**Error response:** `{ "status": "error", "message": "<text>" }`

> `/api/v1/scan` does **not** write `diagnostic_logs`. The app performs the INSERT after receiving this response.

**⚠️ Current code** — `ScanResponse` deserializes only 3 fields:
```kotlin
@Serializable
data class ScanResponse(val pest_type: String, val confidence: Float, val recommendation_ur: String)
```
It drops `whitefly_count`, `confidence_score`, `prediction`, `recommendation_en`, `status`, echoed lat/lon — so the real `whitefly_count`/`confidence` can't flow into the DB insert. `ignoreUnknownKeys=true` tolerates the extra fields, but **missing required fields still throw** (§10). → should be extended to carry at least `whitefly_count` and `status`.

### 3.2 Backend endpoints NOT yet called by the app (defined cross-repo)
- `POST /api/v1/supabase-webhook` — Supabase→Backend only; app is not involved.
- `GET /api/v1/risk-metrics` — returns `{ temperature, humidity, wind_speed, risk_level, alert_text_en, alert_text_ur }`. The Home screen currently shows **hardcoded** weather (37°C / 42% / 14 km/h) and a hardcoded alert; wiring it to this endpoint is a future task.
- `POST /api/v1/chat` — `{ message, language }` → `{ reply }`. `language` uses §5 codes. The Expert screen is currently a static placeholder.

---

## 4. Risk Level Enum (canonical)

> All components use exactly these four values: **uppercase, no spaces.**

| Value | Meaning | Whitefly band |
|---|---|---|
| `LOW` | Healthy / below threshold | 0–4 |
| `MEDIUM` | Monitor; localized presence | 5–8 |
| `HIGH` | Action recommended | 9–15 |
| `CRITICAL` | Outbreak; immediate mitigation | 16+ |

Android rule: derive from `ScanResponse.confidence` **and** `whitefly_count`.

### UI mapping — History badge ([MainActivity.kt:1118](app/src/main/java/com/example/MainActivity.kt:1118))
| `riskLevel` | Color |
|---|---|
| `CRITICAL` | `DangerRed` |
| `MEDIUM` | `WarningAmber` |
| anything else (incl. `LOW`, `HIGH`) | `SuccessGreen` |

**⚠️ Current code** — only ever produces two values: `confidence > 0.8f ? "CRITICAL" : "MEDIUM"` ([MainActivity.kt:970](app/src/main/java/com/example/MainActivity.kt:970)). `LOW`/`HIGH` are never emitted, and `HIGH` would mis-render green in the badge. → §10.

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

**⚠️ Current code** — `AppLanguage` enum uses full names internally (fine for UI state) but the sync sends the literal `"URDU"` to `preferred_language` (wrong) and hardcodes it regardless of the user's selection. → §10.

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

| Value | Location | Notes |
|---|---|---|
| Backend base URL `http://192.168.18.11:8000` | NetworkUtil.kt:24 | LAN IP; `const val` → recompile to change. If reached via ngrok, this is stale every session. **Move to `BuildConfig`/`local.properties`** (`buildConfig=true` already on). |
| Scan path `/api/v1/scan` | NetworkUtil.kt:40 | |
| Supabase URL + publishable key | CottonAceApplication.kt:21-22 | committed to git; rotate + move to Secrets plugin |
| Device-ID salt `KapasKiSehat2026_SecureSalt` | DataSyncWorker.kt:55 | SHA-256(ANDROID_ID + salt) |
| `app_version = "2.4"` | DataSyncWorker.kt:67 | should be gradle `versionName` `"1.0"` |
| `preferred_language = "URDU"` | DataSyncWorker.kt:69 | should be `"ur"`; also hardcoded |
| `confidence_score = 0.95f` | DataSyncWorker.kt:78 | placeholder; use real value |
| `inference_time_ms = 150` | DataSyncWorker.kt:80 | placeholder |
| `imagePath = "/storage/.../mock_leaf.jpg"` | MainActivity.kt:968 | mock path |
| `whiteflyCount` `15`/`(5..45).random()` | MainActivity.kt:969 | fabricated |
| `district = "Multan Belt"` | MainActivity.kt:971 | hardcoded |
| Room DB `cotton_ace.db` | CottonAceApplication.kt:16 | |
| WorkManager unique name `CottonAceDataSync` | MainActivity.kt:980 | `ExistingWorkPolicy.REPLACE` |
| `GEMINI_API_KEY` | .env.example | declared via Secrets plugin but **unused** by app code |

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

---

## 9. Data models / classes defined in this repo

| Class | File | Role |
|---|---|---|
| `ScanResponse` | NetworkUtil.kt | `/api/v1/scan` response DTO (`@Serializable`) |
| `DiagnosticLogPayload` | DataSyncWorker.kt | `diagnostic_logs` row DTO |
| `ProfilePayload` | DataSyncWorker.kt | `farmers_profiles` row DTO |
| `ScanHistoryEntity` | database/ScanHistoryEntity.kt | Room entity `scan_history` |
| `ScanHistoryDao` | database/ScanHistoryDao.kt | Room DAO |
| `AppDatabase` | database/AppDatabase.kt | Room DB v1, `exportSchema=false` |
| `SharedViewModel` | MainActivity.kt | `StateFlow<ScanResponse?>` across Scanner→Diagnosis |
| `CottonAceApplication` | CottonAceApplication.kt | holds `database` + `supabaseClient` |
| `AppLanguage` (enum) | localization/LocalizationData.kt | UI language |
| `LocalizationData` (object) | localization/LocalizationData.kt | static string maps |
| `ApiClient` (object) | NetworkUtil.kt | Ktor backend client |
| `DataSyncWorker` | network/DataSyncWorker.kt | `CoroutineWorker` Supabase sync |

### `ScanHistoryEntity` (Room table `scan_history`, local-only)
```kotlin
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, val imagePath: String, val whiteflyCount: Int,
    val riskLevel: String, val district: String,
    val syncState: Int = 0   // 0 = pending sync, 1 = synced
)
```
DAO: `insertScan` (REPLACE) · `getAllScans()` (`ORDER BY timestamp DESC`, Flow) · `getPendingSyncScans()` (`WHERE syncState = 0`, Flow) · `updateSyncStatus(id, status)`.

---

## 10. Open divergences & unhandled states (Android TODOs)

Code-vs-contract gaps, highest impact first.

Status key: 🔴/🟠/🟡 = open · ✅ = fixed.

Status key: 🔴/🟠/🟡 = open · ◑ = partially done · ✅ = fixed.

| # | Sev | Issue |
|---|---|---|
| 1 | ✅ | ~~No Storage upload~~ — after `/scan`, JPEG is uploaded to `leaf-images/{device_id}/{epoch_ms}.jpg`. Path stored as `imageStoragePath` in `ScanHistoryEntity` → synced as `image_storage_path` in `diagnostic_logs`. Non-fatal: gatekeeper skips if null. |
| 2 | ✅ | ~~`confidence_score` hardcoded~~ — real `ScanResponse.confidence` flows into `ScanHistoryEntity.confidenceScore` → `DiagnosticLogPayload.confidence_score`. |
| 3 | ✅ | ~~`whitefly_count` fabricated~~ — real `ScanResponse.whitefly_count` stored and synced. |
| 4 | ✅ | ~~`inference_time_ms` hardcoded~~ — measured round-trip around `/api/v1/scan` call (includes network; acceptable per MASTER §11). |
| 5 | ✅ | ~~`DiagnosticLogPayload` missing columns~~ — `image_storage_path`, `latitude`, `longitude`, `agricultural_belt` all present. `agricultural_belt` still `null` (derive from district — future pass). |
| 6 | ✅ | ~~`preferred_language` sends `"URDU"`~~ → sends `"ur"`. (Hardcoded for now; live-selection persistence is a local follow-up.) |
| 7 | ✅ | ~~`app_version` sends `"2.4"`~~ → `BuildConfig.VERSION_NAME` (`"1.0"`). |
| 8 | ✅ | ~~GPS defaults to `0.0/0.0`~~ → `lat`/`lon` are `Double?`, null when no fix. Omitted from the multipart form entirely (not sent as `0.0`). |
| 9 | ◑ | History badge colors all 4 risk levels correctly. App still only *emits* `CRITICAL`/`MEDIUM` — deriving `LOW`/`HIGH` from real `whitefly_count` is Phase 3. |
| 10 | ✅ | ~~`imagePath` mock path~~ → real captured file path via `SharedViewModel`. |
| 11 | ✅ | ~~`ScanResponse` non-nullable crash~~ → all fields have safe defaults. DTO extended with `confidence_score`, `whitefly_count`, `recommendation_en`. |
| 12 | ✅ | ~~No HTTP status check / no timeout~~ → `status.isSuccess()` check + `{status:"error"}` envelope rejection + `HttpTimeout` (30s/15s). |
| 13 | ✅ | ~~Silent upload/capture failure~~ → bilingual Toast on both error paths. |
| 14 | ✅ | ~~`syncState=1` marked without confirming insert~~ → ordering is now explicit with logging; `updateSyncStatus` is only reached if `insert` returned without throwing. |
| 15 | ✅ | ~~`farmers_profiles` upsert swallows exceptions~~ → logged at WARN with throwable. |
| 16 | ✅ | ~~`BASE_URL` hardcoded in Kotlin source~~ → `BuildConfig.BACKEND_BASE_URL` via Secrets plugin. Rotate ngrok by editing `.env` + Gradle sync only. |
| 17 | 🟡 | Home weather + alert hardcoded; Expert chat is a stub. Wire to `/risk-metrics` + `/chat`. *(cross-repo — after Phase 3)* |
| 18 | 🟡 | Supabase URL/key committed to source — rotate and move to Secrets plugin. *(rotation is cross-repo)* |

---

## 11. External-surface summary

```
┌─ Android app (this repo) ──────────────────────────────────────────────┐
│  Camera ─► POST http://<backend>/api/v1/scan                            │
│            (multipart: file[jpeg], latitude?, longitude?)               │
│              └─► ScanResponse{status, pest_type, confidence,            │
│                               whitefly_count, recommendation_ur/en, …}  │
│                                                                          │
│  [canonical] ─► upload JPEG to Supabase Storage "leaf-images"           │
│                  └─► image_storage_path                                  │
│                                                                          │
│  Save ─► Room "scan_history" ─► WorkManager "CottonAceDataSync"         │
│                                    ├─► Supabase upsert farmers_profiles  │
│                                    └─► Supabase insert  diagnostic_logs  │
│                                          └─(webhook)─► backend gatekeeper│
└──────────────────────────────────────────────────────────────────────────┘
```
