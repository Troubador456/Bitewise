package com.example.foodsafety.model;

public record ProductAnalysisResponse(
        OpenFoodFactsProduct product,
        AnalysisResponse analysis
) {}
