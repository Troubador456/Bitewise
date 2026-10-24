package com.example.foodsafety.service;

import com.example.foodsafety.model.FoodProfile;
import com.example.foodsafety.model.FoodProfileRequest;
import com.example.foodsafety.model.UserFoodProfile;
import com.example.foodsafety.repository.FoodProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProfileService {
    private final FoodProfileRepository profiles;

    public ProfileService(FoodProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional
    public UserFoodProfile create(UUID ownerId, FoodProfileRequest request) {
        return profiles.save(new FoodProfile(ownerId, request)).toResponse();
    }

    @Transactional
    public Optional<UserFoodProfile> update(UUID ownerId, UUID profileId, FoodProfileRequest request) {
        return profiles.findByIdAndOwnerId(profileId, ownerId)
                .map(profile -> {
                    profile.update(request);
                    return profile.toResponse();
                });
    }

    @Transactional(readOnly = true)
    public Optional<UserFoodProfile> find(UUID ownerId, UUID profileId) {
        return profiles.findByIdAndOwnerId(profileId, ownerId)
                .map(FoodProfile::toResponse);
    }

    @Transactional(readOnly = true)
    public List<UserFoodProfile> all(UUID ownerId) {
        return profiles.findAllByOwnerIdOrderByNameAsc(ownerId).stream()
                .map(FoodProfile::toResponse)
                .toList();
    }

    @Transactional
    public boolean delete(UUID ownerId, UUID profileId) {
        return profiles.findByIdAndOwnerId(profileId, ownerId)
                .map(profile -> {
                    profiles.delete(profile);
                    return true;
                })
                .orElse(false);
    }
}
