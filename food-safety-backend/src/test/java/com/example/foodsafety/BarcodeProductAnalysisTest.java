package com.example.foodsafety;

import com.example.foodsafety.model.DietaryRestriction;
import com.example.foodsafety.model.UserFoodProfile;
import com.example.foodsafety.service.FoodAnalysisService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarcodeProductAnalysisTest {
    private final FoodAnalysisService service = new FoodAnalysisService();

    @Test
    void matchesOpenFoodFactsAllergenTagsWithoutAFixedAllergenList() {
        var profile = new UserFoodProfile(
                "profile-1", "Test", Set.of("en:milk", "en:unlisted-allergen"), Set.of());

        var response = service.analyzeProduct(
                "Example product",
                "Rice, milk",
                List.of("en:milk", "en:unlisted-allergen"),
                profile);

        assertEquals("POTENTIAL_ALLERGEN_CONFLICT", response.status());
        assertEquals(List.of("en:milk", "en:unlisted-allergen"), response.detectedAllergens());
        assertEquals(2, response.restrictionConflicts().size());
    }

    @Test
    void checksDietaryRestrictionsAgainstBarcodeProductIngredients() {
        var profile = new UserFoodProfile(
                "profile-2", "Test", Set.of(), Set.of(DietaryRestriction.VEGAN));

        var response = service.analyzeProduct(
                "Example product", "Water, milk powder", List.of(), profile);

        assertEquals("DIETARY_CONFLICT", response.status());
    }
}
