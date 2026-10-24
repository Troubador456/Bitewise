package com.example.foodsafety.model;

import com.example.foodsafety.auth.AuthUser;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "food_profiles")
public class FoodProfile {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "food_profile_allergies",
            joinColumns = @JoinColumn(name = "profile_id"),
            foreignKey = @ForeignKey(name = "fk_food_profile_allergies_profile")
    )
    @Column(name = "allergen_tag", nullable = false, length = 255)
    private Set<String> allergies = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "food_profile_restrictions",
            joinColumns = @JoinColumn(name = "profile_id"),
            foreignKey = @ForeignKey(name = "fk_food_profile_restrictions_profile")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "restriction", nullable = false, length = 32)
    private Set<DietaryRestriction> restrictions = new HashSet<>();

    protected FoodProfile() {}

    public FoodProfile(UUID ownerId, FoodProfileRequest request) {
        this.ownerId = ownerId;
        update(request);
    }

    public void update(FoodProfileRequest request) {
        this.name = request.name().trim();
        this.allergies = new HashSet<>(request.allergies());
        this.restrictions = new HashSet<>(request.restrictions());
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public Set<String> getAllergies() {
        return Set.copyOf(allergies);
    }

    public Set<DietaryRestriction> getRestrictions() {
        return Set.copyOf(restrictions);
    }

    public UserFoodProfile toResponse() {
        return new UserFoodProfile(id.toString(), name, allergies, restrictions);
    }
}
