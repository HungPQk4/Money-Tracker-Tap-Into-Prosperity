-- Migration: thêm updated_at, deleted_at vào categories và deleted_at vào transactions
-- Chạy 1 lần trên Neon DB để đồng bộ với entity model

ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
