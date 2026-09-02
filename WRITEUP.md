# SecureVault — Project Write-up

## 1. Objective

Build a **zero-knowledge encrypted file storage** system for a cybersecurity
mini-project. Files must be encrypted in the browser before upload so that the
server and database can be fully compromised without exposing file contents. The
system must also solve a specific defect from the earlier prototype: a file
encrypted on one computer could not be decrypted on another.

## 2. Architecture

A thin **browser client** performs all cryptography using the Web Crypto API. A
**Spring Boot** REST API handles authentication and stores encrypted blobs, and
keeps **metadata only** in **PostgreSQL**. The trust boundary is the browser:
plaintext and the passphrase never cross it. Deployed as two Render services
(backend Web Service + frontend Static Site) with managed Postgres.

```
Browser (encrypt/decrypt)  ──ciphertext+salt+iv+hash──▶  Spring Boot  ──▶  PostgreSQL (metadata
                                                                      │      + ciphertext BYTEA)
                                                                      └──▶  Disk (*.enc blobs, optional)
```

- **Client:** HTML/CSS/JS + `crypto.subtle`.
- **Server:** Java 17, Spring Boot 3.3, Spring Web/Data JPA/Security, BCrypt, session auth.
- **DB:** PostgreSQL — `users`, `files`, `audit_log`. No plaintext, passphrases, or keys stored.
- **Blobs:** stored in Postgres as `BYTEA` by default (Render-safe), or on disk when configured.

## 3. Algorithms & crypto design

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Key derivation | **PBKDF2-HMAC-SHA-256, 150,000 iterations** | Stretches a human passphrase into a 256-bit key; high iteration count slows brute force. |
| Salt | **16 random bytes per file** | Prevents precomputed/rainbow attacks; makes identical passphrases yield distinct keys. |
| Symmetric cipher | **AES-256-GCM** | Authenticated encryption: confidentiality **and** integrity in one primitive. |
| IV / nonce | **12 random bytes per encryption** | GCM-recommended nonce size; unique per operation. |
| Integrity | GCM auth tag **+** independent **SHA-256** of the original plaintext | GCM detects ciphertext tampering; SHA-256 confirms the decrypted bytes equal the original. |
| Login secret | **BCrypt** password hash | Server-side login only; unrelated to the file passphrase. |

### The cross-machine fix

The root cause of "decrypt only on the same machine" was that the **salt** (and
IV) were not durably associated with the ciphertext, so a second machine derived
a *different* key from the same passphrase. SecureVault stores the base64 salt
and IV in the `files` table and returns them on download. Any machine therefore
runs `PBKDF2(passphrase, storedSalt)` → the **identical** AES key → successful
decryption. Verified: encrypt on machine A, decrypt on machine B with only the
stored salt/IV + passphrase reproduces the original and passes the SHA-256 check.

## 4. Data flow

- **Upload:** read file → `SHA-256(plaintext)` → generate salt+IV → PBKDF2 → AES-GCM encrypt → POST `blob + salt + iv + hash + filename`. Server saves the blob and a metadata row; logs `UPLOAD`.
- **Download:** GET returns `ciphertext + salt + iv + hash` → client re-derives key → AES-GCM decrypt → recompute SHA-256 and compare → browser download + integrity badge; server logs `DOWNLOAD`.

## 5. Security analysis

**Strengths**
- **Zero-knowledge server:** compromise of the database or disk yields only ciphertext + metadata.
- **Authenticated encryption:** any bit-flip in the stored ciphertext makes GCM decryption fail loudly (❌ Tampered), so silent corruption/tampering is impossible.
- **Per-file salt & IV:** no key/nonce reuse across files.
- **Defence in depth on integrity:** GCM tag *and* a separate SHA-256 comparison.
- **Least authority:** files are scoped per authenticated user; path-traversal guarded on storage.
- **Auditability:** every UPLOAD/DOWNLOAD/DELETE is timestamped per user.

**Limitations / trade-offs**
- **Passphrase strength is the ceiling.** A weak passphrase can be brute-forced offline if the ciphertext is stolen; PBKDF2 iterations only slow this.
- **No key recovery.** Forgetting the passphrase means permanent data loss — an intentional consequence of zero-knowledge design.
- **Single-server storage, no redundancy.** Disk loss loses the ciphertext.
- **Prototype auth hardening.** CSRF is disabled and CORS is permissive for the demo; production needs CSRF tokens (or token auth), a strict CORS allow-list, HTTPS, and login rate limiting.
- **Metadata leakage without TLS.** Filenames, sizes, salt/IV and the login password need TLS to stay private in transit.

## 6. Future work

Multi-factor authentication; passphrase recovery via Shamir's Secret Sharing;
encrypted cloud storage (S3, server-side encryption off since data is already
client-encrypted); chunked encryption for very large files; a native desktop/CLI
client sharing the scheme; brute-force protection and rate limiting on login and
passphrase attempts.
