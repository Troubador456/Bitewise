# Bitewise

Bitewise helps people review packaged foods against their allergies and dietary preferences. Scan a product barcode, upload a barcode image, or use the manual ingredient checker as an alternative.

> **Important:** Bitewise is an informational aid, not a medical device or food-safety certification. Product information may be incomplete or out of date, and ingredient matching can miss synonyms, derivatives, and cross-contact warnings. Always read the package label and contact the manufacturer when needed, especially for severe allergies.

## Features

- **Accounts:** sign up, verify an email address, sign in, refresh a session, and reset or change a password.
- **Food profiles:** create multiple profiles with allergy tags and dietary restrictions, then choose which profile to use for a check.
- **Barcode lookup:** scan with a camera or upload an image containing a barcode. Product data comes from Open Food Facts.
- **Manual ingredient check:** an optional alternative for products that cannot be found by barcode or when you want to check copied label text.
- **Product library and history:** save products to your account and view recent barcode and ingredient checks.
- **Private account data:** profiles, saved products, and scan history are associated with the signed-in user.
- **Responsive browser interface:** the frontend is plain HTML, CSS, and JavaScript; no frontend package installation is required.

Barcode and product details depend on Open Food Facts' community-contributed data. A barcode can decode successfully while its product is missing from that database. A `NO_MATCH_FOUND` or similar result does **not** mean a product is safe.

## How it works

1. Create an account and verify it using the email sent by the app.
2. Create a food profile with the allergy tags and dietary restrictions you want to check.
3. Scan a barcode with the camera or upload a clear barcode image. The backend decodes uploaded images and looks up the product in Open Food Facts.
4. Bitewise compares available product information with the selected profile and displays potential matches and uncertainty notes.
5. If the product cannot be looked up, expand **Enter ingredients manually (alternative)** and check text copied from the package.

The manual checker uses ingredient text and a limited keyword list. Barcode-based checks use allergen tags supplied by Open Food Facts. Neither method can guarantee that a product is safe.

## Technology

- **Frontend:** HTML, CSS, and browser JavaScript
- **Backend:** Java 21, Spring Boot 3.5, Spring Security, and Spring Data JPA
- **Persistence:** PostgreSQL, with Flyway-managed database migrations
- **Barcode decoding:** ZXing
- **Product catalog:** Open Food Facts
- **Local email testing:** Mailpit
- **Deployment:** Docker web service on Render; the Blueprint expects a separate PostgreSQL database

In production the frontend files are packaged into the Spring Boot application and served from the same origin as the API. This keeps the browser UI, API requests, and refresh cookie on one origin.

## Repository layout

```text
.
├── index.html                 # Landing page
├── signin.html                # Sign-in page
├── createaccount.html         # Account registration page
├── home.html                  # Authenticated product-checking dashboard
├── api-config.js              # Selects the local or same-origin API URL
├── auth.js                    # Browser authentication helpers
├── assets/
│   └── bitewise-logo.png
├── Dockerfile                 # Multi-stage production image
├── render.yaml                # Render Blueprint for the web service
└── food-safety-backend/
    ├── compose.yaml           # Local PostgreSQL and Mailpit
    ├── pom.xml
    ├── READMEBackend.md       # Backend, API, and deployment details
    └── src/
        ├── main/java/         # Spring Boot API
        ├── main/resources/    # Configuration and Flyway migrations
        └── test/java/         # Backend tests
```

## Requirements

- Java 21
- Maven 3.9 or later
- Docker Desktop with Docker Compose (for local PostgreSQL and Mailpit)
- A modern browser

## Run locally

The commands below assume PowerShell and are run from the repository root.

### 1. Start the local services

```powershell
Set-Location food-safety-backend
docker compose up -d
```

Compose starts PostgreSQL on port `5432` and Mailpit on port `1025`. The Mailpit web inbox is available at [http://localhost:8025](http://localhost:8025).

### 2. Configure and start the backend

In the same PowerShell window, configure the local email settings and a temporary JWT signing secret, then start Spring Boot:

```powershell
$env:SMTP_HOST = "localhost"
$env:SMTP_PORT = "1025"
$env:SMTP_AUTH = "false"
$env:SMTP_STARTTLS = "false"
$env:AUTH_EMAIL_FROM = "noreply@bitewise.local"
$env:PUBLIC_BASE_URL = "http://localhost:8080"
$env:AUTH_COOKIE_SECURE = "false"

$secretBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($secretBytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($secretBytes)
$rng.Dispose()

mvn spring-boot:run
```

The JWT secret is temporary to that PowerShell session. The application requires it to start; never reuse this local secret in production or commit it to the repository.

### 3. Open Bitewise

With the backend running, open [http://localhost:8080](http://localhost:8080). Create an account, open its verification email in Mailpit, and follow the verification link. Then sign in and create a food profile.

For frontend-only development, you can serve the repository root with VS Code Live Server instead. The default ports `5500` and `5501` are included in the backend's local CORS allowlist. Keep the frontend and backend hostnames consistent (for example, both `localhost`, not one `localhost` and one `127.0.0.1`), so browser cookies work as expected. Camera access requires HTTPS or `localhost`.

To stop the local database and Mailpit containers:

```powershell
docker compose down
```

Run this from the `food-safety-backend` directory (the same directory used to start the services). The named PostgreSQL volume is retained by this command.

## Tests

The backend tests use an in-memory database; PostgreSQL and Mailpit do not need to be running:

```powershell
Set-Location food-safety-backend
mvn test
```

## API and backend documentation

The backend README contains the complete API examples, authentication flow, supported profile restrictions, barcode endpoints, local database instructions, and deployment notes:

- [Backend README](food-safety-backend/READMEBackend.md)

Useful endpoint groups include:

- `/api/auth/*` — registration, verification, login, refresh, logout, and password management
- `/api/profiles` — authenticated food profiles
- `/api/analyze` — manual ingredient checks
- `/api/products/*` — barcode lookup, barcode-image decoding, and product analysis
- `/api/saved-products` and `/api/scan-history` — the signed-in user's product library and recent checks
- `/health` — public application and database health check

## Deploy to Render

This repository includes a root-level [`render.yaml`](render.yaml) Blueprint and [`Dockerfile`](Dockerfile). The Blueprint defines a free Render web service that serves both the frontend and API. It does **not** create a database, and a checked-in Blueprint does not by itself create a live service.

For a demo, the documented setup uses a separate PostgreSQL provider such as Neon. Review the provider's current limits and retention policies before storing data you need to keep.

1. Push the repository to GitHub.
2. Create a PostgreSQL database and prepare its host, database name, username, and password.
3. In the [Render Dashboard](https://dashboard.render.com/), create a Blueprint from `Troubador456/Bitewise` and select the branch containing `render.yaml`.
4. Set the Blueprint's environment variables in Render:

   | Variable | Purpose |
   | --- | --- |
   | `DATABASE_URL` | JDBC URL, for example `jdbc:postgresql://<host>/<database>?sslmode=require` |
   | `DATABASE_USERNAME` | Database role |
   | `DATABASE_PASSWORD` | Database password |
   | `JWT_SECRET_BASE64` | Random Base64-encoded secret containing at least 32 bytes |
   | `SMTP_HOST` | Production email provider host |
   | `SMTP_USERNAME` | Email provider username |
   | `SMTP_PASSWORD` | Email provider password |
   | `AUTH_EMAIL_FROM` | Authorized sender address |

   The Blueprint defaults SMTP to port `587` with authentication and STARTTLS enabled. Adjust those settings only if your email provider requires different values. Enter secrets directly in Render; never commit them or paste them into issues or chat.
5. Deploy the Blueprint. Flyway applies the database migrations at startup. Confirm the service health check passes at `/health`, then test account verification, login, barcode lookup, and product saving using the Render URL.

The free Render web service may sleep while idle, so its first request after a quiet period can take longer. A real SMTP provider is required for public account verification and password recovery. See the [backend deployment notes](food-safety-backend/READMEBackend.md#deploy-a-public-demo-on-render) for more detail.
