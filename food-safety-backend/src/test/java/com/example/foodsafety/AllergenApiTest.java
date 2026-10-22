package com.example.foodsafety;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import com.example.foodsafety.model.OpenFoodFactsImageUpload;
import com.example.foodsafety.model.OpenFoodFactsProduct;
import com.example.foodsafety.model.ProductImageField;
import com.example.foodsafety.service.BarcodeImageService;
import com.example.foodsafety.service.OpenFoodFactsService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AllergenApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpenFoodFactsService openFoodFactsService;

    @MockBean
    private BarcodeImageService barcodeImageService;

    @Test
    void acceptsUnlistedAllergensInProfilesAndUsesThemDuringAnalysis() throws Exception {
        String profileId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "%s",
                                  "name": "Test",
                                  "allergies": ["unlisted-allergen"],
                                  "restrictions": []
                                }
                                """.formatted(profileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allergies.length()").value(1));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ingredients": "Rice, unlisted-allergen",
                                  "productName": "Soup",
                                  "profileId": "%s"
                                }
                                """.formatted(profileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POTENTIAL_ALLERGEN_CONFLICT"))
                .andExpect(jsonPath("$.detectedAllergens[0]").value("unlisted-allergen"))
                .andExpect(jsonPath("$.restrictionConflicts.length()").value(1));
    }

    @Test
    void rejectsBlankIngredientText() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ingredients": " ",
                                  "productName": "Soup"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForMissingProfileDuringAnalysis() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ingredients": "celery",
                                  "profileId": "missing-profile"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void productAnalysisUsesArbitraryAllergenTagsFromOpenFoodFacts() throws Exception {
        String profileId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "%s",
                                  "name": "Test",
                                  "allergies": ["en:unlisted-allergen"],
                                  "restrictions": []
                                }
                                """.formatted(profileId)))
                .andExpect(status().isCreated());
        when(openFoodFactsService.getProduct("12345678")).thenReturn(
                new OpenFoodFactsProduct("12345678", "Example food", "Rice",
                        java.util.List.of("en:unlisted-allergen"), null, "https://example.test/product"));

        mockMvc.perform(get("/api/products/12345678/analysis").param("profileId", profileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.allergenTags[0]").value("en:unlisted-allergen"))
                .andExpect(jsonPath("$.analysis.status").value("POTENTIAL_ALLERGEN_CONFLICT"))
                .andExpect(jsonPath("$.analysis.restrictionConflicts.length()").value(1));
    }

    @Test
    void forwardsImageUploadsWithSelectedImageField() throws Exception {
        when(openFoodFactsService.uploadImage(
                eq("12345678"), eq(ProductImageField.INGREDIENTS), any()))
                .thenReturn(new OpenFoodFactsImageUpload(
                        "12345678", "ingredients", "Upload forwarded"));
        var image = new MockMultipartFile(
                "file", "ingredients.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});

        mockMvc.perform(multipart("/api/products/12345678/images")
                        .file(image)
                        .param("imageField", "ingredients"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.barcode").value("12345678"))
                .andExpect(jsonPath("$.imageField").value("ingredients"));
    }

    @Test
    void scansBarcodeImageAndReturnsProduct() throws Exception {
        when(barcodeImageService.decode(any())).thenReturn("0028400642033");
        when(openFoodFactsService.getProduct("0028400642033")).thenReturn(
                new OpenFoodFactsProduct("0028400642033", "Nacho Cheese Flavored Tortilla Chips",
                        "corn, cheese", java.util.List.of("en:milk"), null,
                        "https://world.openfoodfacts.org/product/0028400642033"));
        var image = new MockMultipartFile(
                "file", "barcode.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/products/scan").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("0028400642033"))
                .andExpect(jsonPath("$.productName").value("Nacho Cheese Flavored Tortilla Chips"))
                .andExpect(jsonPath("$.allergenTags[0]").value("en:milk"));
    }

    @Test
    void rejectsUnknownImageFields() throws Exception {
        var image = new MockMultipartFile("file", "front.png", "image/png", new byte[]{1});

        mockMvc.perform(multipart("/api/products/12345678/images")
                        .file(image)
                        .param("imageField", "nutrition-facts"))
                .andExpect(status().isBadRequest());
    }
}
