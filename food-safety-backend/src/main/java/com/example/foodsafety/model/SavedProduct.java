package com.example.foodsafety.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_products", uniqueConstraints = @UniqueConstraint(
        name = "uk_saved_products_user_barcode", columnNames = {"user_id", "barcode"}))
public class SavedProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(nullable = false, length = 14)
    private String barcode;

    @Column(columnDefinition = "text")
    private String ingredients;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    protected SavedProduct() {}

    public SavedProduct(UUID userId, String barcode, String productName, String ingredients) {
        this.userId = userId;
        this.barcode = barcode;
        update(productName, ingredients);
        this.savedAt = Instant.now();
    }

    public void update(String productName, String ingredients) {
        this.productName = productName;
        this.ingredients = ingredients;
        this.savedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProductName() {
        return productName;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getIngredients() {
        return ingredients;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public SavedProductResponse toResponse() {
        return new SavedProductResponse(id, barcode, productName, ingredients, savedAt);
    }
}
