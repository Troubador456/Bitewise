package com.example.foodsafety.controller;

import com.example.foodsafety.model.*;
import com.example.foodsafety.service.FoodAnalysisService;
import com.example.foodsafety.service.BarcodeImageService;
import com.example.foodsafety.service.OpenFoodFactsService;
import com.example.foodsafety.service.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/products")
public class OpenFoodFactsController {
    private final OpenFoodFactsService openFoodFactsService;
    private final BarcodeImageService barcodeImageService;
    private final FoodAnalysisService analysisService;
    private final ProfileService profileService;

    public OpenFoodFactsController(
            OpenFoodFactsService openFoodFactsService,
            BarcodeImageService barcodeImageService,
            FoodAnalysisService analysisService,
            ProfileService profileService
    ) {
        this.openFoodFactsService = openFoodFactsService;
        this.barcodeImageService = barcodeImageService;
        this.analysisService = analysisService;
        this.profileService = profileService;
    }

    @GetMapping("/{barcode}")
    public OpenFoodFactsProduct getProduct(@PathVariable String barcode) {
        return openFoodFactsService.getProduct(barcode);
    }

    @PostMapping(value = "/scan", consumes = "multipart/form-data")
    public OpenFoodFactsProduct scanBarcodeImage(@RequestPart("file") MultipartFile file) {
        String barcode = barcodeImageService.decode(file);
        return openFoodFactsService.getProduct(barcode);
    }

    @GetMapping("/{barcode}/analysis")
    public ProductAnalysisResponse analyzeProduct(
            @PathVariable String barcode,
            @RequestParam(required = false) String profileId
    ) {
        OpenFoodFactsProduct product = openFoodFactsService.getProduct(barcode);
        UserFoodProfile profile = findProfile(profileId);
        AnalysisResponse analysis = analysisService.analyzeProduct(
                product.productName(), product.ingredients(), product.allergenTags(), profile);
        return new ProductAnalysisResponse(product, analysis);
    }

    @PostMapping(value = "/{barcode}/images", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OpenFoodFactsImageUpload uploadImage(
            @PathVariable String barcode,
            @RequestParam(defaultValue = "front") String imageField,
            @RequestPart("file") MultipartFile file
    ) {
        final ProductImageField field;
        try {
            field = ProductImageField.from(imageField);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return openFoodFactsService.uploadImage(barcode, field, file);
    }

    private UserFoodProfile findProfile(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return null;
        }
        return profileService.find(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }
}
