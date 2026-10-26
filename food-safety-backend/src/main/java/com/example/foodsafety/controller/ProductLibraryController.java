package com.example.foodsafety.controller;

import com.example.foodsafety.auth.CurrentUserService;
import com.example.foodsafety.model.SavedProductRequest;
import com.example.foodsafety.model.SavedProductResponse;
import com.example.foodsafety.model.ScanHistoryResponse;
import com.example.foodsafety.service.ProductLibraryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductLibraryController {
    private final ProductLibraryService productLibrary;
    private final CurrentUserService currentUser;

    public ProductLibraryController(ProductLibraryService productLibrary, CurrentUserService currentUser) {
        this.productLibrary = productLibrary;
        this.currentUser = currentUser;
    }

    @PostMapping("/saved-products")
    public SavedProductResponse saveProduct(@Valid @RequestBody SavedProductRequest request) {
        return productLibrary.saveProduct(currentUser.id(), request);
    }

    @GetMapping("/saved-products")
    public List<SavedProductResponse> savedProducts() {
        return productLibrary.savedProducts(currentUser.id());
    }

    @DeleteMapping("/saved-products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSavedProduct(@PathVariable Long id) {
        if (!productLibrary.deleteSavedProduct(currentUser.id(), id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved product not found");
        }
    }

    @GetMapping("/scan-history")
    public List<ScanHistoryResponse> scanHistory() {
        return productLibrary.scanHistory(currentUser.id());
    }
}
