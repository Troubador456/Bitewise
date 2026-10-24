package com.example.foodsafety;

import com.example.foodsafety.auth.AuthUser;
import com.example.foodsafety.auth.AuthUserRepository;
import com.example.foodsafety.model.DietaryRestriction;
import com.example.foodsafety.model.FoodProfileRequest;
import com.example.foodsafety.repository.FoodProfileRepository;
import com.example.foodsafety.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(ProfileService.class)
class FoodProfilePersistenceTest {
    @Autowired
    private AuthUserRepository users;

    @Autowired
    private FoodProfileRepository profiles;

    @Autowired
    private ProfileService profileService;

    @Test
    void persistsProfilesAndCollectionsAndRestrictsAccessToOwner() {
        UUID ownerId = createUser("owner@example.test");
        UUID anotherUserId = createUser("another@example.test");
        var request = new FoodProfileRequest(
                "  Allergy profile  ",
                Set.of("en:milk", "en:an-unlisted-allergen"),
                Set.of(DietaryRestriction.VEGAN));

        var created = profileService.create(ownerId, request);
        UUID profileId = UUID.fromString(created.id());

        assertEquals("Allergy profile", created.name());
        assertEquals(request.allergies(), created.allergies());
        assertEquals(request.restrictions(), created.restrictions());
        assertTrue(profileService.find(ownerId, profileId).isPresent());
        assertTrue(profileService.find(anotherUserId, profileId).isEmpty());
        assertTrue(profileService.all(anotherUserId).isEmpty());
    }

    @Test
    void updatesAndDeletesOnlyProfilesOwnedByTheRequestingUser() {
        UUID ownerId = createUser("update-owner@example.test");
        UUID otherUserId = createUser("update-other@example.test");
        var created = profileService.create(ownerId,
                new FoodProfileRequest("Before", Set.of("en:milk"), Set.of()));
        UUID profileId = UUID.fromString(created.id());
        var replacement = new FoodProfileRequest(
                "After", Set.of("en:unlisted-allergen"), Set.of(DietaryRestriction.VEGAN));

        assertTrue(profileService.update(otherUserId, profileId, replacement).isEmpty());
        assertFalse(profileService.delete(otherUserId, profileId));
        assertEquals("After", profileService.update(ownerId, profileId, replacement).orElseThrow().name());
        assertTrue(profileService.delete(ownerId, profileId));
        assertTrue(profileService.find(ownerId, profileId).isEmpty());
    }

    @Test
    void deletingAnAccountAlsoDeletesItsProfiles() {
        UUID ownerId = createUser("delete-owner@example.test");
        var created = profileService.create(ownerId,
                new FoodProfileRequest("To delete", Set.of("en:milk"), Set.of()));
        UUID profileId = UUID.fromString(created.id());

        users.deleteById(ownerId);
        users.flush();

        assertFalse(profiles.existsById(profileId));
    }

    private UUID createUser(String email) {
        return users.save(new AuthUser(email, "Test user", "password-hash")).getId();
    }
}
