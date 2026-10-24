package com.example.foodsafety.controller;

import com.example.foodsafety.model.UserFoodProfile;
import com.example.foodsafety.model.FoodProfileRequest;
import com.example.foodsafety.auth.CurrentUserService;
import com.example.foodsafety.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
    private final ProfileService service;
    private final CurrentUserService currentUser;

    public ProfileController(ProfileService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserFoodProfile create(@Valid @RequestBody FoodProfileRequest profile) {
        return service.create(currentUser.id(), profile);
    }

    @GetMapping
    public List<UserFoodProfile> list() {
        return service.all(currentUser.id());
    }

    @GetMapping("/{id}")
    public UserFoodProfile get(@PathVariable String id) {
        return service.find(currentUser.id(), parseId(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    @PutMapping("/{id}")
    public UserFoodProfile update(@PathVariable String id, @Valid @RequestBody FoodProfileRequest profile) {
        return service.update(currentUser.id(), parseId(id), profile)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!service.delete(currentUser.id(), parseId(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
    }
}
