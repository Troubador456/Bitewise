package com.example.foodsafety.repository;

import com.example.foodsafety.model.ScanHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScanHistoryRepository extends JpaRepository<ScanHistoryEntry, Long> {
    List<ScanHistoryEntry> findTop20ByUserIdOrderByScannedAtDesc(UUID userId);
}
