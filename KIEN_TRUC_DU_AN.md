# TIP – Tap Into Prosperity (Money Tracker)
## Giải thích kiến trúc dự án: Từ Frontend đến Backend

Tài liệu này giải thích toàn bộ luồng hoạt động của dự án, đi từ giao diện Android (frontend) → tầng mạng → backend Spring Boot → database PostgreSQL.

| Thông tin | Giá trị |
|---|---|
| **Package gốc** | `vn.edu.usth.tip` |
| **Ngôn ngữ** | Java (cả Android lẫn Backend) |
| **Đơn vị tiền tệ** | VND |
| **Ngôn ngữ giao diện** | Tiếng Việt |

---

## 1. Tổng quan kiến trúc

```
[ ANDROID APP (Java) ]
     UI (Activity/Fragment)
          │  observe LiveData
     ViewModel  (AppViewModel = Financial Engine)
          │
     Repository (offline-first)
          │                       \
     Room SQLite (cache)       Retrofit/OkHttp (HTTP + JWT)
                                    │
                                    ▼
[ SPRING BOOT 4.0 BACKEND (Java 21) ]
     Controller → Service → JPA Repository
     Security: JWT (jjwt) + BCrypt
          │  JDBC
          ▼
[ NEON POSTGRESQL (Cloud) ]
     users, accounts, categories, transactions, budgets, goals, debts
```

### Nguyên tắc cốt lõi: Offline-First

- Mọi thao tác CRUD ghi vào **Room TRƯỚC**, sync lên server **SAU**.
- UI luôn phản ánh từ Room qua LiveData (reactive).
- Mất mạng vẫn dùng được; có mạng thì background worker đẩy dữ liệu lên.

---

## 2. Frontend – Android (module: `app/`)

Thư mục gốc: `app/src/main/java/vn/edu/usth/tip/`

### 2.1. Tầng UI (`ui/activities` + `ui/fragments`)

**Activities** (`ui/activities/`) — khai báo trong `AndroidManifest.xml`:

| Activity | Vai trò |
|---|---|
| `SplashActivity` | Màn hình khởi động. Khởi tạo `TokenManager` singleton trên Main Thread (bắt buộc vì `EncryptedSharedPreferences` chậm do Android Keystore). Kiểm tra token → điều hướng Login hoặc MainActivity. |
| `LoginActivity` | Đăng nhập. Phát hiện đổi tài khoản (userChanged / noCredentials) → gọi `clearAllTables()` trước khi vào app. |
| `SignupActivity` | Đăng ký tài khoản mới. |
| `MainActivity` | Khung chính chứa Navigation Component + Bottom Nav. Lắng nghe sự kiện hết phiên (`setupSessionExpiry`). |

**Fragments** (`ui/fragments/`) — các màn hình điều hướng qua `nav_graph.xml`:

| Fragment | Vai trò |
|---|---|
| `DashboardFragment` | Màn hình chính. Hiện tổng tài sản, giao dịch gần đây. Dùng "optimistic UI" (gộp số liệu server + giao dịch chưa sync). |
| `AnalyticsFragment` | Biểu đồ phân tích (MPAndroidChart – BarChart). |
| `SpendingByCategoryFragment` | PieChart chi tiêu theo danh mục (nhúng trong AnalyticsFragment). |
| `BudgetsFragment` | Quản lý ngân sách. |
| `GoalsFragment` | Mục tiêu tiết kiệm. |
| `DebtsLoansFragment` | Nợ / Cho vay. |
| `WalletManagementFragment` | Quản lý ví + nút **ĐĂNG XUẤT**. |
| `AllTransactionsFragment` | Danh sách tất cả giao dịch. |
| `NewTransactionFragment` | Form tạo/sửa giao dịch. |
| `ScanReceiptFragment` | Quét hóa đơn (CameraX + ML Kit OCR). |
| `ExtractInvoiceFragment` | Hiện kết quả bóc tách hóa đơn. |
| `ProfileFragment` | (skeleton – chưa hoàn thiện). |

**BottomSheets** (hiện bằng `.show()`, không qua nav_graph):
- `AddWalletBottomSheet` / `EditWalletBottomSheet` / `WalletDetailSheet`
- `AddBudgetSheet` / `AddGoalSheet` / `AddDebtSheet` / `AddCategorySheet`
- `TransactionDetailSheet` (xem/sửa/xóa giao dịch)
- `IconPickerBottomSheet` (chọn emoji)
- `NotificationBottomSheet` (skeleton)

> **BaseFragment**: lớp cha, cấp `AppViewModel` dùng chung (scope theo Activity).

### 2.2. Tầng ViewModel (`viewmodels/`)

- **`AppViewModel`** — **QUAN TRỌNG NHẤT** — "Financial Engine".
  Dùng `MediatorLiveData` tính toán reactive:
  - **Engine State**: lắng nghe transactions + wallets + debts/loans
    - `totalAssets` = tổng số dư ví (`includedInTotal`)
    - `netWorth` = totalAssets − nợ + cho vay
    - `mIncome` / `mExpense` / `mTransfer` = tổng tháng hiện tại
  - **Budget Engine**: lắng nghe transactions + budgets
    - `spentAmount` = spent(server) + giao dịch chưa sync cùng danh mục
  - Tất cả LiveData đều **lọc theo `userId`**:
    `budgetsLiveData = budgetDao.getAllBudgets(currentUserId)`
  - Các hàm `add*` / `update*`: stamp `userId` trước khi insert vào Room.
- **`LoginViewModel`**: gọi AuthRepository login/register; lưu token (singleton).
- **`AccountViewModel`**: CRUD ví qua API (AccountRepository).
- **`DashboardViewModel`**: lấy tổng hợp tháng từ `/api/dashboard/summary`.
- **`NewTransactionViewModel`**: state form tạo giao dịch (loại mặc định, recurring).

### 2.3. Tầng Repository (`repositories/`) — Logic Sync Offline-First

| Repository | Vai trò |
|---|---|
| `TransactionRepository` | **PHỨC TẠP NHẤT**. Push/pull/CRUD giao dịch. |
| `WalletsRepository` | sync ví (account). |
| `CategoriesRepository` | sync danh mục. |
| `BudgetsRepository` | sync ngân sách (delete-before-insert theo userId). |
| `GoalsRepository` | sync mục tiêu. |
| `DebtsRepository` | sync nợ/cho vay. |
| `DashboardRepository` | lấy summary. |
| `AccountRepository` | CRUD ví trực tiếp. |
| `AuthRepository` | login/register. |

**Chi tiết `TransactionRepository`:**
- `pushUnsyncedBatchSync()` — đẩy giao dịch offline lên `/sync`
- `pullDeltaTransactionsSync()` — kéo delta về (cursor-based)
- `resolveAccountId` / `resolveCategoryId` — tự sync ví/danh mục lên server để lấy UUID thật trước khi gán vào giao dịch
- LWW conflict resolution (`updatedAtMs`)

**Mẫu chung "Stale Response Guard"** trong mọi `sync()`:

```java
final String requestUserId = tokenManager.getUserId(); // lúc gửi request
api.getX().enqueue(... onResponse:
    if (!requestUserId.equals(tokenManager.getUserId())) return; // bỏ qua
    ... ghi DB với requestUserId ...
)
```

→ Ngăn response của user A ghi dữ liệu vào DB của user B khi đổi tài khoản.

### 2.4. Tầng Data – Room SQLite (`models/` + `AppDatabase.java`)

`AppDatabase.java`: cấu hình Room, **version = 18**, danh sách migration, seed data.
6 entity: `Transaction`, `Category`, `Wallet`, `Budget`, `DebtLoan`, `Goal`.
Tất cả đều có cột **`user_id`** (để cô lập dữ liệu theo tài khoản).

**Entities** (`models/`):

| Entity | Trường chính |
|---|---|
| `Transaction` | id, title, category, walletName, amountVnd, type (EXPENSE/INCOME/TRANSFER), timestampMs, isSynced, updatedAtMs, isDeleted, isRecurring, user_id… |
| `Wallet` | id, name, balanceVnd, type (CASH/BANK/EWALLET/INVESTMENT), includedInTotal, user_id… |
| `Category` | id, name, type (income/expense), is_system, user_id… |
| `Budget` | id, categoryName, limitAmount, spentAmount, kỳ, user_id |
| `Goal` | id, name, targetAmount, savedAmount, targetDate, user_id |
| `DebtLoan` | id, personName, amount, type (0=nợ/1=cho vay), user_id |

**DAOs** (`models/*Dao.java`): interface SQL thuần, Room tự sinh code.
Ví dụ: `getAllTransactions("WHERE isDeleted=0 AND user_id=:userId ...")`
Trả `LiveData` → Room tự notify khi bảng thay đổi.

**Migrations** (trong `AppDatabase`): 7→8→…→17→18
- `15→16`: thêm `user_id` vào categories
- `16→17`: thêm `user_id` vào 5 bảng còn lại
- `17→18`: dọn các dòng `user_id IS NULL` (dữ liệu cũ mồ côi) → sync kéo lại

### 2.5. Tầng Network (`network/`)

`RetrofitClient.java`: tạo Retrofit service.
- `sharedDispatcher` (OkHttp) dùng chung → `cancelAllRequests()` khi logout.
- **Interceptor**: tự thêm `Authorization: Bearer <token>`.
  Nếu response **401 HOẶC 403** → `tokenManager.clear()` + `SessionManager.triggerSessionExpired()` (buộc đăng xuất, về Login).
- `getAuthApi()`: client riêng cho login (không gắn token, không bị cancel).

**API interfaces:**

| Interface | Endpoint |
|---|---|
| `AuthApi` | `/api/auth/login`, `/register` |
| `TransactionApi` | CRUD + `/sync` + `/recent` + `/delta` |
| `FinancialApi` | accounts, categories, budgets, goals, debts (+ `/delta`) |
| `DashboardApi` | `/api/dashboard/summary` |
| `AccountApi` | CRUD ví |
| `InvoiceApi` | phân tích hóa đơn |
| `InsightApi` | AI insight |

`requests/` + `responses/` + `dto/`: các DTO truyền qua mạng (Gson).

### 2.6. Các thành phần khác

- **`workers/TransactionSyncWorker`** — WorkManager — sync nền đẩy đủ theo thứ tự FK:
  push → categories → accounts → budgets → goals → transactions.
- **`widget/BalanceWidgetProvider`** — widget màn hình chính (tổng tài sản, 3 giao dịch gần nhất), lọc theo userId.
- **`insights/`** (`InsightEngine` + `engine/`) — tính toán AI on-device:
  `BudgetForecaster` (hồi quy OLS), `AnomalyDetector` (Z-score), `PatternAnalyzer`, `GoalAdvisor`, `DebtLoanAdvisor`. Sau đó gọi backend `/api/ai/insights` để làm giàu nội dung bằng LLM.
- **`utils/`** — tiện ích dùng chung (xem chi tiết dưới).

**Package `utils/` (10 file, chia 5 nhóm):**

| Nhóm | File |
|---|---|
| **Auth/Session** | `TokenManager` (lưu JWT, EncryptedSharedPreferences, singleton), `SessionManager` (LiveData sự kiện hết phiên) |
| **Sync/Network** | `SyncPrefs` (cursor delta sync), `NetworkUtils` (kiểm tra mạng) |
| **Convert/Format** | `WalletTypeConverter` (enum ↔ API), `MoneyFormat` (định dạng VND), `InvoiceParser` (bóc tách text OCR) |
| **Animation** | `AnimUtils` (bounce click), `AnimationConstants` (hằng số) |
| **Generic** | `Event` (LiveData event một-lần) |

---

## 3. Backend – Spring Boot (module: `backend/`)

Thư mục gốc: `backend/src/main/java/vn/edu/usth/tip/backend/`
Kiến trúc 3 tầng chuẩn: **Controller → Service → Repository (JPA) → PostgreSQL**.

### 3.1. Controllers (`controllers/`) — nhận HTTP request

| Controller | Endpoint |
|---|---|
| `AuthController` | POST `/api/auth/login`, `/register` (PUBLIC) |
| `UserController` | thông tin người dùng |
| `AccountController` | CRUD `/api/accounts` + `/delta` |
| `CategoryController` | CRUD `/api/categories` + `/delta` |
| `TransactionController` | CRUD `/api/transactions` + `/sync` + `/recent` + `/delta` |
| `BudgetController` | CRUD `/api/budgets` + `/delta` |
| `GoalController` | CRUD `/api/goals` + `/delta` |
| `DebtController` | CRUD `/api/debts` + `/delta` |
| `DashboardController` | GET `/api/dashboard/summary` |
| `InvoiceController` | POST phân tích hóa đơn (gọi Gemini) |
| `InsightController` | POST `/api/ai/insights` (gọi Gemini) |

### 3.2. Services (`services/`) — logic nghiệp vụ + kiểm tra quyền

Auth, User, Account, Category, Transaction, Budget, Goal, Debt, Dashboard Service: mỗi service lấy `userId` hiện tại từ SecurityContext (`SecurityUtils`) → chỉ trả/dữ liệu của đúng user đó.

- `TransactionService`: xử lý batch sync, LWW (`clientUpdatedAt`), soft delete.
- `GeminiService`: gọi Google Gemini API (AI) cho insight + OCR hóa đơn.
- `InsightService`: tạo gợi ý tài chính từ dữ liệu người dùng.

### 3.3. Repositories (`repositories/`) — Spring Data JPA

`UserRepository`, `AccountRepository`, `CategoryRepository`, `TransactionRepository`, `BudgetRepository`, `GoalRepository`, `DebtRepository`.
Truy vấn PostgreSQL; delta dùng `Slice<T>` (cursor, không `COUNT(*)`).

### 3.4. Models (`models/`) — JPA Entities

`User`, `Account` (= Wallet bên Android), `Category`, `Transaction`, `Budget`, `Goal`, `Debt`. Tất cả có `deletedAt` (soft delete) + `updatedAt` (cho delta sync).

`enums/`: `AccountType`, `CategoryType`, `TransactionType`, `BudgetPeriod`, `RecurrenceInterval`, `GoalStatus`, `DebtType`, `DebtStatus`.

### 3.5. Security (`security/`)

- `JwtUtil`: tạo/giải mã/validate JWT (jjwt). Hết hạn 24h (`jwt.expiration`).
- `JwtAuthFilter`: đọc header Authorization, validate token, set SecurityContext. Token lỗi → không set → Security tự lọc.
- `SecurityConfig`: `/api/auth/**` PUBLIC; còn lại `.authenticated()`. Có `AuthenticationEntryPoint` → trả **401** (không phải 403) khi thiếu/sai token. Stateless, CSRF off, BCrypt.
- `CustomUserDetailsService`: load user theo email.

### 3.6. Khác

- `config/JacksonConfig`: cấu hình JSON.
- `config/DatabaseMigrationRunner`: chạy migration DB lúc khởi động.
- `exception/GlobalExceptionHandler`: bắt lỗi tập trung → JSON lỗi chuẩn.
- `dto/`: Request/Response DTO theo từng feature (auth, account, transaction…) + `common/DeltaResponse` (dùng chung cho delta sync).
- `utils/`: `SecurityUtils` (lấy userId hiện tại), `SyncConstants`, `GeminiConstants`, `BudgetConstants`.

---

## 4. Database – Neon PostgreSQL

**Bảng chính:**

| Bảng | Cột chính |
|---|---|
| `users` | id(UUID), email, password_hash (BCrypt), full_name |
| `accounts` | id, user_id(FK), name, type, balance, deleted_at, updated_at |
| `categories` | id, user_id(FK), name, type, icon, is_system |
| `transactions` | id, user_id, account_id, category_id, amount(>0 CHECK), type, transaction_date, is_recurring, deleted_at, updated_at |
| `budgets` | id, user_id, category_id, amount, spent_amount, period… |
| `goals` | id, user_id, name, target_amount, current_amount, target_date |
| `debts` | id, user_id, contact_name, amount, type, due_date |

**Ánh xạ Android ↔ Backend đáng lưu ý:**
- `Wallet` (Android) = `Account` (Backend)
- `Wallet.Type.EWALLET` = `"e_wallet"` (**KHÔNG** phải `"EWALLET"`)
- `amount` luôn **DƯƠNG**; trường `type` quyết định thu/chi.

---

## 5. Luồng end-to-end ví dụ: Tạo một giao dịch

1. User nhập form → bấm **"Lưu"**
2. `NewTransactionFragment` → `AppViewModel.addTransaction(tx)`
   `tx.userId = currentUser; tx.isSynced = false; tx.updatedAtMs = now`
3. `transactionDao.insert(tx)` → Room cập nhật **NGAY**
   → DashboardFragment observe LiveData → số dư đổi tức thì (optimistic)
4. WorkManager enqueue `TransactionSyncWorker`
5. **Worker:**
   - **a.** `pushUnsyncedBatchSync()`
     - resolve account/category UUID (sync lên server nếu cần)
     - POST `/api/transactions/sync`
     - server trả UUID thật → thay record local bằng bản server
   - **b.** `pullDeltaTransactionsSync()`
     - GET `/api/transactions/delta?updatedSince=<cursor>`
     - cập nhật Room (LWW), lưu cursor mới vào `SyncPrefs`
6. Room LiveData fires → UI hiện dữ liệu đã đồng bộ với server.

---

## 6. Bảo mật & Cô lập dữ liệu (Data Isolation)

### Xác thực
- Đăng nhập → JWT, lưu trong `EncryptedSharedPreferences` (mã hóa AES).
- Mỗi request gắn `Authorization: Bearer <token>`.
- Backend trả **401** khi token thiếu/sai/hết hạn.
- Client gặp **401 HOẶC 403** → clear token → về màn Login.

### Cô lập dữ liệu giữa các tài khoản (4 lớp)

| Lớp | Cơ chế |
|---|---|
| **Lớp 1 (Schema)** | cột `user_id` trên tất cả entity; DAO lọc `WHERE user_id=:id` |
| **Lớp 2 (Write)** | stamp `userId` trước mọi insert (user action + server sync) |
| **Lớp 3 (Async)** | stale response guard (bỏ qua response của user cũ) |
| **Lớp 4 (Switch)** | đổi tài khoản → `cancelAllRequests` + cancel worker + `clearAllTables` + `SyncPrefs.clearAll` → AppViewModel mới |

---

## 7. Những điểm cần lưu ý (Gotchas)

- **EWALLET ↔ e_wallet**: convert tại `WalletTypeConverter`.
- **amount luôn `Math.abs()`**: server có `CHECK(amount > 0)`.
- **Wallet = Account**: tên khác nhau giữa 2 phía, map 1:1.
- **TokenManager**: lần đầu `getOrCreate()` PHẢI trên Main Thread (Keystore).
- **401 vs 403**: backend trả 401 cho lỗi auth; client xử lý cả hai để phòng xa.
- **JWT_SECRET** (env var) phải **CỐ ĐỊNH** giữa các lần restart backend; nếu đổi sẽ làm mọi token cũ vô hiệu → 401/403 hàng loạt.
- Sau cập nhật (migration 18): 5 màn hình có thể trống vài giây chờ sync kéo dữ liệu về từ server — đây là hành vi đúng thiết kế.

---

## 8. Công nghệ sử dụng

| Mảng | Công nghệ |
|---|---|
| **Android** | Java, Room, Retrofit 2, OkHttp, Gson, LiveData, ViewModel, Navigation Component, WorkManager, Material Design 3, CameraX + ML Kit (OCR), MPAndroidChart, EncryptedSharedPreferences |
| **Backend** | Spring Boot 4.0.5, Spring Security, Spring Data JPA, Hibernate, Lombok, jjwt 0.12.6, BCrypt, Google Gemini API |
| **Database** | Neon PostgreSQL (cloud) + Room SQLite (local cache) |
| **Build** | Gradle 8.13 (AGP 8.13.2), Java 21 |
| **Kết nối** | Hiện dùng ngrok tunnel (`BASE_URL` trong `RetrofitClient.java`) |
