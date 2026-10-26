package com.example.foodsafety.service;

import com.example.foodsafety.model.AnalysisResponse;
import com.example.foodsafety.model.SavedProduct;
import com.example.foodsafety.model.SavedProductRequest;
import com.example.foodsafety.model.SavedProductResponse;
import com.example.foodsafety.model.ScanHistoryEntry;
import com.example.foodsafety.model.ScanHistoryResponse;
import com.example.foodsafety.repository.SavedProductRepository;
import com.example.foodsafety.repository.ScanHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductLibraryService {
    private final SavedProductRepository savedProducts;
    private final ScanHistoryRepository scanHistory;

    public ProductLibraryService(
            SavedProductRepository savedProducts,
            ScanHistoryRepository scanHistory
    ) {
        this.savedProducts = savedProducts;
        this.scanHistory = scanHistory;
    }

    @Transactional
    public SavedProductResponse saveProduct(UUID userId, SavedProductRequest request) {
        SavedProduct product = savedProducts.findByUserIdAndBarcode(userId, request.barcode())
                .map(existing -> {
                    existing.update(request.productName().trim(), request.ingredients());
                    return existing;
                })
                .orElseGet(() -> new SavedProduct(
                        userId,
                        request.barcode(),
                        request.productName().trim(),
                        request.ingredients()));
        return savedProducts.save(product).toResponse();
    }

    @Transactional(readOnly = true)
    public List<SavedProductResponse> savedProducts(UUID userId) {
        return savedProducts.findAllByUserIdOrderBySavedAtDesc(userId).stream()
                .map(SavedProduct::toResponse)
                .toList();
    }

    @Transactional
    public boolean deleteSavedProduct(UUID userId, Long productId) {
        return savedProducts.findByIdAndUserId(productId, userId)
                .map(product -> {
                    savedProducts.delete(product);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void recordScan(UUID userId, String barcode, AnalysisResponse analysis) {
        String result = analysis.restrictionConflicts().stream()
                .anyMatch(conflict -> conflict.startsWith("ALLERGY "))
                ? "UNSAFE"
                : "WARNING";
        String productName = analysis.productName();
        if (productName.length() > 255) {
            productName = productName.substring(0, 255);
        }
        scanHistory.save(new ScanHistoryEntry(userId, barcode, productName, result));
    }

    @Transactional(readOnly = true)
    public List<ScanHistoryResponse> scanHistory(UUID userId) {
        return scanHistory.findTop20ByUserIdOrderByScannedAtDesc(userId).stream()
                .map(ScanHistoryEntry::toResponse)
                .toList();
    }
}
