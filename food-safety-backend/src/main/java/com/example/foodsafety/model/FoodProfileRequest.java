package com.example.foodsafety.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record FoodProfileRequest(
        @NotBlank @Size(max = 100) String name,
        Set<@NotBlank @Size(max = 255) String> allergies,
        Set<DietaryRestriction> restrictions
) {
    public FoodProfileRequest {
        allergies = allergies == null ? Set.of() : Set.copyOf(allergies);
        restrictions = restrictions == null ? Set.of() : Set.copyOf(restrictions);
    }
}
