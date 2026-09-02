-- ============================================================
--  SecureVault — MySQL schema (metadata only)
--  This DDL is the canonical reference. In the default profile
--  Hibernate (ddl-auto=update) will also create/maintain these
--  tables automatically. Run this manually if you prefer.
--
--  IMPORTANT: this database NEVER stores plaintext, passphrases,
--  or derived keys — only ciphertext file paths + crypto metadata.
-- ============================================================

CREATE DATABASE IF NOT EXISTS securevault
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE securevault;

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,          -- BCrypt hash of the LOGIN password
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS files (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id           BIGINT NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  storage_path      VARCHAR(500) NOT NULL,       -- path to the .enc blob on disk
  salt_base64       VARCHAR(64) NOT NULL,        -- 16-byte PBKDF2 salt (base64)
  iv_base64         VARCHAR(64) NOT NULL,        -- 12-byte AES-GCM IV (base64)
  sha256_hash       VARCHAR(64) NOT NULL,        -- SHA-256 of the ORIGINAL plaintext (hex)
  file_size         BIGINT,
  uploaded_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_files_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS audit_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT,
  file_id     BIGINT,
  action      VARCHAR(20),                       -- UPLOAD, DOWNLOAD, DELETE
  detail      VARCHAR(255),                      -- e.g. filename (kept even after delete)
  action_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
