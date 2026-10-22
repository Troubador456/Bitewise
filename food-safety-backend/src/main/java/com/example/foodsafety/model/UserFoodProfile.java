package com.example.foodsafety.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record UserFoodProfile(
        @NotBlank String id,
        @NotBlank String name,
        Set<String> allergies,
        Set<DietaryRestriction> restrictions
) {
    public UserFoodProfile {
        allergies = allergies == null ? Set.of() : Set.copyOf(allergies);
        restrictions = restrictions == null ? Set.of() : Set.copyOf(restrictions);
    }
}
