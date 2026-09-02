package com.securevault.web.dto;

import com.securevault.entity.AuditLog;
import com.securevault.entity.FileMeta;

import java.time.Instant;

/** Response payloads for file + audit endpoints. */
public final class FileDtos {

    private FileDtos() {
    }

    /** Row shown in the dashboard file list. */
    public record FileListItem(
            Long id,
            String originalFilename,
            Long fileSize,
            String sha256Hash,
            Instant uploadedAt
    ) {
        public static FileListItem from(FileMeta f) {
            return new FileListItem(f.getId(), f.getOriginalFilename(),
                    f.getFileSize(), f.getSha256Hash(), f.getUploadedAt());
        }
    }

    /**
     * Everything the client needs to decrypt: base64 ciphertext plus the salt,
     * IV and original hash. The client re-derives the key from the passphrase +
     * this salt, so decryption works on ANY machine.
     */
    public record DownloadResponse(
            Long id,
            String originalFilename,
            String saltBase64,
            String ivBase64,
            String sha256Hash,
            String ciphertextBase64
    ) {
    }

    public record AuditItem(
            Long id,
            Long fileId,
            String action,
            String detail,
            Instant actionTime
    ) {
        public static AuditItem from(AuditLog a) {
            return new AuditItem(a.getId(), a.getFileId(), a.getAction(),
                    a.getDetail(), a.getActionTime());
        }
    }
}
