# 🛡️ SecureVault — Client-Server File Encryption System

A **zero-knowledge** file storage system. Files are encrypted **entirely in the
browser** (AES-256-GCM with a PBKDF2-derived key) *before* they are uploaded, so
the server and database only ever store **ciphertext + metadata** — never
plaintext, passphrases, or keys.

> Even if the server and MySQL database are fully compromised, stored files stay
> unreadable without the user's passphrase.

This project extends an earlier browser-only encryption client into a full
client-server system, and **fixes the "encrypt on one machine, decrypt only on
that same machine" problem**: the PBKDF2 **salt** and AES-GCM **IV** are now
persisted on the server alongside the ciphertext, so any machine can re-derive
the identical key from the same passphrase and decrypt.

---

## Architecture

```
┌────────────────────────────┐         HTTPS/HTTP          ┌───────────────────────────┐
│         Browser Client       │  ──────────────────────▶  │      Spring Boot API        │
│  (index.html — Web Crypto)   │   ciphertext + salt + iv  │  /api/auth  /api/files      │
│                              │   + sha256 + filename     │  /api/audit                 │
│  PBKDF2 → AES-256-GCM        │  ◀──────────────────────  │                             │
│  encrypt / decrypt LOCALLY   │   ciphertext + salt + iv  │   BCrypt login (session)    │
└────────────────────────────┘                             └────────────┬──────────────┘
        ▲ passphrase never leaves the browser                           │
        │                                                    ┌───────────┴───────────┐
   plaintext only exists                                     │  MySQL (metadata only) │
   in the user's browser                                     │  users / files / audit │
                                                             ├────────────────────────┤
                                                             │  Disk: /uploads/*.enc   │
                                                             │  (ciphertext blobs)     │
                                                             └────────────────────────┘
```

- **Client:** plain HTML/CSS/JS, Web Crypto API (`crypto.subtle`). No build step.
- **Backend:** Java 17, Spring Boot 3.3.x, Spring Web, Spring Data JPA, Spring Security.
- **Database:** MySQL 8 (metadata only). An H2 in-memory profile is included for zero-setup demos.
- **File storage:** encrypted blobs on local disk (`server/uploads/encrypted/<uuid>.enc`), referenced by path in MySQL.

---

## Quick start

### Option A — Zero-setup demo (in-memory H2, no database install)

```bash
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

Then open **http://localhost:8080** in your browser. Data resets when the app stops.

### Option B — Full stack with MySQL (as per the brief)

1. **Create the database** (schema is also auto-created by Hibernate):
   ```bash
   mysql -u root -p < server/src/main/resources/schema.sql
   ```
2. **Configure the connection** in `server/src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/securevault?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
   spring.datasource.username=root
   spring.datasource.password=YOUR_PASSWORD
   ```
3. **Run the server:**
   ```bash
   cd server
   mvn spring-boot:run
   ```
4. Open **http://localhost:8080**.

### Opening the client separately

The client is served by Spring at `/` (from `static/index.html`), which keeps it
**same-origin** so the session cookie just works. If you'd rather open the HTML
file directly from disk, use `securevault-client.html` at the repo root — it sets
`const API = "http://localhost:8080"` and relies on the server's CORS config.

### Demo account

A demo user is seeded on startup so evaluators can log in instantly:

```
username: demo
password: Demo@1234
```

---

## End-to-end test (the cross-machine flow)

1. Log in as `demo` / `Demo@1234`.
2. Click **＋ Encrypt & Upload**, choose a `.txt` file, enter a passphrase (e.g. `hunter2`), upload.
3. Log out. Open the app **in a different browser or on another machine**, log in as `demo`.
4. Click **🔓 Decrypt** on the file, enter the **same passphrase**.
5. The file downloads and shows a green **✅ Integrity Verified** badge — proving
   the key was re-derived correctly on a different machine and the SHA-256 matches.
6. Try a **wrong passphrase** → red **❌ Tampered / wrong passphrase** (GCM auth tag rejects it).

---

## REST API

| Method | Path                       | Auth | Body / Notes |
|--------|----------------------------|------|--------------|
| POST   | `/api/auth/register`       | no   | `{username, password}` |
| POST   | `/api/auth/login`          | no   | `{username, password}` → sets session cookie |
| POST   | `/api/auth/logout`         | yes  | — |
| GET    | `/api/auth/me`             | yes  | current user (used to restore session) |
| POST   | `/api/files/upload`        | yes  | multipart: `file` (ciphertext blob), `salt`, `iv`, `sha256Hash`, `originalFilename`, `originalSize` |
| GET    | `/api/files`               | yes  | list current user's files (metadata) |
| GET    | `/api/files/{id}/download` | yes  | JSON: `ciphertextBase64`, `saltBase64`, `ivBase64`, `sha256Hash`, `originalFilename` |
| DELETE | `/api/files/{id}`          | yes  | deletes blob + DB row, logs to audit |
| GET    | `/api/audit`               | yes  | current user's audit entries |

### curl example

```bash
# login (store the session cookie)
curl -c cookies.txt -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Demo@1234"}' \
  http://localhost:8080/api/auth/login

# list files
curl -b cookies.txt http://localhost:8080/api/files
```

---

## HTTPS / TLS (why it still matters)

The file *payloads* are already end-to-end encrypted, so why add TLS?

- **Metadata protection.** Filenames, file sizes, usernames and the salt/IV travel
  in the clear over plain HTTP. TLS hides them from a network eavesdropper.
- **Login credentials.** The BCrypt-verified login password is sent to the server
  on `/api/auth/login`. Without TLS it is exposed on the wire.
- **Session cookie theft.** The `JSESSIONID` cookie could be sniffed and replayed.
- **MITM ciphertext substitution.** Without TLS, an attacker could swap the stored
  ciphertext for their own. GCM would then fail to decrypt (good — no data leak),
  but TLS prevents the tampering/denial-of-service in the first place.

### Local self-signed cert for a demo

```bash
keytool -genkeypair -alias securevault -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore keystore.p12 -validity 365 \
  -dname "CN=localhost" -storepass changeit
```

Add to `application.properties`:

```properties
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-type=PKCS12
server.ssl.key-store-password=changeit
server.ssl.key-alias=securevault
```

Then browse to `https://localhost:8080` (accept the self-signed warning) and set
`const API = "https://localhost:8080"` in the standalone client if used.

---

## Project layout

```
Encryptionfile/
├── README.md
├── WRITEUP.md                     # 1-page report: objective, design, security analysis
├── securevault-client.html        # standalone client (API=http://localhost:8080)
├── securedrop.html                # original browser-only P2P reference (kept for history)
└── server/
    ├── pom.xml
    └── src/main/
        ├── java/com/securevault/
        │   ├── SecureVaultApplication.java
        │   ├── config/            # SecurityConfig, DemoDataSeeder
        │   ├── entity/            # User, FileMeta, AuditLog
        │   ├── repository/        # Spring Data JPA repositories
        │   ├── service/           # StorageService, AuditService, UserService
        │   └── web/               # AuthController, FileController, AuditController, DTOs
        └── resources/
            ├── application.properties        # MySQL (default)
            ├── application-h2.properties      # in-memory demo profile
            ├── schema.sql                     # canonical MySQL DDL
            └── static/index.html              # the SecureVault dashboard (served at /)
```

---

## Security notes / limitations

See **[WRITEUP.md](WRITEUP.md)** for the full analysis. In short:

- **Strengths:** zero-knowledge server, per-file random salt + IV, PBKDF2 (150k iters,
  SHA-256), AES-256-GCM authenticated encryption (built-in tamper detection),
  independent SHA-256 integrity check, per-user file scoping, BCrypt login hashes.
- **Limitations:** security depends on passphrase strength; **no key recovery** if a
  passphrase is forgotten (by design); single-server storage with no redundancy;
  CSRF disabled for this prototype (documented trade-off); demo CORS is permissive.

## Future improvements (viva)

MFA on login · passphrase recovery via Shamir's Secret Sharing · encrypted cloud
storage (S3) · chunked encryption for very large files · native desktop/CLI client
using the same scheme · rate limiting / brute-force protection on login.
