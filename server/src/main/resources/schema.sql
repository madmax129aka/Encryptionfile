-- ============================================================
--  SecureVault — PostgreSQL schema (metadata only)
--  Canonical reference DDL. In the default profile Hibernate
--  (ddl-auto=update) also creates/maintains these tables.
--
--  On Render: create the database via the dashboard (managed
--  Postgres). Do NOT run CREATE DATABASE here — connect to the
--  database Render provisioned and run the CREATE TABLE blocks.
--
--  IMPORTANT: this database NEVER stores plaintext, passphrases,
--  or derived keys — only ciphertext (+ optional BYTEA blob) and
--  crypto metadata (salt, IV, SHA-256).
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
  id            BIGSERIAL PRIMARY KEY,
  username      VARCHAR(50) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,          -- BCrypt hash of the LOGIN password
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS files (
  id                BIGSERIAL PRIMARY KEY,
  user_id           BIGINT NOT NULL REFERENCES users(id),
  original_filename VARCHAR(255) NOT NULL,
  storage_path      VARCHAR(500),                -- disk path when STORAGE_BACKEND=filesystem
  encrypted_blob    BYTEA,                        -- ciphertext when STORAGE_BACKEND=db (Render default)
  salt_base64       VARCHAR(64) NOT NULL,        -- 16-byte PBKDF2 salt (base64)
  iv_base64         VARCHAR(64) NOT NULL,        -- 12-byte AES-GCM IV (base64)
  sha256_hash       VARCHAR(64) NOT NULL,        -- SHA-256 of the ORIGINAL plaintext (hex)
  file_size         BIGINT,
  uploaded_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT,
  file_id     BIGINT,
  action      VARCHAR(20),                       -- UPLOAD, DOWNLOAD, DELETE
  detail      VARCHAR(255),                      -- e.g. filename (kept even after delete)
  action_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
