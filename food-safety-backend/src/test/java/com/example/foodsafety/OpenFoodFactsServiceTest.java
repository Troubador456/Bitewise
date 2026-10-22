package com.example.foodsafety;

import com.example.foodsafety.model.ProductImageField;
import com.example.foodsafety.service.OpenFoodFactsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class OpenFoodFactsServiceTest {
    private static final String BASE_URL = "https://off.test";

    @Test
    void retrievesCommunityAllergenTagsWithoutAssumingAClosedSet() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "/api/v2/product/12345678.json")))
                .andExpect(method(GET))
                .andExpect(header("User-Agent", "Bitewise/1.0 (food-safety application)"))
                .andRespond(withSuccess("""
                        {
                          "status": 1,
                          "product": {
                            "code": "12345678",
                            "product_name": "Example",
                            "ingredients_text": "Rice, beans",
                            "allergens_tags": ["en:milk", "en:mustard", "en:an-allergen-not-in-our-code"],
                            "image_front_url": "https://images.example/front.jpg"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var product = service(builder, "user", "pass").getProduct("12345678");

        assertEquals("Example", product.productName());
        assertEquals("Rice, beans", product.ingredients());
        assertEquals(3, product.allergenTags().size());
        assertTrue(product.allergenTags().contains("en:an-allergen-not-in-our-code"));
        assertEquals("https://images.example/front.jpg", product.imageUrl());
        server.verify();
    }

    @Test
    void reportsUnknownBarcodeAsNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v2/product/12345678.json")))
                .andRespond(withSuccess("{\"status\":0,\"product\":{}}", MediaType.APPLICATION_JSON));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(builder, "", "").getProduct("12345678"));

        assertEquals(NOT_FOUND, exception.getStatusCode());
        server.verify();
    }

    @Test
    void rejectsInvalidBarcodeBeforeMakingARequest() {
        var exception = assertThrows(ResponseStatusException.class,
                () -> service(RestClient.builder(), "", "").getProduct("../product"));

        assertTrue(exception.getReason().contains("Barcode"));
    }

    @Test
    void forwardsImageAsAuthenticatedMultipartRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/cgi/product_image_upload.pl"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"imagefield\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ingredients")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"user_id\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("off-user")))
                .andRespond(withSuccess("Image uploaded", MediaType.TEXT_PLAIN));
        byte[] png = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0, 0, 0, 0
        };
        var image = new MockMultipartFile("file", "ingredients.png", "image/png", png);

        var result = service(builder, "off-user", "off-password")
                .uploadImage("12345678", ProductImageField.INGREDIENTS, image);

        assertEquals("12345678", result.barcode());
        assertEquals("ingredients", result.imageField());
        server.verify();
    }

    @Test
    void refusesImageUploadsWhenCredentialsAreMissing() {
        var image = new MockMultipartFile(
                "file", "front.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(RestClient.builder(), "", "")
                        .uploadImage("12345678", ProductImageField.FRONT, image));

        assertTrue(exception.getReason().contains("not configured"));
    }

    @Test
    void reportsOpenFoodFactsUploadRejection() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/cgi/product_image_upload.pl"))
                .andRespond(withSuccess("{\"status\": 0, \"status_verbose\": \"Rejected\"}",
                        MediaType.APPLICATION_JSON));
        var image = new MockMultipartFile("file", "front.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(builder, "user", "pass")
                        .uploadImage("12345678", ProductImageField.FRONT, image));

        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        server.verify();
    }

    @Test
    void rejectsNonImageUploads() {
        var image = new MockMultipartFile("file", "script.txt", "text/plain", "not an image".getBytes());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(RestClient.builder(), "user", "pass")
                        .uploadImage("12345678", ProductImageField.FRONT, image));

        assertTrue(exception.getReason().contains("JPEG and PNG"));
    }

    private static OpenFoodFactsService service(
            RestClient.Builder builder,
            String username,
            String password
    ) {
        return new OpenFoodFactsService(builder, BASE_URL, username, password);
    }
}
