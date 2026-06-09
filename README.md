<h1 align="center">TIP — Tap Into Prosperity</h1>

<p align="center">
  A personal finance tracker for Android with offline-first architecture and real-time cloud sync.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Java-ED8B00?style=flat-square&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Backend-Spring%20Boot%204-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Database-PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white" />
  <img src="https://img.shields.io/badge/Min%20SDK-API%2026-blue?style=flat-square" />
</p>

---

## What is TIP?

TIP is an Android app for managing personal finances in Vietnamese Dong (₫). It supports transactions, budgets, savings goals, debts, and multi-wallet management — all available offline, synced to the cloud in the background.

**Core principle:** Every write hits the local Room database first. The UI always reacts to local data via LiveData. A WorkManager job pushes changes to the server whenever a connection is available.

---

## Architecture

```
┌──────────────────────────────────────────────────┐
│                Android App  (Java)                │
│                                                   │
│   Fragment  ──▶  ViewModel  ──▶  Repository       │
│   (Material 3)   (LiveData)    (Offline-first)    │
│                                                   │
│         Room SQLite              Retrofit 2       │
│         (local cache)            (OkHttp 4)       │
└────────────────────────┬─────────────────────────┘
                         │  HTTPS · JWT Bearer
                         ▼
┌──────────────────────────────────────────────────┐
│          Spring Boot 4 · Java 21  (REST)          │
│   Controller  ──▶  Service  ──▶  JPA Repository   │
│          Spring Security · BCrypt · JWT           │
└────────────────────────┬─────────────────────────┘
                         │  JDBC
                         ▼
┌──────────────────────────────────────────────────┐
│             Neon PostgreSQL  (cloud)              │
│  users · accounts · categories · transactions    │
│  budgets · goals · debts                         │
└──────────────────────────────────────────────────┘
```

---

## Tech Stack

### Android

| Area               | Library / Technology                                 |
| ------------------ | ---------------------------------------------------- |
| Language           | Java                                                 |
| Local database     | Room (SQLite) · schema v18, 11-step migration path   |
| Networking         | Retrofit 2 · OkHttp 4 · Gson                         |
| Reactive UI        | LiveData · ViewModel (Jetpack)                       |
| Navigation         | Navigation Component · Bottom Navigation             |
| UI toolkit         | Material Design 3 · RecyclerView · BottomSheet       |
| Background sync    | WorkManager                                          |
| Camera & OCR       | CameraX · ML Kit (receipt scanning)                  |
| Charts             | MPAndroidChart (BarChart, PieChart)                  |
| Home-screen widget | AppWidgetProvider                                    |
| Secure storage     | EncryptedSharedPreferences (AES256-GCM + AES256-SIV) |

### Backend

| Area           | Library / Technology                                |
| -------------- | --------------------------------------------------- |
| Framework      | Spring Boot 4.0                                     |
| Language       | Java 21                                             |
| ORM            | Spring Data JPA · Hibernate                         |
| Auth           | Spring Security · JWT (jjwt 0.12.6) · BCrypt        |
| Database       | Neon PostgreSQL (serverless cloud Postgres)         |
| AI integration | Google Gemini API (financial insights, invoice OCR) |
| Build          | Gradle (Kotlin DSL) · Lombok                        |

---

## Engineering Highlights

### Offline-First & Background Sync

All user actions write to Room immediately. A `WorkManager` job syncs dirty records with the server respecting foreign-key order:

```
Push unsynced records → Pull categories → accounts → budgets → goals → transactions
```

The app remains fully functional with no network connection.

### Cursor-Based Delta Sync

Each entity exposes a `/delta` endpoint. The client stores a server-issued `syncTimestamp` cursor and sends it as `updatedSince` on the next request — only changed records are transferred. Cursor pagination (`lastUpdatedAt + lastId`) prevents drift across pages.

### Last-Write-Wins Conflict Resolution

Every entity carries `updatedAtMs`. On pull, the server version wins only if its timestamp is newer; otherwise the local version is kept and re-pushed in the next sync cycle.

### Multi-Account Data Isolation

User data is isolated across four layers:

| Layer              | Mechanism                                                                            |
| ------------------ | ------------------------------------------------------------------------------------ |
| **Schema**         | `user_id` column on every entity; all DAO queries filter by `userId`                 |
| **Write**          | `userId` is stamped before every `insert()` — both from user actions and server sync |
| **Async guard**    | In-flight responses are discarded if `userId` has changed since the request was sent |
| **Account switch** | Cancel all HTTP requests → cancel WorkManager job → wipe Room → reset sync cursors   |

### Secure Token Management

- Token stored in `EncryptedSharedPreferences` backed by Android Keystore.
- `TokenManager` singleton caches credentials in memory so background threads never touch the Keystore directly.
- An OkHttp interceptor injects `Authorization: Bearer` on every request automatically.
- Only HTTP **401** triggers logout; HTTP **403** is treated as a normal authorization error.

### On-Device AI Insight Engine

Statistical analysis runs entirely offline before optionally calling the Gemini backend:

| Module             | Method                                  |
| ------------------ | --------------------------------------- |
| `BudgetForecaster` | OLS regression — spending forecast      |
| `AnomalyDetector`  | Z-score — unusual transaction detection |
| `PatternAnalyzer`  | EWMA — day-of-week spending trends      |
| `GoalAdvisor`      | Savings velocity recommendations        |
| `DebtLoanAdvisor`  | Upcoming due-date reminders             |

---

## Getting Started

### Prerequisites

- Android Studio (latest stable)
- Java 21 JDK
- A [Neon](https://neon.tech) PostgreSQL database (free tier works)

### 1 · Run the backend

```bash
cd backend
# Fill in your Neon connection string:
# backend/src/main/resources/application.properties
./gradlew bootRun
```

### 2 · Run the Android app

1. Open the project root in Android Studio.
2. Set `BASE_URL` in [`RetrofitClient.java`](app/src/main/java/vn/edu/usth/tip/network/RetrofitClient.java) to your backend URL.
3. Build and run on an emulator or physical device (API 26+).

---

## Project Info

|              |                                                      |
| ------------ | ---------------------------------------------------- |
| Package      | `vn.edu.usth.tip`                                    |
| Organization | USTH — University of Science and Technology of Hanoi |
| App language | Vietnamese                                           |
| Currency     | VND (₫)                                              |
