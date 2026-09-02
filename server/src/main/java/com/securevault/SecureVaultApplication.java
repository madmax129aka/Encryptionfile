package com.securevault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SecureVault — zero-knowledge encrypted file storage.
 *
 * The server only ever sees ciphertext + metadata. All encryption/decryption
 * happens in the browser via the Web Crypto API (AES-256-GCM, PBKDF2).
 */
@SpringBootApplication
public class SecureVaultApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecureVaultApplication.class, args);
    }
}
