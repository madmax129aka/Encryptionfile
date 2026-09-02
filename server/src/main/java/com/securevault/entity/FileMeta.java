package com.securevault.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Metadata for one encrypted file.
 *
 * IMPORTANT: this table never contains plaintext, passphrases, or derived keys.
 * The salt + IV are stored so ANY machine can re-derive the same key from the
 * user's passphrase — this is exactly what makes cross-machine decryption work.
 * The encrypted bytes themselves live on disk at {@code storagePath}.
 */
@Entity
@Table(name = "files")
public class FileMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /** Base64 of the 16-byte PBKDF2 salt used during encryption. */
    @Column(name = "salt_base64", nullable = false, length = 64)
    private String saltBase64;

    /** Base64 of the 12-byte AES-GCM IV used during encryption. */
    @Column(name = "iv_base64", nullable = false, length = 64)
    private String ivBase64;

    /** Hex SHA-256 of the ORIGINAL plaintext, for post-decrypt integrity check. */
    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    public FileMeta() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getSaltBase64() {
        return saltBase64;
    }

    public void setSaltBase64(String saltBase64) {
        this.saltBase64 = saltBase64;
    }

    public String getIvBase64() {
        return ivBase64;
    }

    public void setIvBase64(String ivBase64) {
        this.ivBase64 = ivBase64;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
