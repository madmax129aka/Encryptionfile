# 🛡️ SecureVault — Client-Server File Encryption System

A **zero-knowledge** file storage system. Files are encrypted **entirely in the
browser** (AES-256-GCM with a PBKDF2-derived key) *before* they are uploaded, so
the server and database only ever store **ciphertext + metadata** — never
plaintext, passphrases, or keys.

> Even if the server and database are fully compromised, stored files stay
> unreadable without the user's passphrase.

> **Deploying on Render?** Jump to [Deploying on Render](#deploying-on-render). The
> backend runs as a **Web Service**, the client as a **Static Site**, backed by
> Render's free managed **PostgreSQL**.

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
        │                                                    ┌───────────┴────────────┐
   plaintext only exists                                     │  PostgreSQL             │
   in the user's browser                                     │  users / files / audit  │
                                                             │  ciphertext as BYTEA    │
                                                             │  (Render-safe default)  │
                                                             └─────────────────────────┘
```

- **Client:** plain HTML/CSS/JS, Web Crypto API (`crypto.subtle`). No build step.
- **Backend:** Java 17, Spring Boot 3.3.x, Spring Web, Spring Data JPA, Spring Security.
- **Database:** PostgreSQL (metadata only). An H2 in-memory profile is included for zero-setup demos.
- **File storage:** two backends selectable via `STORAGE_BACKEND`:
  - `db` (**default, Render-safe**) — ciphertext stored in Postgres as `BYTEA`.
  - `filesystem` — ciphertext on local disk (`server/uploads/encrypted/<uuid>.enc`).
    ⚠️ **Do not use on Render** — its disk is ephemeral and wiped on every redeploy/restart.

---

## Quick start

### Option A — Zero-setup demo (in-memory H2, no database install)

```bash
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

Then open **http://localhost:8080** in your browser. Data resets when the app stops.

### Option B — Full stack with local PostgreSQL

1. **Create a database and user** (once):
   ```bash
   createdb securevault           # or: psql -c "CREATE DATABASE securevault;"
   ```
   The tables are auto-created by Hibernate. To create them manually, run the
   Postgres DDL:
   ```bash
   psql -d securevault -f server/src/main/resources/schema.sql
   ```
2. **Point the app at your Postgres** using environment variables (no hardcoded
   values in the code — everything is externalized):
   ```bash
   export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/securevault"
   export SPRING_DATASOURCE_USERNAME="postgres"
   export SPRING_DATASOURCE_PASSWORD="postgres"
   # local HTTP: relax the cross-site cookie so it works without HTTPS
   export COOKIE_SAMESITE=Lax
   export COOKIE_SECURE=false
   ```
   (Or edit the defaults in `server/src/main/resources/application.properties`.)
3. **Run the server:**
   ```bash
   cd server
   mvn spring-boot:run
   ```
4. Open **http://localhost:8080**.

### Opening the client separately

The client is served by Spring at `/` (from `static/index.html`), which keeps it
**same-origin** so the session cookie just works. If you'd rather open the HTML
file directly from disk (or host it elsewhere, like a Render Static Site), use
`securevault-client.html` at the repo root and set the single config line near
the top: `const API_BASE_URL = "http://localhost:8080";` (or your deployed
backend URL). Make sure the backend's `ALLOWED_ORIGIN` includes that origin.

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

## Deploying on Render

The app deploys as **two services + one database**: a Spring Boot **Web Service**
(backend), a **Static Site** (frontend), and a managed **PostgreSQL** instance.
All configuration is via environment variables — there are no hardcoded localhost
values in the backend.

### Environment variables (backend Web Service)

| Variable          | Required | Example / default | Purpose |
|-------------------|----------|-------------------|---------|
| `DATABASE_URL`    | yes (Render injects) | `postgresql://user:pass@host:5432/db` | Render's Postgres URL. Parsed automatically into a JDBC URL by `DatabaseUrlConfig` (adds `sslmode=require`). |
| `ALLOWED_ORIGIN`  | **yes** | `https://securevault-frontend.onrender.com` | CORS: the frontend Static Site URL. Comma-separate multiple origins. |
| `STORAGE_BACKEND` | no | `db` (default) | `db` = ciphertext in Postgres BYTEA (**use this on Render**). `filesystem` = local disk (ephemeral, will lose files). |
| `COOKIE_SAMESITE` | no | `None` (default) | Cross-site session cookie. Keep `None` on Render (different domains). |
| `COOKIE_SECURE`   | no | `true` (default) | Send cookie only over HTTPS. Keep `true` on Render. |
| `SEED_DEMO_USER`  | no | `true` (default) | Seeds `demo` / `Demo@1234`. |
| `PORT`            | no (Render injects) | `10000` | Server port. The app already binds `0.0.0.0:${PORT}`. |

> You can also override the DB with `SPRING_DATASOURCE_URL` /
> `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` if you prefer not to
> use `DATABASE_URL`.

### Option 1 — One-click Blueprint (recommended)

This repo includes **`render.yaml`**, which provisions the database, backend, and
frontend together.

1. On the Render dashboard: **New → Blueprint**, connect this GitHub repo, and apply.
2. Render creates `securevault-db`, `securevault-backend`, and `securevault-frontend`.
   `DATABASE_URL` is wired to the backend automatically.
3. When the **frontend** finishes deploying, copy its URL
   (e.g. `https://securevault-frontend.onrender.com`) and:
   - Set the backend's **`ALLOWED_ORIGIN`** env var to that URL → the backend redeploys.
   - Edit **`API_BASE_URL`** at the top of `securevault-client.html` to the **backend**
     URL (e.g. `https://securevault-backend.onrender.com`), commit & push → the
     Static Site redeploys.
4. Open the frontend URL and log in with `demo` / `Demo@1234`.

### Option 2 — Manual setup

**Database:** New → **PostgreSQL** (free). Note its **Internal Connection String**.

**Backend:** New → **Web Service**, from this repo.
- **Runtime:** **Docker** (Render has no native Java runtime — the backend ships a
  `server/Dockerfile` that builds the jar with Maven and runs it on a JRE).
- **Dockerfile Path:** `./server/Dockerfile`
- **Docker Build Context Directory:** `./server`
- **Health Check Path:** `/api/health`
- **Env vars:** `DATABASE_URL` (paste the connection string), `STORAGE_BACKEND=db`,
  `COOKIE_SAMESITE=None`, `COOKIE_SECURE=true`, and (after the frontend exists)
  `ALLOWED_ORIGIN=<your static-site URL>`.
- No build/start commands needed — the Dockerfile handles both. `PORT` is injected
  by Render and read by the app automatically.

**Frontend:** New → **Static Site**, from this repo.
- **Build Command:** *(leave empty — no build step)*
- **Publish Directory:** `.` (repo root)
- Add a **Rewrite Rule** `/` → `/securevault-client.html` (or just visit
  `.../securevault-client.html`).
- Edit `API_BASE_URL` in `securevault-client.html` to the backend URL, then commit.

### The one line to change in the frontend

At the very top of the `<script>` in **`securevault-client.html`**:

```js
const API_BASE_URL = "https://securevault-backend.onrender.com"; // ← your backend URL
```

That is the only frontend edit needed to point the deployed client at the deployed
backend. The encryption/decryption logic (PBKDF2 salt + IV handling, AES-256-GCM)
is untouched.

### Note on Render's free tier

- **Ephemeral disk:** anything written to the container filesystem is wiped on
  redeploy/restart. Keeping `STORAGE_BACKEND=db` avoids this by storing ciphertext
  in Postgres. This is well-suited to small/demo-scale files.
- **Cold starts:** free Web Services sleep after inactivity; the first request may
  take ~30–60s to wake.
- **Free Postgres expiry:** Render's free databases have a limited lifetime — for a
  long-lived demo, plan to recreate or upgrade.

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
`const API_BASE_URL = "https://localhost:8080"` in the standalone client if used.
(On Render you don't need this — TLS is provided automatically.)

---

## Project layout

```
Encryptionfile/
├── README.md
├── WRITEUP.md                     # 1-page report: objective, design, security analysis
├── render.yaml                    # Render Blueprint: db + backend + frontend
├── securevault-client.html        # standalone client (set API_BASE_URL) — the Render Static Site
├── securedrop.html                # original browser-only P2P reference (kept for history)
└── server/
    ├── pom.xml                    # PostgreSQL driver
    ├── Dockerfile                 # multi-stage build for Render's Docker runtime
    ├── .dockerignore
    └── src/main/
        ├── java/com/securevault/
        │   ├── SecureVaultApplication.java
        │   ├── config/            # SecurityConfig, DemoDataSeeder, DatabaseUrlConfig
        │   ├── entity/            # User, FileMeta (BYTEA blob), AuditLog
        │   ├── repository/        # Spring Data JPA repositories
        │   ├── service/           # StorageService (db|filesystem), AuditService, UserService
        │   └── web/               # AuthController, FileController, AuditController, HealthController, DTOs
        └── resources/
            ├── application.properties        # PostgreSQL (env-var driven, Render-ready)
            ├── application-h2.properties      # in-memory demo profile
            ├── schema.sql                     # canonical PostgreSQL DDL
            ├── META-INF/spring.factories      # registers DatabaseUrlConfig
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
