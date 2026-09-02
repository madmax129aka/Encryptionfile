package com.securevault.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * Stores encrypted blobs on the local filesystem. The bytes written here are
 * ALWAYS ciphertext (salt + IV + AES-GCM ciphertext+tag) produced in the
 * browser — the server never decrypts them.
 */
@Service
public class StorageService {

    @Value("${securevault.storage.dir:./uploads/encrypted}")
    private String storageDir;

    private Path root;

    @PostConstruct
    void init() {
        this.root = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create storage dir: " + root, e);
        }
    }

    /** Persist the uploaded ciphertext under a random UUID; return the path stored in DB. */
    public String store(MultipartFile encryptedBlob) {
        String name = UUID.randomUUID() + ".enc";
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        try {
            Files.copy(encryptedBlob.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store encrypted blob", e);
        }
        return target.toString();
    }

    public byte[] read(String storagePath) {
        Path p = Paths.get(storagePath).toAbsolutePath().normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Refusing to read outside storage dir");
        }
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read encrypted blob", e);
        }
    }

    public void delete(String storagePath) {
        try {
            Path p = Paths.get(storagePath).toAbsolutePath().normalize();
            if (p.startsWith(root)) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            // Non-fatal: DB row removal is what matters most for the user.
            System.err.println("[SecureVault] Could not delete blob: " + storagePath);
        }
    }
}
