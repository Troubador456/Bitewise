package com.example.foodsafety.model;

import java.time.Instant;

public record SavedProductResponse(
        Long id,
        String barcode,
        String productName,
        String ingredients,
        Instant savedAt
) {}
