package com.example.foodsafety;

import com.example.foodsafety.model.*;
import com.example.foodsafety.service.FoodAnalysisService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FoodAnalysisServiceTest {
    private final FoodAnalysisService service = new FoodAnalysisService();

    @Test
    void detectsArbitraryProfileAllergyInIngredientText() {
        var profile = new UserFoodProfile("u1", "Test", Set.of("en:unlisted-allergen"), Set.of());
        var response = service.analyze(
                new AnalyzeRequest("Rice, unlisted-allergen, salt", "Snack", "u1"), profile);
        assertEquals("POTENTIAL_ALLERGEN_CONFLICT", response.status());
        assertTrue(response.detectedAllergens().contains("en:unlisted-allergen"));
        assertTrue(response.restrictionConflicts().stream().anyMatch(s -> s.contains("en:unlisted-allergen")));
    }

    @Test
    void detectsVeganConflict() {
        var profile = new UserFoodProfile("u2", "Test", Set.of(), Set.of(DietaryRestriction.VEGAN));
        var response = service.analyze(new AnalyzeRequest("Oats, honey, cinnamon", "Cereal", "u2"), profile);
        assertEquals("DIETARY_CONFLICT", response.status());
    }

    @Test
    void noMatchDoesNotClaimSafe() {
        var response = service.analyze(new AnalyzeRequest("rice, water", "Rice", null), null);
        assertEquals("NO_MATCH_FOUND", response.status());
        assertTrue(response.explanation().contains("does not mean the product is safe"));
    }

    @Test
    void comparesArbitraryOpenFoodFactsAllergenTagsWithProfileTags() {
        var profile = new UserFoodProfile("u4", "Test", Set.of("en:sesame", "en:an-unlisted-allergen"), Set.of());
        var response = service.analyzeProduct(
                "Snack", "Rice, seeds", List.of("en:sesame", "en:an-unlisted-allergen"), profile);

        assertEquals("POTENTIAL_ALLERGEN_CONFLICT", response.status());
        assertEquals(List.of("en:sesame", "en:an-unlisted-allergen"), response.detectedAllergens());
        assertEquals(2, response.restrictionConflicts().size());
    }

    @Test
    void keepsDietaryRestrictionChecksForBarcodeProducts() {
        var profile = new UserFoodProfile("u5", "Test", Set.of(), Set.of(DietaryRestriction.VEGAN));

        var response = service.analyzeProduct("Soup", "Water, milk powder", List.of(), profile);

        assertEquals("DIETARY_CONFLICT", response.status());
        assertTrue(response.restrictionConflicts().stream().anyMatch(conflict -> conflict.contains("VEGAN")));
    }

    @Test
    void reportsUnusableIngredientListAndFallbackProductName() {
        var response = service.analyze(new AnalyzeRequest("  ", " ", null), null);

        assertEquals("Unnamed product", response.productName());
        assertEquals("NO_MATCH_FOUND", response.status());
        assertTrue(response.uncertainties().stream().anyMatch(s -> s.contains("No usable ingredient entries")));
    }
}
