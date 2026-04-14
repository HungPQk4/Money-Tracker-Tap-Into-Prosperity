-- Kích hoạt extension cho UUID nếu chưa có
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Bảng users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    avatar_url TEXT,
    currency_code CHAR(3) NOT NULL DEFAULT 'VND',
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bảng accounts
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    balance DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    currency_code CHAR(3) NOT NULL DEFAULT 'VND',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bảng categories
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    parent_id UUID REFERENCES categories(id),
    name VARCHAR(100) NOT NULL,
    type VARCHAR(10) NOT NULL, -- income, expense
    icon VARCHAR(50),
    color_hex CHAR(7),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bảng goals
CREATE TABLE goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    target_amount DECIMAL(18, 2) NOT NULL,
    current_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    deadline DATE,
    status VARCHAR(10) NOT NULL DEFAULT 'active',
    icon VARCHAR(50),
    color_hex CHAR(7),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bảng budgets
CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    amount DECIMAL(18, 2) NOT NULL,
    period_type VARCHAR(10) NOT NULL, -- monthly, yearly...
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    alert_threshold SMALLINT NOT NULL DEFAULT 80,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bảng transactions
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    goal_id UUID REFERENCES goals(id),
    amount DECIMAL(18, 2) NOT NULL,
    type VARCHAR(10) NOT NULL, -- income, expense, transfer
    note TEXT,
    transaction_date DATE NOT NULL,
    receipt_url TEXT,
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    recur_interval VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bảng debts
CREATE TABLE debts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_name VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(20),
    type VARCHAR(10) NOT NULL, -- lend, borrow
    amount DECIMAL(18, 2) NOT NULL,
    remaining_amount DECIMAL(18, 2) NOT NULL,
    due_date DATE,
    status VARCHAR(10) NOT NULL DEFAULT 'active',
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
