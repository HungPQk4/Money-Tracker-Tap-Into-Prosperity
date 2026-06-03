# 💰 TIP — Tap Into Prosperity (Money Tracker)

> Ứng dụng quản lý tài chính cá nhân trên Android với đồng bộ dữ liệu real-time lên PostgreSQL (Neon) thông qua Spring Boot REST API.

---

## 📋 Mục lục

- [Tổng quan kiến trúc](#-tổng-quan-kiến-trúc)
- [Tech Stack](#-tech-stack)
- [Cấu trúc dự án](#-cấu-trúc-dự-án)
- [Data Models](#-data-models)
- [Luồng đồng bộ dữ liệu](#-luồng-đồng-bộ-dữ-liệu)
- [Authentication & Security](#-authentication--security)
- [Multi-Account Data Isolation](#-multi-account-data-isolation)
- [Financial Engine](#-financial-engine)
- [UI & Navigation](#-ui--navigation)
- [API Endpoints](#-api-endpoints)
- [Gotchas & Known Issues](#-gotchas--known-issues)
- [Hướng phát triển](#-hướng-phát-triển)

---

## 🏗 Tổng quan kiến trúc

```
┌──────────────────────────────────────────────────────┐
│                  Android App (Java)                  │
│  ┌────────┐  ┌────────────┐  ┌────────────────────┐ │
│  │   UI   │──│ ViewModels │──│   Repositories     │ │
│  │Fragments│  │ LiveData   │  │ (Offline-first)    │ │
│  └────────┘  └────────────┘  └─────┬──────────────┘ │
│                                    │                 │
│              ┌─────────────────────┼───────────┐     │
│              │    Room SQLite      │ Retrofit  │     │
│              │   (Local Cache)     │ (Network) │     │
│              └─────────────────────┴───────────┘     │
└────────────────────────────┬─────────────────────────┘
                             │ HTTP (JWT Bearer)
                             ▼
┌──────────────────────────────────────────────────────┐
│            Spring Boot 4.0 Backend (Java 21)         │
│  Controllers → Services → JPA Repositories           │
│  Security: JWT (jjwt 0.12) + BCrypt                  │
└────────────────────────────┬─────────────────────────┘
                             │ JDBC
                             ▼
┌──────────────────────────────────────────────────────┐
│              Neon PostgreSQL (Cloud)                  │
│  Tables: users, accounts, categories, transactions,  │
│          budgets, goals, debts                        │
└──────────────────────────────────────────────────────┘
```

**Nguyên tắc cốt lõi: Offline-First**
- Mọi thao tác CRUD ghi vào Room trước → sync lên server sau.
- UI luôn reactive qua LiveData từ Room.
- Nếu server không khả dụng, app vẫn hoạt động bình thường.

---

## 🛠 Tech Stack

| Layer | Công nghệ |
|-------|-----------|
| **Android** | Java, Room Database, Retrofit 2, OkHttp, Gson, LiveData, ViewModel, Navigation Component, Material Design 3, WorkManager |
| **Backend** | Spring Boot 4.0.5, Spring Security, Spring Data JPA, Hibernate, Lombok |
| **Database** | Neon PostgreSQL (cloud), Room SQLite (local) |
| **Auth** | JWT (jjwt 0.12.6), BCrypt, EncryptedSharedPreferences |
| **Build** | Gradle (Kotlin DSL), Java 21 |

---

## 📁 Cấu trúc dự án

```
Money-Tracker-Tap-Into-Prosperity/
├── app/                          # Android Application
│   └── src/main/java/vn/edu/usth/tip/
│       ├── AppDatabase.java      # Room DB config, migrations (v17), seed data
│       ├── models/               # Room Entities + DAOs
│       │   ├── Transaction.java  # Giao dịch (EXPENSE/INCOME/TRANSFER) + userId
│       │   ├── Category.java     # Danh mục (expense/income) + userId
│       │   ├── Wallet.java       # Ví (CASH/BANK/EWALLET/INVESTMENT) + userId
│       │   ├── Budget.java       # Ngân sách theo kỳ + userId
│       │   ├── Goal.java         # Mục tiêu tiết kiệm + userId
│       │   ├── DebtLoan.java     # Nợ/Cho vay + userId
│       │   └── *Dao.java         # DAOs — tất cả queries filter by user_id
│       ├── network/              # Retrofit API layer
│       │   ├── RetrofitClient.java    # Shared Dispatcher, cancelAllRequests(), JWT interceptor
│       │   ├── AuthApi.java           # Login/Register (public)
│       │   ├── TransactionApi.java    # CRUD + sync + recent
│       │   ├── FinancialApi.java      # Accounts, Categories, Budgets, Goals, Debts
│       │   ├── requests/             # Request DTOs
│       │   └── responses/            # Response DTOs
│       ├── repositories/         # Data sync logic (offline-first)
│       │   ├── TransactionRepository.java  # Push/Pull/CRUD sync (phức tạp nhất)
│       │   ├── WalletsRepository.java      # requestUserId guard trong sync()
│       │   ├── CategoriesRepository.java   # requestUserId guard trong sync()
│       │   ├── BudgetsRepository.java      # deleteAllForUser + requestUserId guard
│       │   ├── GoalsRepository.java        # requestUserId guard trong sync()
│       │   └── DebtsRepository.java        # requestUserId guard trong sync()
│       ├── viewmodels/           # MVVM ViewModels
│       │   ├── AppViewModel.java          # Financial Engine — queries filtered by userId
│       │   ├── NewTransactionViewModel.java
│       │   ├── LoginViewModel.java        # Singleton TokenManager
│       │   ├── AccountViewModel.java
│       │   └── DashboardViewModel.java
│       ├── workers/
│       │   └── TransactionSyncWorker.java # Background sync (WorkManager)
│       ├── adapters/             # RecyclerView Adapters
│       ├── insights/             # AI Insight Engine
│       │   └── engine/           # BudgetForecaster, AnomalyDetector, PatternAnalyzer
│       ├── ui/
│       │   ├── activities/       # Splash (init TokenManager), Login, Signup, Main
│       │   └── fragments/        # Dashboard, Wallets (logout btn), Transactions, Goals...
│       └── utils/
│           ├── TokenManager.java # EncryptedSharedPreferences JWT, singleton getOrCreate()
│           ├── SessionManager.java # Global session expiry (401 redirect)
│           └── SyncPrefs.java    # Server-cursor storage cho delta sync
│
├── backend/                      # Spring Boot REST API
│   └── src/main/java/vn/edu/usth/tip/backend/
│       ├── models/               # JPA Entities
│       ├── controllers/          # REST Controllers
│       ├── services/             # Business logic
│       ├── repositories/         # Spring Data JPA
│       ├── dto/                  # Request/Response DTOs
│       ├── security/             # JWT filter, config
│       └── exception/            # Global exception handler
```

---

## 📊 Data Models

### Mapping Android ↔ Backend

| Android (Room) | Backend (JPA) | Neon Table |
|----------------|---------------|------------|
| `Wallet` | `Account` | `accounts` |
| `Transaction` | `Transaction` | `transactions` |
| `Category` | `Category` | `categories` |
| `Budget` | `Budget` | `budgets` |
| `Goal` | `Goal` | `goals` |
| `DebtLoan` | `Debt` | `debts` |

### Enum Mapping (QUAN TRỌNG)

Android sử dụng UPPERCASE enum, Backend sử dụng lowercase. Cần convert khi sync:

| Android Enum | Backend/Neon Enum |
|-------------|-------------------|
| `Wallet.Type.CASH` | `cash` |
| `Wallet.Type.BANK` | `bank` |
| `Wallet.Type.EWALLET` | `e_wallet` ⚠️ |
| `Wallet.Type.INVESTMENT` | `investment` |
| `Transaction.Type.EXPENSE` | `expense` |
| `Transaction.Type.INCOME` | `income` |
| `Transaction.Type.TRANSFER` | `transfer` |

> ⚠️ **EWALLET → e_wallet**: Room lưu `"EWALLET"`, Neon cần `"e_wallet"`. Chuyển đổi tại `WalletsRepository.mapTypeToNeon()` và `WalletTypeConverter`.

### Room Database Version: **18**

Migration path: `7→8→9→10→11→12→13→14→15→16→17→18`

| Migration | Thay đổi |
|-----------|---------|
| 7→8 | Thêm `spentAmount` vào `budgets` |
| 8→9 | Xóa data categories/wallets cũ |
| 9→10 | ALTER TABLE thêm `color_hex`, `type`, `is_system` (categories); `balanceVnd`, `color`, `type`, `includedInTotal` (wallets) + re-seed |
| 11→12 | Thêm `createdMs` vào `goals` |
| 12→13 | Thêm `updatedAtMs` + `isDeleted` vào `transactions` (LWW + soft delete) |
| 13→14 | Thêm `isRecurring` + `recurInterval` vào `transactions` |
| 14→15 | Fix icon cho danh mục "Hóa đơn" và "Gia đình" |
| 15→16 | Thêm `user_id` vào `categories` |
| 16→17 | Thêm `user_id` vào `transactions`, `wallets`, `budgets`, `goals`, `debt_loans` |
| **17→18** | **Dọn các dòng mồ côi `user_id IS NULL` ở 5 bảng trên (data cũ trước migration) — sync kéo lại từ server. Không đụng `categories` (system categories hợp lệ có NULL)** |

Có `fallbackToDestructiveMigration()` — nếu mất migration path sẽ xóa toàn bộ DB và tạo lại.

---

## 🔄 Luồng đồng bộ dữ liệu

### 1. Multi-device Delta Sync (TransactionSyncWorker) ✅

```
TransactionSyncWorker.doWork()
  │
  ├─ Phase 1 — txRepo.fullSyncPhase1()
  │   ├─ pushUnsyncedBatchSync()    ← PUSH giao dịch offline lên server
  │   ├─ refreshCategoriesSync()    ← PULL delta categories (cursor-based)
  │   └─ refreshAccountsSync()      ← PULL delta accounts/wallets + stamp userId
  │
  ├─ budgetRepo.syncDeltaBlocking() ← PULL delta budgets + strict userId validation
  ├─ goalRepo.syncDeltaBlocking()   ← PULL delta goals + stamp userId
  └─ txRepo.pullDeltaTransactionsSync() ← PULL delta transactions (cuối)
```

**Thiết kế delta sync:**
- **Server là nguồn thời gian duy nhất.** Server trả về `syncTimestamp`; client lưu vào `SyncPrefs` và dùng làm `updatedSince` cho phiên sau.
- **Cursor-based pagination**: `lastUpdatedAt + lastId` làm cursor. `untilTimestamp` đóng băng tập dữ liệu từ trang đầu để tránh pagination drift.
- **Soft delete toàn bộ entity**: Mọi entity có `deletedAt`. Khi xóa: set cả `deletedAt = now` VÀ `updatedAt = now`.
- **LWW conflict resolution**: So sánh `serverUpdatedAtMs` với `local.updatedAtMs`. Server mới hơn → ghi đè; local mới hơn → giữ, đẩy lên ở PUSH phase sau.
- **`db.runInTransaction()`** bọc toàn bộ write của mỗi trang — atomic.
- **Thứ tự PULL theo FK dependency**: categories → accounts → budgets → goals → transactions.

### 2. Repository Async Sync — Stale Response Guard

Tất cả các `sync()` method dùng `enqueue()` (async Retrofit) đều áp dụng pattern:

```java
// Capture userId TẠI THỜI ĐIỂM GỬI REQUEST
final String requestUserId = tokenManager.getUserId();

financialApi.getAllBudgets().enqueue(new Callback<>() {
    public void onResponse(...) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Guard: bỏ qua response cũ nếu user đã đổi tài khoản
            if (!requestUserId.equals(tokenManager.getUserId())) return;
            ...
        });
    }
});
```

Điều này ngăn race condition: response của user A không thể ghi dữ liệu vào DB của user B kể cả khi về muộn sau khi B đã đăng nhập.

### 3. Transaction Sync (real-time, từng giao dịch)

```
addTransactionOnline() / updateTransactionOnline()
  ├─ Ghi vào Room ngay (isSynced=true, userId stamped)
  ├─ resolveAccountId() / resolveCategoryId()
  └─ POST /api/transactions hoặc PUT /api/transactions/{id}
     └─ onSuccess: replace Room record với server version (UUID thật, userId preserved)
     └─ onFailure:  revertToUnsynced() → isSynced=false → Worker sẽ push sau
```

### 4. Optimistic UI

```
Optimistic Balance = Server Balance + Σ(unsyncedIncome) - Σ(unsyncedExpense)
```

### 5. Self-Healing Categories

`AppViewModel` observe danh sách categories. Nếu trống → tự động seed lại nút "Thêm" mặc định.

---

## 🔐 Authentication & Security

### Flow đăng nhập

```
SplashActivity → TokenManager.getOrCreate()  ← khởi tạo singleton trên Main Thread
     │
     └─ LoginActivity → POST /api/auth/login → JWT token
           │
           ├─ TokenManager.getOrCreate().saveAuthData(token, fullName, userId)
           ├─ clearAllTables() nếu khác user (hoặc noCredentials)
           └─ Navigate to MainActivity
```

### TokenManager — Singleton, EncryptedSharedPreferences

- **Storage**: `EncryptedSharedPreferences` ("AuthPrefs") — mã hóa `jwt_token`, `user_full_name`, `user_id` bằng AES256-GCM + AES256-SIV
- **Singleton pattern**: `TokenManager.getOrCreate(context)` — double-checked locking, `volatile`. Phải tạo trên Main Thread (lần đầu, trong `SplashActivity`) vì EncryptedSharedPreferences truy cập Android Keystore.
- **In-memory cache**: `cachedToken`, `cachedUserId`, `cachedFullName` — đọc từ disk một lần, sau đó serve từ cache. Background threads (OkHttp interceptor, Worker) đọc cache, không truy cập Keystore.
- **Fallback**: Nếu EncryptedSharedPreferences bị corrupt → xóa file + tạo lại. Nếu Keystore không khả dụng → fallback sang plain `SharedPreferences`.

### JWT Injection & 401 Handling

- `RetrofitClient` interceptor tự thêm `Authorization: Bearer <token>` vào mọi request
- **Chỉ HTTP 401** kích hoạt session expiry — HTTP 403 là lỗi phân quyền hợp lệ, không phải token hết hạn
- Khi 401: `TokenManager.clear()` → `SessionManager.triggerSessionExpired()`
- `MainActivity.setupSessionExpiry()` observe event → `clearAllTables()` + navigate về Login

### Logout Flow

```
Logout button (WalletManagementFragment)
  │
  ├─ Hiện ProgressDialog (chặn UI trong khi xóa DB)
  ├─ RetrofitClient.cancelAllRequests()  ← hủy TẤT CẢ request đang bay
  ├─ WorkManager.cancelUniqueWork("TxSync")
  ├─ [background] clearAllTables() + SyncPrefs.clearAll()
  └─ [main thread] pd.dismiss() → TokenManager.clear() → navigate LoginActivity (CLEAR_TASK)
```

### Backend Security

- `/api/auth/**` — public (login, register)
- Tất cả endpoint khác — yêu cầu JWT hợp lệ
- Stateless session (CSRF disabled), BCrypt password encoding

---

## 🔒 Multi-Account Data Isolation

Đảm bảo dữ liệu của từng tài khoản hoàn toàn tách biệt. Được triển khai theo 4 lớp:

### Lớp 1: Schema — `user_id` trên tất cả entities

Migration 16→17 thêm cột `user_id TEXT` vào 5 bảng. Tất cả DAOs dùng `WHERE user_id = :userId`:

```sql
-- Ví dụ: TransactionDao
SELECT * FROM transactions WHERE isDeleted = 0 AND user_id = :userId ORDER BY timestampMs DESC
```

Nếu `userId = null` (chưa đăng nhập), query trả về rỗng.

### Lớp 2: Write — Stamp userId khi ghi vào Room

Mọi path ghi dữ liệu đều đặt `userId` trước khi insert:

- **User actions** (`AppViewModel.addBudget/addGoal/addDebtLoan/addWallet/addTransaction`): set `entity.setUserId(TokenManager.getOrCreate(...).getUserId())` trước `insert()`
- **Server sync** (tất cả `convertToModel()` + `sync()` methods): `b.setUserId(requestUserId)` trước `budgetDao.insert(b)`

### Lớp 3: Async Guard — Stale response bị loại bỏ

Xem mục **Repository Async Sync** ở trên.

### Lớp 4: Account Switch — Xóa DB + Cancel requests

Khi user đổi tài khoản (Login phát hiện `userChanged || noCredentials`):

```java
RetrofitClient.cancelAllRequests();          // abort in-flight HTTP
WorkManager.cancelUniqueWork("TxSync");      // stop pending worker
clearAllTables();                            // wipe entire Room DB
SyncPrefs.clearAll();                        // reset delta cursors
TokenManager.saveAuthData(newUser);          // update singleton
// → navigate to new MainActivity (fresh AppViewModel, fresh LiveData)
```

`AppViewModel` tạo mới với `currentUserId` mới → `budgetsLiveData = getAllBudgets(newUserId)` → chỉ thấy data của user mới.

### Shared OkHttp Dispatcher

`RetrofitClient` dùng một `Dispatcher` chung cho tất cả `createService()` và `createAiInsightService()`:

```java
private static final okhttp3.Dispatcher sharedDispatcher = new okhttp3.Dispatcher();

public static void cancelAllRequests() {
    sharedDispatcher.cancelAll();  // hủy tất cả request đang bay tại tầng HTTP
}
```

Auth API (`getAuthApi()`) dùng client riêng — không bị cancel.

---

## ⚙️ Financial Engine

`AppViewModel` chứa 2 engine tính toán reactive:

### Engine State (MediatorLiveData)

Lắng nghe: `transactions(userId)` + `wallets(userId)` + `totalIOwe(userId)` + `totalOwedToMe(userId)`

```
totalAssets  = Σ wallet.balanceVnd (where includedInTotal=true)
netWorth     = totalAssets - totalDebts + totalLoans
mIncome      = Σ income transactions (tháng hiện tại)
mExpense     = Σ expense transactions (tháng hiện tại)
mTransfer    = Σ transfer transactions (tháng hiện tại)
```

### Budget Engine (MediatorLiveData)

Lắng nghe: `transactions(userId)` + `budgets(userId)`

```
Với mỗi Budget:
  spentAmount = budget.spentAmount (server) + Σ(expense tx chưa sync, trong kỳ, cùng categoryName)
```

### Insight Engine

`InsightEngine.analyzeAll(userId)` nhận `userId` và pass xuống tất cả DAO queries:
- `BudgetForecaster`: OLS regression dự báo chi tiêu
- `AnomalyDetector`: Z-score phát hiện bất thường
- `PatternAnalyzer`: Xu hướng chi tiêu theo ngày trong tuần
- `GoalAdvisor`: Tư vấn tốc độ tích lũy
- `DebtLoanAdvisor`: Nhắc nhở hạn nợ

---

## 🖥 UI & Navigation

### Navigation Graph (`nav_graph.xml`)

```
SplashActivity → LoginActivity → MainActivity
                                      │
                    ┌─────────────────┼──────────────────┐
                    │                 │                   │
              Dashboard        BottomNav Tabs        FAB (+)
                │               ├─ Analytics      NewTransaction
                │               ├─ Goals (popup)
                │               │    ├─ GoalsFragment
                │               │    ├─ BudgetsFragment
                │               │    └─ DebtsLoansFragment
                │               └─ WalletManagement (có nút Đăng xuất)
                │
                ├─ AllTransactions
                └─ ScanReceipt
```

### Bottom Navigation

4 tabs: Dashboard | Analytics | Goals (dropdown) | WalletManagement

- **WalletManagement** có nút **Đăng xuất** ở toolbar (thay thế hiển thị số dư cũ)

### Bottom Sheets

- `AddWalletBottomSheet` / `EditWalletBottomSheet` — CRUD ví
- `AddBudgetSheet` — Tạo/sửa ngân sách
- `AddDebtSheet` — Tạo nợ/cho vay
- `AddGoalSheet` — Tạo mục tiêu
- `AddCategorySheet` — Tạo danh mục
- `TransactionDetailSheet` — Chi tiết giao dịch (edit/delete)
- `WalletDetailSheet` — Chi tiết ví
- `IconPickerBottomSheet` — Chọn emoji icon

### Dashboard Tabs

- **Hôm nay** — Giao dịch trong ngày
- **Tuần** — Giao dịch trong tuần (Thứ 2 → Chủ nhật)
- **Tháng** — Giao dịch trong tháng

---

## 🌐 API Endpoints

### Auth (Public)
| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/auth/login` | Đăng nhập → JWT |
| POST | `/api/auth/register` | Đăng ký |

### Transactions (Authenticated)
| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/transactions` | Tạo giao dịch |
| POST | `/api/transactions/sync` | Batch push offline data |
| GET | `/api/transactions/recent?days=N` | N ngày gần nhất |
| PUT | `/api/transactions/{id}` | Cập nhật |
| DELETE | `/api/transactions/{id}` | Soft delete |
| GET | `/api/transactions/delta` | Delta sync (cursor-based) |

### Financial (Authenticated)
| Method | Path | Mô tả |
|--------|------|-------|
| GET/POST/PUT/DELETE | `/api/accounts/*` | CRUD Wallets |
| GET | `/api/accounts/delta` | Delta sync accounts |
| GET/POST/PUT/DELETE | `/api/categories/*` | CRUD Categories |
| GET | `/api/categories/delta` | Delta sync categories |
| GET/POST/PUT/DELETE | `/api/budgets/*` | CRUD Budgets |
| GET | `/api/budgets/delta` | Delta sync budgets |
| GET/POST/PUT/DELETE | `/api/goals/*` | CRUD Goals |
| GET | `/api/goals/delta` | Delta sync goals |
| GET/POST/PUT/DELETE | `/api/debts/*` | CRUD Debts |
| GET | `/api/debts/delta` | Delta sync debts |
| GET | `/api/dashboard/summary` | Tổng hợp tháng |

**Delta endpoint params**: `?updatedSince=&untilTimestamp=&lastUpdatedAt=&lastId=&limit=500`

### Kết nối

- **Base URL**: `https://aviation-skincare-undertone.ngrok-free.dev/api/` (ngrok tunnel)
- **Logging**: `HttpLoggingInterceptor.Level.HEADERS` (chỉ bật trên `BuildConfig.DEBUG`)

---

## ⚠️ Gotchas & Known Issues

### 1. EWALLET ↔ e_wallet Enum Mismatch ✅
Room lưu `"EWALLET"`, Neon cần `"e_wallet"`. Convert tại `WalletTypeConverter`.

### 2. Amount luôn dương ✅
Server có `CHECK(amount > 0)`. Android phải gửi `Math.abs(amountVnd)`. `type` xác định chiều.

### 3. Wallet = Account ✅
Android gọi là "Wallet", Backend gọi là "Account". Mapping 1:1.

### 4. TokenManager chỉ khởi tạo trên Main Thread ✅
Lần đầu gọi `TokenManager.getOrCreate()` phải trên Main Thread (trong `SplashActivity`) vì EncryptedSharedPreferences truy cập Android Keystore — Keystore có thể throw `SecurityException` nếu gọi từ background thread. Sau lần đầu, các lần gọi tiếp theo trên bất kỳ thread nào chỉ return cached instance, an toàn.

### 5. 401 vs 403 ✅
Chỉ **HTTP 401** kích hoạt session expiry + `TokenManager.clear()`. HTTP 403 là lỗi phân quyền hợp lệ (resource không thuộc user), không phải token hết hạn.

### 6. Stale Response Race Condition ✅
Khi user đổi tài khoản trong khi có API call đang in-flight:
- `RetrofitClient.cancelAllRequests()` hủy tại tầng HTTP ngay lập tức
- Stale response guard (`requestUserId.equals(tokenManager.getUserId())`) là safety net thứ 2

### 7. Fragment Lifecycle trong performLogout() ✅
`performLogout()` gọi `clearAllTables()` trên background thread (~50–200ms). Nếu Fragment bị detach trước khi hoàn tất (xoay màn hình), handler check `isAdded() && !isDetached() && getActivity() != null` trước khi dismiss dialog và navigate.

### 8. UUID validation ✅
Khi resolve accountId/categoryId, nếu ID local không phải UUID → tự sync entity lên Neon để nhận UUID thật.

### 9. DebtLoan types ✅
Dùng int constants (`TYPE_I_OWE = 0`, `TYPE_LENT = 1`), không dùng enum.

---

## 🚀 Hướng phát triển

### Ngắn hạn (Priority)

- [x] **Incremental Sync**: Chỉ push `isSynced=false` records ✅
- [x] **Conflict Resolution**: LWW với `updatedAtMs` ✅
- [x] **Error Handling**: `SessionManager` singleton, `Event<T>` wrapper, `NetworkUtils` fail-fast ✅
- [x] **Pull-to-Refresh**: `SwipeRefreshLayout` trên Dashboard và AllTransactions ✅
- [x] **Recurring Transactions**: `isRecurring` + `recurInterval`, badge trên adapter ✅
- [x] **Receipt Scanning**: CameraX + ML Kit OCR + `InvoiceApi` ✅
- [x] **Multi-Account Data Isolation**: `user_id` trên tất cả entities, DAO filter, stale response guard, cancel in-flight requests ✅

### Trung hạn

- [x] **Multi-currency Support**: `currencyCode` field (default VND) ✅
- [x] **Analytics Charts**: MPAndroidChart (BarChart, PieChart) ✅
- [x] **Budget Auto-Calculate**: `BudgetWithSpent` LiveData từ Engine ✅
- [x] **Multi-device Sync**: Delta sync, soft delete, LWW, `TransactionSyncWorker` ✅
- [x] **Widget**: `BalanceWidgetProvider` — tổng tài sản, tài sản ròng, 3 giao dịch gần nhất ✅
- [x] **AI Insights**: `InsightEngine` (OLS, Z-score, EWMA) + AI API backend ✅
- [ ] **Export Data**: Xuất CSV/PDF ⚠️ chưa bắt đầu
- [ ] **Dark Mode**: `values-night/themes.xml` chỉ là placeholder, chưa override màu ⚠️
- [ ] **Notification System**: `NotificationBottomSheet` chỉ là shell, chưa có FCM ⚠️
- [ ] **Profile Management**: `ProfileFragment` chỉ inflate layout, chưa có UI ⚠️
- [ ] **Cloud Deployment**: Backend vẫn dùng ngrok tunnel, chưa deploy Koyeb ⚠️

### Dài hạn

- [ ] **Real-time Sync**: WebSocket/SSE thay vì polling ⚠️
- [ ] **Google Login**: `SocialLoginButton` có nhưng chưa implement OAuth ⚠️

---

## 🏃 Chạy dự án

### Backend
```bash
cd backend
./gradlew bootRun
```
Cần cấu hình `application.properties` với Neon PostgreSQL connection string.

### Android
1. Mở project bằng Android Studio
2. Cập nhật `BASE_URL` trong `RetrofitClient.java` nếu cần
3. Chạy trên emulator hoặc thiết bị thật

---

## 👥 Thông tin dự án

- **Tên**: TIP — Tap Into Prosperity
- **Package**: `vn.edu.usth.tip`
- **Tổ chức**: USTH (University of Science and Technology of Hanoi)
- **Ngôn ngữ giao diện**: Tiếng Việt
- **Đơn vị tiền tệ**: VNĐ (₫)
