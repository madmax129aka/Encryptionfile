package com.securevault.service;

import com.securevault.entity.FileMeta;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * Persists encrypted blobs using one of two backends, selected by the
 * {@code securevault.storage.backend} property:
 *
 *   "db"         -> ciphertext is stored in Postgres as BYTEA (FileMeta.encryptedBlob).
 *                   This is the DEFAULT and the ONLY safe choice on Render, whose
 *                   container filesystem is EPHEMERAL — any file written to disk is
 *                   WIPED on every redeploy/restart.
 *
 *   "filesystem" -> ciphertext is written to disk under securevault.storage.dir.
 *                   Fine for a purely local demo, but DO NOT use on Render: uploaded
 *                   files will silently disappear after a restart.
 *
 * The bytes handled here are ALWAYS ciphertext (AES-GCM output incl. auth tag)
 * produced in the browser. The server never sees plaintext or keys.
 */
@Service
public class StorageService {

    @Value("${securevault.storage.backend:db}")
    private String backend;

    @Value("${securevault.storage.dir:./uploads/encrypted}")
    private String storageDir;

    private boolean useDb;
    private Path root;

    @PostConstruct
    void init() {
        this.useDb = !"filesystem".equalsIgnoreCase(backend);
        if (useDb) {
            System.out.println("[SecureVault] Storage backend = DB (Postgres BYTEA). "
                    + "Render-safe: no dependency on ephemeral disk.");
        } else {
            this.root = Paths.get(storageDir).toAbsolutePath().normalize();
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not create storage dir: " + root, e);
            }
            System.out.println("[SecureVault] Storage backend = FILESYSTEM at " + root
                    + "  ⚠ WARNING: Render's disk is EPHEMERAL — files are wiped on redeploy/restart. "
                    + "Use STORAGE_BACKEND=db on Render.");
        }
    }

    /**
     * Store the uploaded ciphertext against the given metadata row. Depending on
     * the backend this either sets {@code meta.encryptedBlob} (DB) or writes a
     * file and sets {@code meta.storagePath} (filesystem).
     */
    public void store(MultipartFile encryptedBlob, FileMeta meta) {
        try {
            byte[] bytes = encryptedBlob.getBytes();
            if (useDb) {
                meta.setEncryptedBlob(bytes);
                meta.setStoragePath(null);
            } else {
                String name = UUID.randomUUID() + ".enc";
                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root)) {
                    throw new IllegalArgumentException("Invalid storage path");
                }
                Files.write(target, bytes);
                meta.setStoragePath(target.toString());
                meta.setEncryptedBlob(null);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store encrypted blob", e);
        }
    }

    /** Read the ciphertext back for download, from whichever backend holds it. */
    public byte[] read(FileMeta meta) {
        if (meta.getEncryptedBlob() != null) {
            return meta.getEncryptedBlob();
        }
        String storagePath = meta.getStoragePath();
        if (storagePath == null) {
            throw new IllegalStateException(
                    "No stored ciphertext for file " + meta.getId()
                    + " (likely lost to Render's ephemeral disk — re-upload it).");
        }
        Path p = Paths.get(storagePath).toAbsolutePath().normalize();
        if (root != null && !p.startsWith(root)) {
            throw new IllegalArgumentException("Refusing to read outside storage dir");
        }
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read encrypted blob", e);
        }
    }

    /** Remove any on-disk file for this row. DB blobs go away with the row itself. */
    public void delete(FileMeta meta) {
        String storagePath = meta.getStoragePath();
        if (storagePath == null) {
            return;
        }
        try {
            Path p = Paths.get(storagePath).toAbsolutePath().normalize();
            if (root == null || p.startsWith(root)) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            System.err.println("[SecureVault] Could not delete blob: " + storagePath);
        }
    }
}
