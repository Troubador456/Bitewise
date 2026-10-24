package com.example.foodsafety.controller;

import com.example.foodsafety.model.*;
import com.example.foodsafety.auth.CurrentUserService;
import com.example.foodsafety.service.FoodAnalysisService;
import com.example.foodsafety.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AnalysisController {
    private final FoodAnalysisService analysisService;
    private final ProfileService profileService;
    private final CurrentUserService currentUser;

    public AnalysisController(
            FoodAnalysisService analysisService,
            ProfileService profileService,
            CurrentUserService currentUser
    ) {
        this.analysisService = analysisService;
        this.profileService = profileService;
        this.currentUser = currentUser;
    }

    @PostMapping("/analyze")
    public AnalysisResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        UserFoodProfile profile = null;
        if (request.profileId() != null && !request.profileId().isBlank()) {
            profile = profileService.find(currentUser.id(), parseProfileId(request.profileId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        }
        return analysisService.analyze(request, profile);
    }

    private static java.util.UUID parseProfileId(String id) {
        try {
            return java.util.UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
    }
}
