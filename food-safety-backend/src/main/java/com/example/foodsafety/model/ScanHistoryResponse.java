package com.example.foodsafety.model;

import java.time.Instant;

public record ScanHistoryResponse(
        Long id,
        String barcode,
        String productName,
        String safetyResult,
        Instant scannedAt
) {}
