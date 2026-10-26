package com.example.foodsafety.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SavedProductRequest(
        @NotBlank @Pattern(regexp = "\\d{8,14}") String barcode,
        @NotBlank @Size(max = 255) String productName,
        @Size(max = 20000) String ingredients
) {}
