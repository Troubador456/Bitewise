# Food Safety Backend (Java / Spring Boot)

REST backend that compares supplied ingredient text against a user profile containing allergies and dietary restrictions.

## Requirements
- Java 21
- Maven 3.9+ (or use your IDE's Maven integration)
- Docker Desktop with Docker Compose for the local PostgreSQL database

## Run
Start PostgreSQL from the backend directory:

```bash
docker compose up -d
```

For local email testing, Compose also starts Mailpit. Configure the backend to deliver emails to this local inbox:

```powershell
$env:SMTP_HOST = "localhost"
$env:SMTP_PORT = "1025"
$env:SMTP_AUTH = "false"
$env:SMTP_STARTTLS = "false"
$env:AUTH_EMAIL_FROM = "noreply@bitewise.local"
$env:PUBLIC_BASE_URL = "http://localhost:8080"
$secretBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($secretBytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($secretBytes)
$rng.Dispose()
$env:AUTH_COOKIE_SECURE = "false"
mvn spring-boot:run
```

Run the environment setup and `mvn spring-boot:run` in the same PowerShell window. Do not run Maven by itself: the app intentionally refuses to start without `JWT_SECRET_BASE64`. The generated JWT secret is local to that PowerShell session and should not be committed or reused in production.

Open [http://localhost:8025](http://localhost:8025) to view local verification and password-reset emails. Mailpit captures these messages; it does not send them to real recipients.

### Try the authentication flow

With the backend and Mailpit running, use PowerShell to create an account. Replace the example email with an address you can recognize in Mailpit; local Mailpit does not deliver mail externally.

```powershell
$baseUrl = "http://localhost:8080"
$email = "you@example.test"
$password = "Use-a-local-test-password-123!"
$registration = @{
  email = $email
  displayName = "Local Test"
  password = $password
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/register" `
  -ContentType "application/json" -Body $registration
```

Open [http://localhost:8025](http://localhost:8025), open the verification email, and follow its link. Then log in and save the short-lived access token:

```powershell
$loginBody = @{ email = $email; password = $password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" `
  -ContentType "application/json" -Body $loginBody
$token = $login.accessToken
$headers = @{ Authorization = "Bearer $token" }

Invoke-RestMethod -Uri "$baseUrl/api/auth/me" -Headers $headers
```

The login response contains the access token and user information. The refresh token is sent separately as an HTTP-only `bitewise_refresh` cookie; browser clients should let the browser manage that cookie rather than reading it from JavaScript. To try an authenticated profile request:

```powershell
$profileBody = @{
  name = "My local profile"
  allergies = @("en:milk", "en:mustard")
  restrictions = @("VEGAN")
} | ConvertTo-Json

$profile = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/profiles" `
  -Headers $headers -ContentType "application/json" -Body $profileBody
$profile
Invoke-RestMethod -Uri "$baseUrl/api/profiles" -Headers $headers
```

The profile response includes its generated ID. Profile routes return `401 Unauthorized` without a valid bearer access token. Other auth routes include `POST /api/auth/refresh`, `POST /api/auth/logout`, `POST /api/auth/logout-all`, `POST /api/auth/verification/resend`, `POST /api/auth/password/forgot`, `POST /api/auth/password/reset`, `POST /api/auth/password/change`, and `DELETE /api/auth/me` (requires authentication and the account password). Password-reset links/tokens are delivered to the same Mailpit inbox.

To inspect persisted accounts and profiles locally, query PostgreSQL from the backend directory:

```powershell
docker compose exec postgres psql -U bitewise -d bitewise -c "SELECT id, email, email_verified, created_at FROM auth_users;"
docker compose exec postgres psql -U bitewise -d bitewise -c "SELECT id, owner_id, name FROM food_profiles;"
```

Do not query or share password hashes, action tokens, refresh-token hashes, or access/refresh tokens. Use a disposable local account for testing.

For real email delivery, configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `AUTH_EMAIL_FROM`, and (as required by your provider) `SMTP_AUTH` and `SMTP_STARTTLS`. Set these as environment variables or in a secret manager; never commit SMTP credentials. Use the provider's recommended TLS settings and an authorized sender address.

The app connects to PostgreSQL at `localhost:5432` using the development credentials in `compose.yaml`. Override `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` through environment variables for another database or deployment. Never use the development database password or a sample JWT secret in production.

Flyway applies versioned schema migrations automatically at startup; Hibernate validates the schema rather than modifying it.

The API starts at `http://localhost:8080`.

### Run the browser frontend

Serve the repository root (`index.html`, `signin.html`, `createaccount.html`, and `home.html`) with VS Code Live Server. Open the Live Server URL, such as `http://127.0.0.1:5500/index.html`; do not open the HTML files directly with a `file://` URL because browser security blocks API requests from that origin. If VS Code Live Server chooses port `5501`, that origin is also allowed by default.

On the dashboard, choose **Scan barcode with camera** or **Upload barcode image** to look up a product and check its Open Food Facts data against the active profile. Camera access requires a secure origin (HTTPS or `localhost`). If the product is not listed or scanning is unavailable, expand **Enter ingredients manually (alternative)** to check copied label text.

The frontend API URL is set in the repository-root `api-config.js` and defaults to port `8080` on the same hostname as the frontend. Keeping the hostname consistent (both `localhost` or both `127.0.0.1`) lets the browser send the HTTP-only refresh cookie. If the backend is running on another port, change the port in that file to match. The backend's default CORS allowlist includes the common Live Server origins on ports `5500` and `5501`; for another frontend origin, set `CORS_ALLOWED_ORIGINS` before starting Spring Boot, for example:

```powershell
$env:CORS_ALLOWED_ORIGINS = "http://localhost:5500,http://127.0.0.1:5500"
mvn spring-boot:run
```

Create an account in the frontend, verify the email through Mailpit at [http://localhost:8025](http://localhost:8025), and then sign in. The dashboard loads and saves profiles using the authenticated API. The access token is kept in the current browser tab's session storage; the refresh token remains in an HTTP-only cookie.

Restart Spring Boot after changing its CORS settings so the new allowlist takes effect.

If the frontend reports “Failed to fetch,” confirm the backend started successfully and restart any previously running backend after CORS settings change. The Maven Flyway metadata `401` warning is separate; the fatal startup error in that case is the missing JWT secret.

Run tests:
```bash
mvn test
```

## API

### Create a profile
`POST /api/profiles`

```json
{
  "name": "Rohan",
  "allergies": ["en:peanuts", "en:milk"],
  "restrictions": ["VEGAN", "GLUTEN_FREE"]
}
```

Profile `allergies` values are strings and may contain any Open Food Facts allergen tag, such as `en:milk` or `en:mustard`. The barcode lookup and analysis endpoints return and compare the product's live `allergens_tags` without mapping them to a fixed list. The ingredient-text endpoint can only perform literal matching against the allergy tags saved in a profile; it does not infer every allergen or synonym.

All profile endpoints require a valid Bearer access token. The backend generates the profile ID and associates the profile with the authenticated account. Do not send an `id` or owner ID in the create body.

Supported restrictions: `VEGAN`, `VEGETARIAN`, `PESCATARIAN`, `GLUTEN_FREE`, `DAIRY_FREE`, `EGG_FREE`, `SOY_FREE`, `LOW_SODIUM`, `KOSHER`, `HALAL`.

### List, read, update, delete profiles
- `GET /api/profiles`
- `GET /api/profiles/{id}`
- `PUT /api/profiles/{id}` (same body shape as create)
- `DELETE /api/profiles/{id}`

Profile IDs are UUIDs returned by create/list/get. Users can only access their own profiles; another user's profile is reported as not found.

### Analyze ingredients
`POST /api/analyze`

```json
{
  "productName": "Snack bar",
  "ingredients": "Oats, peanut butter, whey protein, salt. May contain sesame.",
  "profileId": "550e8400-e29b-41d4-a716-446655440000"
}
```

`profileId` is optional. Replace the example UUID with an ID returned by `POST /api/profiles`. Without it, no allergen matching is performed by this ingredient-text endpoint.

Ingredient checks are also recorded in the authenticated user's recent scan history. Because a keyword or community-data check cannot certify a product as safe, history records `WARNING` unless an allergy conflict is found, in which case it records `UNSAFE`; it does not record `SAFE`.

### Look up and analyze a barcode
`GET /api/products/{barcode}` returns the product name, ingredient text, image URL, and Open Food Facts allergen tags. The barcode must contain 8–14 digits.

`GET /api/products/{barcode}/analysis?profileId=550e8400-e29b-41d4-a716-446655440000` compares the product's community-contributed allergen tags with the saved profile. Replace the example UUID with an ID returned by `POST /api/profiles`. A profile tag matches whether supplied with or without the language prefix (for example, `milk` matches `en:milk`). Without `profileId`, this endpoint reports the product tags without profile conflict checks.

### Scan a barcode from an image
`POST /api/products/scan` accepts a multipart `file` image, decodes a supported product barcode, and returns the matching Open Food Facts product. Supported barcode formats include EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, ITF, and QR codes. The uploaded image must be 10 MB or smaller, and the barcode must decode to 8–14 digits.

PowerShell example:

```powershell
curl.exe -X POST "http://localhost:8080/api/products/scan" `
  -F "file=@C:\path\to\barcode-image.jpg"
```

This scan endpoint only reads the barcode; it does not upload the image to Open Food Facts. If the barcode is blurry, cropped, or unsupported, retake a clear image with the entire barcode visible. If it decodes but Open Food Facts has no matching product, the API returns `404`.

### Save products and view scan history

- `POST /api/saved-products` saves or updates a product for the signed-in account. Send `barcode`, `productName`, and optional `ingredients` as JSON. Saving an existing barcode updates its stored details rather than creating a duplicate.
- `GET /api/saved-products` lists the signed-in user's saved products.
- `DELETE /api/saved-products/{id}` removes one of the signed-in user's saved products.
- `GET /api/scan-history` returns up to the user's 20 most recent barcode and ingredient checks.

These records belong to `auth_users` and are deleted when the account is deleted. Food allergies and dietary restrictions remain attached to `food_profiles`, so users can keep different sets of needs without a second account-level restriction system.

### Upload a product image
`POST /api/products/{barcode}/images?imageField=front` accepts a multipart request containing a `file` image. `imageField` may be `front` (default), `ingredients`, `nutrition`, or `other`. JPEG and PNG files up to 10 MB are accepted.

Image uploads are forwarded to Open Food Facts as authenticated contributions. Configure the server environment with an Open Food Facts account before enabling uploads:

```powershell
$env:OPENFOODFACTS_USERNAME = "your-open-food-facts-username"
$env:OPENFOODFACTS_PASSWORD = "your-open-food-facts-password"
mvn spring-boot:run
```

Keep these credentials on the backend; do not put them in a browser or mobile client. Uploading contributes the image to the public Open Food Facts product database. Obtain the user's consent before submitting an image, and verify the product page after upload.

Example response:
```json
{
  "productName": "Snack bar",
  "status": "POTENTIAL_ALLERGEN_CONFLICT",
  "detectedAllergens": ["PEANUT", "MILK", "SESAME"],
  "restrictionConflicts": [
    "ALLERGY PEANUT matched: peanut",
    "ALLERGY MILK matched: whey",
    "VEGAN possible conflict: peanut, whey"
  ],
  "matchedIngredients": ["PEANUT: peanut", "MILK: whey", "SESAME: sesame"],
  "uncertainties": [
    "Label mentions possible cross-contact. Treat this as unresolved for an allergy.",
    "Keyword matching can miss synonyms, derivatives, ambiguous ingredients, and cross-contact. Verify the package label and contact the manufacturer for severe allergies."
  ],
  "explanation": "Results are based only on the ingredient text supplied. NO_MATCH_FOUND does not mean the product is safe."
}
```

# BARCODE SCAN testing: 
mvn spring-boot:run
curl.exe -X POST "http://localhost:8080/api/products/scan" `
  -F "file=@path/to/your/image"
or... the equivalent for Mac (search it up)
check with a bunch of images. I have some in the test folder in food-safety-backend if y'all want to check it out

## Status values
- `POTENTIAL_ALLERGEN_CONFLICT`: a configured allergy keyword matched.
- `DIETARY_CONFLICT`: at least one configured dietary restriction keyword matched.
- `ALLERGENS_DETECTED`: allergen terms were detected, but no profile conflict was found.
- `NO_MATCH_FOUND`: no configured keyword matched. This is not a safety guarantee.

## Included functionality
- Create/update, list, retrieve, and delete food profiles
- Built-in allergen keyword detection for ingredient text
- Open Food Facts barcode lookup and dynamic product allergen tags
- Authenticated Open Food Facts product image uploads
- Restriction conflict detection
- Explicit uncertainty notes for cross-contact and ambiguous ingredient terms
- Request validation and consistent HTTP errors
- Unit tests for core analysis behavior

## Important implementation limits
Profiles and authentication records are stored in PostgreSQL, with schema changes managed by Flyway. Configure a persistent, backed-up production PostgreSQL database before deployment; the included Compose service is for development. Account verification and password recovery require SMTP settings. Ingredient-text matching still uses a limited local keyword list. Open Food Facts data is community-contributed and may be missing or outdated; neither source is a certified food-safety system or establishes that food is safe. Image upload forwards an image contribution but does not perform OCR or infer product allergens from the image. For serious allergies, check the package's allergen statement and contact the manufacturer; do not rely on a keyword result alone.

## Potential next steps
1. Add OCR and label verification for package images; image uploads currently contribute photos to Open Food Facts but do not read their contents.
2. Build a reviewed ingredient ontology with multilingual synonyms and regression tests, with food-safety domain experts reviewing matching behavior.
3. Add privacy-conscious audit logging and operational monitoring.
4. Prepare production deployment: use a managed, backed-up PostgreSQL database, configure a real SMTP provider and production secrets, and add deployment-specific integration checks.

PostgreSQL persistence, Flyway migrations, account authentication, and per-user profile authorization are already implemented. Keyword matches are not a food-safety determination.
