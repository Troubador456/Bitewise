package com.example.foodsafety;

import com.example.foodsafety.controller.OpenFoodFactsController;
import com.example.foodsafety.auth.CurrentUserService;
import com.example.foodsafety.model.OpenFoodFactsProduct;
import com.example.foodsafety.service.BarcodeImageService;
import com.example.foodsafety.service.FoodAnalysisService;
import com.example.foodsafety.service.OpenFoodFactsService;
import com.example.foodsafety.service.ProductLibraryService;
import com.example.foodsafety.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BarcodeProductApiTest {
    private final OpenFoodFactsService openFoodFactsService = mock(OpenFoodFactsService.class);
    private final BarcodeImageService barcodeImageService = mock(BarcodeImageService.class);
    private final ProfileService profileService = mock(ProfileService.class);
    private final ProductLibraryService productLibrary = mock(ProductLibraryService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OpenFoodFactsController(
            openFoodFactsService,
            barcodeImageService,
            new FoodAnalysisService(),
            profileService,
            productLibrary,
            currentUser
    )).build();

    @Test
    void scanningBarcodeImageLooksUpAndReturnsProduct() throws Exception {
        var product = new OpenFoodFactsProduct(
                "0028400642033",
                "Nacho Cheese Flavored Tortilla Chips",
                "corn, cheese",
                List.of("en:milk"),
                null,
                "https://world.openfoodfacts.org/product/0028400642033");
        when(barcodeImageService.decode(org.mockito.ArgumentMatchers.any())).thenReturn("0028400642033");
        when(openFoodFactsService.getProduct("0028400642033")).thenReturn(product);
        var image = new MockMultipartFile("file", "barcode.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/products/scan").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("0028400642033"))
                .andExpect(jsonPath("$.productName").value("Nacho Cheese Flavored Tortilla Chips"))
                .andExpect(jsonPath("$.ingredients").value("corn, cheese"))
                .andExpect(jsonPath("$.allergenTags[0]").value("en:milk"));

        verify(barcodeImageService).decode(org.mockito.ArgumentMatchers.any());
        verify(openFoodFactsService).getProduct("0028400642033");
    }

    @Test
    void barcodeLookupReturnsProductDetails() throws Exception {
        when(openFoodFactsService.getProduct("0028400642033")).thenReturn(new OpenFoodFactsProduct(
                "0028400642033", "Example product", "Rice", List.of(), null,
                "https://world.openfoodfacts.org/product/0028400642033"));

        mockMvc.perform(get("/api/products/0028400642033").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("0028400642033"))
                .andExpect(jsonPath("$.productName").value("Example product"));

        verify(openFoodFactsService).getProduct("0028400642033");
    }
}
