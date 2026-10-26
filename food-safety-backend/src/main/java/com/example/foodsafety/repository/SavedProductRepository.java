package com.example.foodsafety.repository;

import com.example.foodsafety.model.SavedProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedProductRepository extends JpaRepository<SavedProduct, Long> {
    List<SavedProduct> findAllByUserIdOrderBySavedAtDesc(UUID userId);

    Optional<SavedProduct> findByIdAndUserId(Long id, UUID userId);

    Optional<SavedProduct> findByUserIdAndBarcode(UUID userId, String barcode);
}
