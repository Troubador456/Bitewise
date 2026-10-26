package com.example.foodsafety.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_history")
public class ScanHistoryEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(length = 14)
    private String barcode;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "safety_result", nullable = false, length = 20)
    private String safetyResult;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    protected ScanHistoryEntry() {}

    public ScanHistoryEntry(UUID userId, String barcode, String productName, String safetyResult) {
        this.userId = userId;
        this.barcode = barcode;
        this.productName = productName;
        this.safetyResult = safetyResult;
        this.scannedAt = Instant.now();
    }

    public ScanHistoryResponse toResponse() {
        return new ScanHistoryResponse(id, barcode, productName, safetyResult, scannedAt);
    }
}
