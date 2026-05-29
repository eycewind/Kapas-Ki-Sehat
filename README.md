# CottonAce (کپاس کی صحت) 🌾📱

CottonAce is an AI-driven, offline-first mobile application designed to empower cotton farmers with real-time field diagnostics. The platform specializes in detecting early-stage whitefly infestations using computer vision, providing instantly localized risk analysis even in environments with zero cellular connectivity.

---

## 🚀 Key Features

* **Multi-Dialect Localization:** Comprehensive UI support natively switchable between English, Urdu (اردو), Punjabi (پنجابی), and Saraiki (سرائیکی) using custom calligraphy font rendering.
* **Offline-First Architecture:** Complete structural reliability deep in remote fields. Scan logs save instantly to local storage and sync in the background when connectivity becomes available.
* **Dynamic Risk Assessment:** Real-time localized classification (LOW, MEDIUM, CRITICAL) mapping out regional threat indicators.

---

## 🏗️ Technical Architecture

The application is built using a modern, reactive unidirectional data flow architecture on Android:

* **UI Layer:** Jetpack Compose (Declarative UI layout system with fluid layout states and dynamic font weight scaling).
* **Local Persistence:** Jetpack Room Database (SQLite abstraction layer managing local entities and transactional queries).
* **Data Stream:** Kotlin Coroutines & Asynchronous Flows (Real-time database observation automatically piping state updates down to UI views).
* **Backend Ecosystem (Target):** FastAPI Python server paired with a scalable, open-source Supabase PostgreSQL cloud backend.

### 📊 Data Pipeline Topology

                   ┌──────────────────────────────────┐
                   │       Jetpack Compose UI         │
                   └─────────────────▲────────────────┘
                                     │ (Live Flow Stream)
                   ┌─────────────────┴────────────────┐
                   │    Local Room Database Cache     │
                   └─────────────────▲────────────────┘
                                     │ (Async Insertion)
                   ┌─────────────────┴────────────────┐
                   │   Camera / ML Inference Engine   │
                   └─────────────────┬────────────────┘
                                     │ (Background Worker Sync)
                                     ▼
                   ┌──────────────────────────────────┐
                   │  Supabase Cloud PostgreSQL DB    │
                   └──────────────────────────────────┘

---

## 🗃️ Database Schema Primitives

The core persistence layer models the following relational parameters within the `scan_history` table:

* `id` (Long, Primary Key, Auto-Increment)
* `timestamp` (Long, Unix Epoch)
* `imagePath` (String, Local URI reference)
* `whiteflyCount` (Int, Absolute pest detection count)
* `riskLevel` (String: `LOW`, `MEDIUM`, `CRITICAL`)
* `district` (String, Regional identifier)
* `syncState` (Int: `0` = Pending Cloud Sync, `1` = Successfully Synced)

---

## 🛠️ Local Development Setup

### Prerequisites
* Android Studio Ladybug (or newer)
* Android SDK 34+
* Kotlin 1.9.x / KSP plugin configured

### Installation Steps
1. Clone the repository:
   ```bash
   git clone [https://github.com/your-repo/cotton-ace.git](https://github.com/your-repo/cotton-ace.git)