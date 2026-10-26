package com.example.foodsafety.controller;

import com.example.foodsafety.model.*;
import com.example.foodsafety.auth.CurrentUserService;
import com.example.foodsafety.service.FoodAnalysisService;
import com.example.foodsafety.service.ProfileService;
import com.example.foodsafety.service.ProductLibraryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AnalysisController {
    private final FoodAnalysisService analysisService;
    private final ProfileService profileService;
    private final ProductLibraryService productLibrary;
    private final CurrentUserService currentUser;

    public AnalysisController(
            FoodAnalysisService analysisService,
            ProfileService profileService,
            ProductLibraryService productLibrary,
            CurrentUserService currentUser
    ) {
        this.analysisService = analysisService;
        this.profileService = profileService;
        this.productLibrary = productLibrary;
        this.currentUser = currentUser;
    }

    @PostMapping("/analyze")
    public AnalysisResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        java.util.UUID userId = currentUser.id();
        UserFoodProfile profile = null;
        if (request.profileId() != null && !request.profileId().isBlank()) {
            profile = profileService.find(userId, parseProfileId(request.profileId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        }
        AnalysisResponse result = analysisService.analyze(request, profile);
        productLibrary.recordScan(userId, null, result);
        return result;
    }

    private static java.util.UUID parseProfileId(String id) {
        try {
            return java.util.UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
    }
}
