package com.example.foodsafety.service;

import com.example.foodsafety.model.OpenFoodFactsImageUpload;
import com.example.foodsafety.model.OpenFoodFactsProduct;
import com.example.foodsafety.model.ProductImageField;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class OpenFoodFactsService {
    private static final Pattern BARCODE_PATTERN = Pattern.compile("\\d{8,14}");
    private static final Pattern UPLOAD_FAILURE_PATTERN =
            Pattern.compile("\"status\"\\s*:\\s*0(?:\\s*[,}])");
    private static final long MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private final RestClient restClient;
    private final String username;
    private final String password;

    public OpenFoodFactsService(
            RestClient.Builder builder,
            @Value("${openfoodfacts.base-url:https://world.openfoodfacts.org}") String baseUrl,
            @Value("${openfoodfacts.username:}") String username,
            @Value("${openfoodfacts.password:}") String password
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, "Bitewise/1.0 (food-safety application)")
                .build();
        this.username = username;
        this.password = password;
    }

    public OpenFoodFactsProduct getProduct(String barcode) {
        validateBarcode(barcode);
        try {
            JsonNode response = restClient.get()
                    .uri("/api/v2/product/{barcode}.json?fields=code,product_name,product_name_en,ingredients_text,allergens_tags,image_front_url,image_url",
                            barcode)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.path("status").asInt(0) != 1) {
                throw new ResponseStatusException(NOT_FOUND, "Product not found in Open Food Facts");
            }

            JsonNode product = response.path("product");
            String name = firstNonBlank(product.path("product_name").asText(null),
                    product.path("product_name_en").asText(null));
            List<String> allergenTags = new ArrayList<>();
            JsonNode tags = product.path("allergens_tags");
            if (tags.isArray()) {
                tags.forEach(tag -> {
                    if (tag.isTextual() && !tag.asText().isBlank()) {
                        allergenTags.add(tag.asText());
                    }
                });
            }
            return new OpenFoodFactsProduct(
                    product.path("code").asText(barcode),
                    name,
                    product.path("ingredients_text").asText(null),
                    allergenTags,
                    firstNonBlank(product.path("image_front_url").asText(null),
                            product.path("image_url").asText(null)),
                    "https://world.openfoodfacts.org/product/" + barcode
            );
        } catch (RestClientResponseException exception) {
            throw upstreamFailure("product lookup", exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Open Food Facts is temporarily unavailable", exception);
        }
    }

    public OpenFoodFactsImageUpload uploadImage(
            String barcode,
            ProductImageField imageField,
            MultipartFile file
    ) {
        validateBarcode(barcode);
        if (username.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Image upload is not configured. Set OPENFOODFACTS_USERNAME and OPENFOODFACTS_PASSWORD.");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "A non-empty image file is required");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new ResponseStatusException(BAD_REQUEST, "Image must be 10 MB or smaller");
        }

        final byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to read uploaded image", exception);
        }
        String mimeType = imageMimeType(imageBytes);
        if (mimeType == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Only valid JPEG and PNG images are supported");
        }
        String extension = MediaType.IMAGE_PNG_VALUE.equals(mimeType) ? ".png" : ".jpg";

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("code", barcode);
        form.add("imagefield", imageField.apiValue());
        form.add("user_id", username);
        form.add("password", password);
        form.add("imgupload", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "product-image" + extension;
            }
        });

        try {
            String response = restClient.post()
                    .uri("/cgi/product_image_upload.pl")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            if (response != null && UPLOAD_FAILURE_PATTERN.matcher(response).find()) {
                throw new ResponseStatusException(BAD_GATEWAY,
                        "Open Food Facts rejected the image upload");
            }
            return new OpenFoodFactsImageUpload(
                    barcode,
                    imageField.apiValue(),
                    "Upload forwarded to Open Food Facts. Verify the product page to confirm publication."
            );
        } catch (RestClientResponseException exception) {
            throw upstreamFailure("image upload", exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Open Food Facts is temporarily unavailable", exception);
        }
    }

    private static void validateBarcode(String barcode) {
        if (barcode == null || !BARCODE_PATTERN.matcher(barcode).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "Barcode must contain 8 to 14 digits");
        }
    }

    private static String imageMimeType(byte[] bytes) {
        if (bytes.length >= PNG_SIGNATURE.length) {
            boolean isPng = true;
            for (int index = 0; index < PNG_SIGNATURE.length; index++) {
                if (bytes[index] != PNG_SIGNATURE[index]) {
                    isPng = false;
                    break;
                }
            }
            if (isPng) {
                return MediaType.IMAGE_PNG_VALUE;
            }
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first
                : second != null && !second.isBlank() ? second
                : null;
    }

    private static ResponseStatusException upstreamFailure(
            String operation,
            RestClientResponseException exception
    ) {
        return new ResponseStatusException(BAD_GATEWAY,
                "Open Food Facts " + operation + " failed with HTTP "
                        + exception.getStatusCode().value());
    }
}
