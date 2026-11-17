package com.example.foodsafety;

import com.example.foodsafety.model.ProductImageField;
import com.example.foodsafety.service.OpenFoodFactsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenFoodFactsLookupTest {
    private static final String BASE_URL = "https://off.test";

    @Test
    void looksUpProductAndReturnsIngredientsAndDynamicAllergenTags() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "/api/v2/product/0028400642033.json")))
                .andExpect(method(GET))
                .andExpect(header("User-Agent", "Bitewise/1.0 (food-safety application)"))
                .andRespond(withSuccess("""
                        {
                          "status": 1,
                          "product": {
                            "code": "0028400642033",
                            "product_name": "Nacho Cheese Flavored Tortilla Chips",
                            "ingredients_text": "corn, cheese",
                            "allergens_tags": ["en:milk", "en:an-allergen-not-in-the-code"],
                            "image_front_url": "https://images.example/front.jpg"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var product = service(builder).getProduct("0028400642033");

        assertEquals("0028400642033", product.barcode());
        assertEquals("Nacho Cheese Flavored Tortilla Chips", product.productName());
        assertEquals("corn, cheese", product.ingredients());
        assertEquals(2, product.allergenTags().size());
        assertEquals("en:an-allergen-not-in-the-code", product.allergenTags().get(1));
        assertEquals("https://images.example/front.jpg", product.imageUrl());
        server.verify();
    }

    @Test
    void returnsNotFoundWhenBarcodeIsNotInOpenFoodFacts() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v2/product/12345678.json")))
                .andRespond(withSuccess("{\"status\":0,\"product\":{}}", MediaType.APPLICATION_JSON));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(builder).getProduct("12345678"));

        assertEquals(NOT_FOUND, exception.getStatusCode());
        server.verify();
    }

    @Test
    void rejectsMalformedBarcodeBeforeCallingOpenFoodFacts() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(RestClient.builder()).getProduct("../not-a-barcode"));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsProductImagesLargerThanTheHostingRequestLimit() {
        var service = new OpenFoodFactsService(RestClient.builder(), BASE_URL, "user", "password");
        var image = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                new byte[4 * 1024 * 1024 + 1]);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.uploadImage("0028400642033", ProductImageField.FRONT, image));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Image must be 4 MB or smaller", exception.getReason());
    }

    private static OpenFoodFactsService service(RestClient.Builder builder) {
        return new OpenFoodFactsService(builder, BASE_URL, "", "");
    }
}
