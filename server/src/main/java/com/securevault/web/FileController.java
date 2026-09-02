package com.securevault.web;

import com.securevault.entity.FileMeta;
import com.securevault.entity.User;
import com.securevault.repository.FileMetaRepository;
import com.securevault.service.AuditService;
import com.securevault.service.StorageService;
import com.securevault.service.UserService;
import com.securevault.web.dto.FileDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * File endpoints. Every payload here is ciphertext + metadata — the server
 * has no way to read the user's files, which is the whole point (zero-knowledge).
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final int MAX_B64_FIELD = 64; // matches DB column length

    private final FileMetaRepository files;
    private final StorageService storage;
    private final AuditService audit;
    private final UserService userService;

    public FileController(FileMetaRepository files,
                          StorageService storage,
                          AuditService audit,
                          UserService userService) {
        this.files = files;
        this.storage = storage;
        this.audit = audit;
        this.userService = userService;
    }

    private User currentUser(Authentication auth) {
        return userService.requireByUsername(auth.getName());
    }

    /**
     * Upload an encrypted file. The blob is raw ciphertext (AES-GCM output,
     * including the auth tag). salt/iv/hash are supplied as separate fields.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(Authentication auth,
                                    @RequestParam("file") MultipartFile encryptedBlob,
                                    @RequestParam("salt") String saltBase64,
                                    @RequestParam("iv") String ivBase64,
                                    @RequestParam("sha256Hash") String sha256Hash,
                                    @RequestParam("originalFilename") String originalFilename,
                                    @RequestParam(value = "originalSize", required = false) Long originalSize) {
        if (encryptedBlob == null || encryptedBlob.isEmpty()) {
            return bad("Encrypted blob is missing or empty");
        }
        if (isBlank(saltBase64) || saltBase64.length() > MAX_B64_FIELD) {
            return bad("Invalid salt");
        }
        if (isBlank(ivBase64) || ivBase64.length() > MAX_B64_FIELD) {
            return bad("Invalid IV");
        }
        if (sha256Hash == null || !sha256Hash.matches("^[a-fA-F0-9]{64}$")) {
            return bad("sha256Hash must be a 64-char hex string");
        }
        if (isBlank(originalFilename)) {
            return bad("originalFilename is required");
        }

        User user = currentUser(auth);
        String path = storage.store(encryptedBlob);

        FileMeta meta = new FileMeta();
        meta.setUserId(user.getId());
        meta.setOriginalFilename(sanitizeName(originalFilename));
        meta.setStoragePath(path);
        meta.setSaltBase64(saltBase64);
        meta.setIvBase64(ivBase64);
        meta.setSha256Hash(sha256Hash.toLowerCase());
        meta.setFileSize(originalSize != null ? originalSize : encryptedBlob.getSize());
        meta = files.save(meta);

        audit.log(user.getId(), meta.getId(), "UPLOAD", meta.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.CREATED).body(FileListItem.from(meta));
    }

    /** List the current user's files (metadata only). */
    @GetMapping
    public List<FileListItem> list(Authentication auth) {
        User user = currentUser(auth);
        return files.findByUserIdOrderByUploadedAtDesc(user.getId())
                .stream().map(FileListItem::from).toList();
    }

    /** Return ciphertext + salt + IV so the client can decrypt locally. */
    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(Authentication auth, @PathVariable Long id) {
        User user = currentUser(auth);
        FileMeta meta = files.findByIdAndUserId(id, user.getId()).orElse(null);
        if (meta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "File not found"));
        }
        byte[] ciphertext = storage.read(meta.getStoragePath());
        audit.log(user.getId(), meta.getId(), "DOWNLOAD", meta.getOriginalFilename());

        return ResponseEntity.ok(new DownloadResponse(
                meta.getId(),
                meta.getOriginalFilename(),
                meta.getSaltBase64(),
                meta.getIvBase64(),
                meta.getSha256Hash(),
                Base64.getEncoder().encodeToString(ciphertext)
        ));
    }

    /** Delete file blob + DB row (audit entry kept for the record). */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(Authentication auth, @PathVariable Long id) {
        User user = currentUser(auth);
        FileMeta meta = files.findByIdAndUserId(id, user.getId()).orElse(null);
        if (meta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "File not found"));
        }
        storage.delete(meta.getStoragePath());
        files.delete(meta);
        audit.log(user.getId(), id, "DELETE", meta.getOriginalFilename());
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }

    // ── helpers ───────────────────────────────────────────────
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ResponseEntity<?> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    /** Strip any path components a client might sneak into the filename. */
    private static String sanitizeName(String name) {
        String cleaned = name.replaceAll("[\\r\\n]", "").trim();
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        if (cleaned.isEmpty()) {
            cleaned = "file";
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }
}
