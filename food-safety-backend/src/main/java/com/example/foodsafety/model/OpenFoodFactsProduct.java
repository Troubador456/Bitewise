package com.example.foodsafety.model;

import java.util.List;

public record OpenFoodFactsProduct(
        String barcode,
        String productName,
        String ingredients,
        List<String> allergenTags,
        String imageUrl,
        String productUrl
) {
    public OpenFoodFactsProduct {
        allergenTags = allergenTags == null ? List.of() : List.copyOf(allergenTags);
    }
}
