package com.example.foodsafety;

import com.example.foodsafety.auth.AuthUser;
import com.example.foodsafety.auth.AuthUserRepository;
import com.example.foodsafety.model.FoodProfile;
import com.example.foodsafety.repository.FoodProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticatedProfileApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUserRepository users;

    @Autowired
    private FoodProfileRepository profiles;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearProfiles() {
        profiles.deleteAll();
    }

    @Test
    void rejectsProfileRequestsWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/profiles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanCreateListUpdateAndDeleteOwnProfile() throws Exception {
        AuthUser user = createVerifiedUser();
        String token = accessToken(user);
        String createBody = """
                {
                  "name": "My food profile",
                  "allergies": ["en:milk", "en:an-unlisted-allergen"],
                  "restrictions": ["VEGAN"]
                }
                """;

        String response = mockMvc.perform(post("/api/profiles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My food profile"))
                .andExpect(jsonPath("$.allergies.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode profile = objectMapper.readTree(response);
        String profileId = profile.path("id").asText();

        mockMvc.perform(get("/api/profiles")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(profileId));

        mockMvc.perform(put("/api/profiles/{id}", profileId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated profile",
                                  "allergies": ["en:mustard"],
                                  "restrictions": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated profile"))
                .andExpect(jsonPath("$.allergies[0]").value("en:mustard"));

        mockMvc.perform(delete("/api/profiles/{id}", profileId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/profiles/{id}", profileId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedUserCannotReadOrDeleteAnotherUsersProfile() throws Exception {
        AuthUser owner = createVerifiedUser();
        AuthUser otherUser = createVerifiedUser();
        FoodProfile profile = profiles.saveAndFlush(new FoodProfile(
                owner.getId(),
                new com.example.foodsafety.model.FoodProfileRequest(
                        "Private profile", java.util.Set.of("en:milk"), java.util.Set.of())));

        mockMvc.perform(get("/api/profiles/{id}", profile.getId())
                        .header("Authorization", bearer(accessToken(otherUser))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/profiles/{id}", profile.getId())
                        .header("Authorization", bearer(accessToken(otherUser))))
                .andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(profiles.existsById(profile.getId())).isTrue();
    }

    private AuthUser createVerifiedUser() {
        AuthUser user = users.saveAndFlush(new AuthUser(
                UUID.randomUUID() + "@example.test", "Test user", "not-a-real-password-hash"));
        user.verifyEmail();
        return users.saveAndFlush(user);
    }

    private String accessToken(AuthUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("bitewise")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject(user.getEmail())
                .claim("user_id", user.getId().toString())
                .claim("auth_version", user.getAuthVersion())
                .claim("email_verified", true)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
