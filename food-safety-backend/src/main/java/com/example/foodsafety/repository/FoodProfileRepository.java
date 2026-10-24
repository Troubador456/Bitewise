package com.example.foodsafety.repository;

import com.example.foodsafety.model.FoodProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FoodProfileRepository extends JpaRepository<FoodProfile, UUID> {
    List<FoodProfile> findAllByOwnerIdOrderByNameAsc(UUID ownerId);

    Optional<FoodProfile> findByIdAndOwnerId(UUID id, UUID ownerId);

}
