package com.example.foodsafety;

import com.example.foodsafety.model.DietaryRestriction;
import com.example.foodsafety.model.UserFoodProfile;
import com.example.foodsafety.service.ProfileService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProfileServiceTest {
    private final ProfileService service = new ProfileService();

    @Test
    void savesFindsListsAndDeletesProfile() {
        var profile = new UserFoodProfile(
                "profile-1", "Test", Set.of("en:mustard"), Set.of(DietaryRestriction.VEGAN));

        assertSame(profile, service.save(profile));
        assertEquals(profile, service.find("profile-1").orElseThrow());
        assertEquals(profile, service.all().get("profile-1"));
        assertTrue(service.delete("profile-1"));
        assertTrue(service.find("profile-1").isEmpty());
        assertFalse(service.delete("profile-1"));
    }

    @Test
    void savingSameIdReplacesExistingProfile() {
        service.save(new UserFoodProfile("profile-2", "Old", Set.of(), Set.of()));
        var updated = new UserFoodProfile("profile-2", "New", Set.of("en:lupin"), Set.of());

        service.save(updated);

        assertEquals(updated, service.find("profile-2").orElseThrow());
        assertEquals(1, service.all().size());
    }

    @Test
    void profileCopiesAllergyAndRestrictionSets() {
        var allergies = new java.util.HashSet<>(Set.of("en:celery"));
        var restrictions = new java.util.HashSet<>(Set.of(DietaryRestriction.VEGAN));
        var profile = new UserFoodProfile("profile-3", "Test", allergies, restrictions);

        allergies.add("en:sulfites");
        restrictions.clear();

        assertEquals(Set.of("en:celery"), profile.allergies());
        assertEquals(Set.of(DietaryRestriction.VEGAN), profile.restrictions());
    }
}
