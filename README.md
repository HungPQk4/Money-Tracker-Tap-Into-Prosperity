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
| **Android** | Java, Room Database, Retrofit 2, OkHttp, Gson, LiveData, ViewModel, Navigation Component, Material Design 3 |
| **Backend** | Spring Boot 4.0.5, Spring Security, Spring Data JPA, Hibernate, Lombok |
| **Database** | Neon PostgreSQL (cloud), Room SQLite (local) |
| **Auth** | JWT (jjwt 0.12.6), BCrypt |
| **Build** | Gradle (Kotlin DSL), Java 21 |

---

## 📁 Cấu trúc dự án

```
Money-Tracker-Tap-Into-Prosperity/
├── app/                          # Android Application
│   └── src/main/java/vn/edu/usth/tip/
│       ├── AppDatabase.java      # Room DB config, migrations, seed data
│       ├── models/               # Room Entities + DAOs
│       │   ├── Transaction.java  # Giao dịch (EXPENSE/INCOME/TRANSFER)
│       │   ├── Category.java     # Danh mục (expense/income)
│       │   ├── Wallet.java       # Ví (CASH/BANK/EWALLET/INVESTMENT)
│       │   ├── Budget.java       # Ngân sách theo kỳ
│       │   ├── Goal.java         # Mục tiêu tiết kiệm
│       │   ├── DebtLoan.java     # Nợ/Cho vay
│       │   └── *Dao.java         # Data Access Objects
│       ├── network/              # Retrofit API layer
│       │   ├── RetrofitClient.java    # Base URL, interceptors, JWT
│       │   ├── AuthApi.java           # Login/Register (public)
│       │   ├── TransactionApi.java    # CRUD + sync + recent
│       │   ├── FinancialApi.java      # Accounts, Categories, Budgets, Goals, Debts
│       │   ├── requests/             # Request DTOs
│       │   └── responses/            # Response DTOs
│       ├── repositories/         # Data sync logic (offline-first)
│       │   ├── TransactionRepository.java  # Push/Pull/CRUD sync (phức tạp nhất)
│       │   ├── WalletsRepository.java
│       │   ├── CategoriesRepository.java
│       │   ├── BudgetsRepository.java
│       │   ├── GoalsRepository.java
│       │   └── DebtsRepository.java
│       ├── viewmodels/           # MVVM ViewModels
│       │   ├── AppViewModel.java          # Financial Engine chính
│       │   ├── NewTransactionViewModel.java
│       │   ├── LoginViewModel.java
│       │   ├── AccountViewModel.java
│       │   └── DashboardViewModel.java
│       ├── adapters/             # RecyclerView Adapters
│       ├── ui/
│       │   ├── activities/       # Splash, Login, Signup, Main
│       │   └── fragments/        # Dashboard, Wallets, Transactions, Goals...
│       └── utils/
│           └── TokenManager.java # SharedPreferences JWT storage
│
├── backend/                      # Spring Boot REST API
│   └── src/main/java/vn/edu/usth/tip/backend/
│       ├── BackendApplication.java
│       ├── models/               # JPA Entities
│       │   ├── User.java
│       │   ├── Account.java      # = Wallet trên Android
│       │   ├── Category.java
│       │   ├── Transaction.java
│       │   ├── Budget.java
│       │   ├── Goal.java
│       │   ├── Debt.java
│       │   └── enums/            # AccountType, TransactionType, etc.
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

> ⚠️ **EWALLET → e_wallet**: Đây là gotcha lớn nhất. Room lưu `"EWALLET"`, Neon cần `"e_wallet"`. Chuyển đổi được thực hiện trong `WalletsRepository.mapTypeToNeon()` và `TransactionRepository.syncWalletToNeon()`.

### UUID cố định (Seed Data)

Categories và Wallets mặc định sử dụng UUID cố định, được khai báo trong `AppDatabase.java`:

```
EXPENSE Categories:  a1000000-0000-0000-0000-00000000000X
INCOME Categories:   b1000000-0000-0000-0000-00000000000X
Default Wallets:     w1000000-0000-0000-0000-00000000000X
```

Các UUID này **phải khớp** giữa Room và Neon để tránh duplicate khi sync.

### Room Database Version: 10

Migration path: `7→8→9→10`
- **7→8**: Thêm `spentAmount` vào `budgets`
- **8→9**: Xóa data categories/wallets cũ
- **9→10**: ALTER TABLE thêm cột mới (`color_hex`, `type`, `is_system` cho categories; `balanceVnd`, `color`, `type`, `includedInTotal` cho wallets) + re-seed

Có `fallbackToDestructiveMigration()` — nếu mất migration sẽ xóa toàn bộ DB.

---

## 🔄 Luồng đồng bộ dữ liệu

### 1. Transaction Sync (phức tạp nhất)

```
syncTransactions()
  │
  ├─ 1. resetSyncStatus() — đặt tất cả isSynced = false
  │
  ├─ 2. pushUnsyncedToServer() — đẩy dữ liệu offline lên
  │     ├─ Lấy tất cả tx có isSynced=false
  │     ├─ resolveAccountId() — tìm UUID wallet (tự sync nếu cần)
  │     ├─ resolveCategoryId() — tìm UUID category (tự sync nếu cần)
  │     ├─ POST /api/transactions/sync (batch)
  │     └─ Xóa bản nháp cũ, insert bản từ server
  │
  └─ 3. pullFromServer() — kéo 30 ngày gần nhất về
        ├─ GET /api/transactions/recent?days=30
        ├─ Xóa record đã sync nhưng server không còn
        └─ Upsert từng record từ server
```

**Quy tắc quan trọng:**
- `amount` luôn gửi **giá trị dương** (abs). `type` xác định chiều (income/expense/transfer).
- Server có CHECK constraint `amount > 0`.
- `createdAt` giữ nguyên thời gian gốc từ client khi batch sync.

### 2. Wallet/Category Sync

- **Auto-resolve**: Khi tạo transaction mà wallet/category chỉ có local ID → tự động sync lên Neon trước, nhận UUID → dùng UUID đó.
- **Pull sync**: Khi app khởi động, `syncAllData()` gọi tất cả repository sync song song.
- **Dedup logic**: Khi pull wallets, nếu tìm thấy wallet cùng tên nhưng khác ID → xóa bản cũ, insert bản mới.

### 3. Optimistic UI

Dashboard hiển thị **optimistic data**: số liệu từ PostgreSQL + các giao dịch chưa sync (isSynced=false) để UI luôn phản ánh hành động user ngay lập tức.

```
Optimistic Balance = Server Balance + Σ(unsyncedIncome) - Σ(unsyncedExpense)
```

### 4. Self-Healing Categories

`AppViewModel` observe danh sách categories. Nếu trống → tự động seed lại default categories:

```java
categoriesLiveData.observeForever(categories -> {
    if (categories == null || categories.isEmpty()) {
        initializeDefaultCategories();
    }
});
```

---

## 🔐 Authentication & Security

### Flow đăng nhập

```
LoginActivity → POST /api/auth/login → JWT token
  │
  ├─ TokenManager.saveAuthData(token, fullName, userId)
  └─ Navigate to MainActivity
```

### JWT Management

- **Storage**: `SharedPreferences` ("AuthPrefs") — lưu `jwt_token`, `user_full_name`, `user_id`
- **Injection**: `RetrofitClient` interceptor tự thêm `Authorization: Bearer <token>` vào mọi request
- **Auto-logout**: Khi nhận 401/403 → `TokenManager.clear()` → navigate về Login
- **Session monitoring**: `AccountViewModel` observe `sessionExpired` LiveData → `MainActivity` xử lý redirect

### Backend Security

- `/api/auth/**` — public (login, register)
- Tất cả endpoint khác — yêu cầu JWT hợp lệ
- Stateless session (CSRF disabled)
- BCrypt password encoding

---

## ⚙️ Financial Engine

`AppViewModel` chứa 2 engine tính toán reactive:

### Engine State (MediatorLiveData)

Lắng nghe: `transactions` + `wallets` + `totalIOwe` + `totalOwedToMe`

```
totalAssets  = Σ wallet.balanceVnd (where includedInTotal=true)
netWorth     = totalAssets - totalDebts + totalLoans
mIncome      = Σ income transactions (tháng hiện tại)
mExpense     = Σ expense transactions (tháng hiện tại)
mTransfer    = Σ transfer transactions (tháng hiện tại)
```

### Budget Engine (MediatorLiveData)

Lắng nghe: `transactions` + `budgets`

```
Với mỗi Budget:
  spentAmount = budget.spentAmount + Σ(expense tx trong kỳ, cùng categoryName)
```

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
                │               └─ Profile
                │
                ├─ WalletManagement
                ├─ AllTransactions
                └─ ScanReceipt
```

### Bottom Navigation

5 tabs: Dashboard | Analytics | **[FAB]** | Goals (dropdown) | Profile

Goals tab hiển thị popup menu với 3 lựa chọn: Goals, Budgets, Debts & Loans.

### Bottom Sheets

- `AddWalletBottomSheet` / `EditWalletBottomSheet` — CRUD ví
- `AddBudgetSheet` — Tạo ngân sách
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
| POST | `/api/transactions/sync` | Batch sync offline |
| GET | `/api/transactions/recent?days=N` | N ngày gần nhất |
| GET | `/api/transactions/user/{userId}` | Theo user |
| PUT | `/api/transactions/{id}` | Cập nhật |
| DELETE | `/api/transactions/{id}` | Xóa |

### Financial (Authenticated)
| Method | Path | Mô tả |
|--------|------|-------|
| GET/POST/PUT/DELETE | `/api/accounts/*` | CRUD Wallets |
| GET/POST/PUT/DELETE | `/api/categories/*` | CRUD Categories |
| GET/POST/PUT/DELETE | `/api/budgets/*` | CRUD Budgets |
| GET/POST/PUT/DELETE | `/api/goals/*` | CRUD Goals |
| GET/POST/PUT/DELETE | `/api/debts/*` | CRUD Debts |
| GET | `/api/dashboard/summary` | Tổng hợp tháng |

### Kết nối

- **Base URL**: `http://10.0.2.2:8080/api/` (Android Emulator → localhost)
- **Logging**: `HttpLoggingInterceptor.Level.BODY`

---

## ⚠️ Gotchas & Known Issues

### 1. EWALLET ↔ e_wallet Enum Mismatch
Room lưu `"EWALLET"`, Neon cần `"e_wallet"`. Convert tại `WalletsRepository.mapTypeToNeon()`.

### 2. Amount luôn dương
Server có `CHECK(amount > 0)`. Android phải gửi `Math.abs(amountVnd)`. Type field xác định chiều.

### 3. Wallet = Account
Android gọi là "Wallet", Backend gọi là "Account". Mapping 1:1, nhưng field names khác nhau.

### 4. Category field naming
- Room: `color_hex`, `is_system` (snake_case trong DB, camelCase trong Java)
- Dùng `@ColumnInfo(name = "color_hex")` để map.

### 5. Transaction sync reset
`syncTransactions()` luôn gọi `resetSyncStatus()` trước → đặt MỌI giao dịch về `isSynced=false` → push lại tất cả. Không tối ưu nhưng đảm bảo consistency.

### 6. UUID validation
Khi resolve accountId/categoryId, nếu ID local không phải UUID → tự sync entity lên Neon để nhận UUID thật.

### 7. DebtLoan types
Dùng int constants (`TYPE_I_OWE = 0`, `TYPE_LENT = 1`), không dùng enum.

---

## 🚀 Hướng phát triển

### Ngắn hạn (Priority)

- [ ] **Incremental Sync**: Thay vì reset toàn bộ sync status, chỉ push records thực sự mới/thay đổi
- [ ] **Conflict Resolution**: Xử lý trường hợp cùng 1 record bị sửa cả offline lẫn online
- [ ] **Error Handling cải thiện**: Hiện tại nhiều callback onFailure bỏ trống, cần log/retry
- [ ] **Pull-to-Refresh**: Cho phép user manual sync từ Dashboard
- [ ] **Recurring Transactions**: Backend đã có `isRecurring` + `recurInterval` nhưng Android chưa implement
- [ ] **Receipt Scanning**: `ScanReceiptFragment` + `ExtractInvoiceFragment` đã có shell, cần tích hợp OCR

### Trung hạn

- [ ] **Multi-currency Support**: Backend có `currencyCode` (default VND), cần mở rộng UI
- [ ] **Analytics Charts**: `AnalyticsFragment` hiện còn basic, cần biểu đồ (MPAndroidChart)
- [ ] **Budget Auto-Calculate**: Tự tính `spentAmount` từ transactions thay vì nhập tay
- [ ] **Export Data**: Xuất CSV/PDF báo cáo tài chính
- [ ] **Dark Mode**: Hỗ trợ theme tối
- [ ] **Notification System**: `NotificationBottomSheet` đã có, cần backend push notification
- [ ] **Profile Management**: `ProfileFragment` hiện còn skeleton

### Dài hạn

- [ ] **Cloud Deployment**: Deploy backend lên cloud thay vì localhost
- [ ] **Real-time Sync**: WebSocket/SSE thay vì polling
- [ ] **Multi-device**: Đồng bộ giữa nhiều thiết bị
- [ ] **Biometric Auth**: Fingerprint/FaceID
- [ ] **Widget**: Home screen widget hiển thị số dư
- [ ] **AI Insights**: Phân tích chi tiêu, gợi ý tiết kiệm

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
2. Đảm bảo backend đang chạy trên port 8080
3. Chạy trên emulator (base URL sử dụng `10.0.2.2`)

---

## 👥 Thông tin dự án

- **Tên**: TIP — Tap Into Prosperity
- **Package**: `vn.edu.usth.tip`
- **Tổ chức**: USTH (University of Science and Technology of Hanoi)
- **Ngôn ngữ giao diện**: Tiếng Việt
- **Đơn vị tiền tệ**: VNĐ (₫)
