package com.example.foodsafety.model;

import java.util.Locale;

public enum ProductImageField {
    FRONT("front"),
    INGREDIENTS("ingredients"),
    NUTRITION("nutrition"),
    OTHER("other");

    private final String apiValue;

    ProductImageField(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static ProductImageField from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Image field is required");
        }
        for (ProductImageField field : values()) {
            if (field.apiValue.equals(value.toLowerCase(Locale.ROOT))) {
                return field;
            }
        }
        throw new IllegalArgumentException("Image field must be front, ingredients, nutrition, or other");
    }
}
