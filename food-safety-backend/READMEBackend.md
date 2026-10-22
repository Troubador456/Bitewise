# Food Safety Backend (Java / Spring Boot)

REST backend that compares supplied ingredient text against a user profile containing allergies and dietary restrictions.

## Requirements
- Java 21
- Maven 3.9+ (or use your IDE's Maven integration)

## Run
```bash
mvn spring-boot:run
```
The API starts at `http://localhost:8080`.

Run tests:
```bash
mvn test
```

## API

### Create or update a profile
`POST /api/profiles`

```json
{
  "id": "profile-1",
  "name": "Rohan",
  "allergies": ["PEANUT", "MILK"],
  "restrictions": ["VEGAN", "GLUTEN_FREE"]
}
```

Profile `allergies` values are strings and may contain any Open Food Facts allergen tag, such as `en:milk` or `en:mustard`. The barcode lookup and analysis endpoints return and compare the product's live `allergens_tags` without mapping them to a fixed list. The ingredient-text endpoint can only perform literal matching against the allergy tags saved in a profile; it does not infer every allergen or synonym.

Supported restrictions: `VEGAN`, `VEGETARIAN`, `PESCATARIAN`, `GLUTEN_FREE`, `DAIRY_FREE`, `EGG_FREE`, `SOY_FREE`, `LOW_SODIUM`, `KOSHER`, `HALAL`.

### List, read, delete profiles
- `GET /api/profiles`
- `GET /api/profiles/{id}`
- `DELETE /api/profiles/{id}`

### Analyze ingredients
`POST /api/analyze`

```json
{
  "productName": "Snack bar",
  "ingredients": "Oats, peanut butter, whey protein, salt. May contain sesame.",
  "profileId": "profile-1"
}
```

`profileId` is optional. Without it, no allergen matching is performed by this ingredient-text endpoint.

### Look up and analyze a barcode
`GET /api/products/{barcode}` returns the product name, ingredient text, image URL, and Open Food Facts allergen tags. The barcode must contain 8–14 digits.

`GET /api/products/{barcode}/analysis?profileId=profile-1` compares the product's community-contributed allergen tags with the saved profile. A profile tag matches whether supplied with or without the language prefix (for example, `milk` matches `en:milk`). Without `profileId`, this endpoint reports the product tags without profile conflict checks.

### Scan a barcode from an image
`POST /api/products/scan` accepts a multipart `file` image, decodes a supported product barcode, and returns the matching Open Food Facts product. Supported barcode formats include EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, ITF, and QR codes. The uploaded image must be 10 MB or smaller, and the barcode must decode to 8–14 digits.

PowerShell example:

```powershell
curl.exe -X POST "http://localhost:8080/api/products/scan" `
  -F "file=@C:\path\to\barcode-image.jpg"
```

This scan endpoint only reads the barcode; it does not upload the image to Open Food Facts. If the barcode is blurry, cropped, or unsupported, retake a clear image with the entire barcode visible. If it decodes but Open Food Facts has no matching product, the API returns `404`.

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
This starter backend uses an in-memory profile store, so profiles disappear when the app restarts. Add a database and authentication before deploying. Ingredient-text matching still uses a limited local keyword list. Open Food Facts data is community-contributed and may be missing or outdated; neither source is a certified food-safety system or establishes that food is safe. Image upload forwards an image contribution but does not perform OCR or infer product allergens from the image. For serious allergies, check the package's allergen statement and contact the manufacturer; do not rely on a keyword result alone.

## Suggested production additions
1. PostgreSQL persistence and schema migrations.
2. Authentication and per-user authorization.
3. Audit logging with privacy controls and rate limiting.
4. OCR and label verification for package images.
5. A reviewed ingredient ontology, multilingual synonyms, and automated regression tests.
6. Integration tests and deployment configuration.
7. Expand and review local allergen synonyms with food-safety domain experts; keyword matches are not a safety determination.
