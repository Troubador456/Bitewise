package com.example.foodsafety.service;

import com.example.foodsafety.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FoodAnalysisService {
    private static final Map<DietaryRestriction, List<String>> RESTRICTION_TERMS = Map.of(
        DietaryRestriction.VEGAN, List.of("beef", "pork", "chicken", "turkey", "lamb", "fish", "shrimp", "gelatin", "honey", "milk", "whey", "casein", "butter", "cheese", "egg", "albumin"),
        DietaryRestriction.VEGETARIAN, List.of("beef", "pork", "chicken", "turkey", "lamb", "fish", "shrimp", "gelatin", "anchovy", "fish sauce"),
        DietaryRestriction.PESCATARIAN, List.of("beef", "pork", "chicken", "turkey", "lamb", "gelatin"),
        DietaryRestriction.GLUTEN_FREE, List.of("wheat", "barley", "rye", "malt", "spelt", "semolina", "bulgur", "farro", "triticale", "seitan"),
        DietaryRestriction.DAIRY_FREE, List.of("milk", "whey", "casein", "butter", "cream", "cheese", "yogurt", "ghee", "lactalbumin"),
        DietaryRestriction.EGG_FREE, List.of("egg", "albumin", "ovalbumin", "mayonnaise"),
        DietaryRestriction.SOY_FREE, List.of("soy", "soya", "tofu", "tempeh", "edamame"),
        DietaryRestriction.LOW_SODIUM, List.of("salt", "sodium", "monosodium glutamate", "msg", "brine", "sodium bicarbonate"),
        DietaryRestriction.KOSHER, List.of("pork", "bacon", "ham", "lard", "shellfish", "shrimp", "crab", "lobster"),
        DietaryRestriction.HALAL, List.of("pork", "bacon", "ham", "lard", "wine", "beer", "rum", "brandy")
    );

    public AnalysisResponse analyze(AnalyzeRequest request, UserFoodProfile profile) {
        String text = normalize(request.ingredients());
        Set<String> ingredientTokens = Arrays.stream(text.split("[,;\\n]+"))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> detectedAllergens = new ArrayList<>();
        List<String> matchedIngredients = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        if (profile != null) {
            for (String allergen : profile.allergies()) {
                String term = normalizeAllergenTag(allergen);
                if (term.isBlank()) {
                    continue;
                }
                List<String> matches = findMatches(text, List.of(term));
                if (!matches.isEmpty()) {
                    detectedAllergens.add(allergen);
                    matchedIngredients.addAll(matches.stream().map(match -> allergen + ": " + match).toList());
                    conflicts.add("ALLERGY " + allergen + " matched: " + String.join(", ", matches));
                }
            }
            for (DietaryRestriction restriction : profile.restrictions()) {
                List<String> matches = findMatches(text, RESTRICTION_TERMS.getOrDefault(restriction, List.of()));
                if (!matches.isEmpty()) {
                    conflicts.add(restriction.name() + " possible conflict: " + String.join(", ", matches));
                }
            }
        }

        List<String> uncertainties = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("may contain") || lower.contains("shared equipment") || lower.contains("cross-contamination")) {
            uncertainties.add("Label mentions possible cross-contact. Treat this as unresolved for an allergy.");
        }
        if (lower.contains("natural flavors") || lower.contains("spices") || lower.contains("seasoning") || lower.contains("modified food starch")) {
            uncertainties.add("A broad ingredient term appears. Its source may need confirmation from the manufacturer.");
        }
        if (lower.contains("gluten-free") || lower.contains("certified") || lower.contains("kosher") || lower.contains("halal")) {
            uncertainties.add("Certification or claim wording was not independently verified.");
        }
        if (ingredientTokens.isEmpty()) {
            uncertainties.add("No usable ingredient entries were detected.");
        }
        uncertainties.add("Ingredient text is matched only against allergens saved in the profile; this does not identify every allergen. Verify the package label and contact the manufacturer for severe allergies.");

        String status = conflicts.stream().anyMatch(s -> s.startsWith("ALLERGY ")) ? "POTENTIAL_ALLERGEN_CONFLICT"
                : !conflicts.isEmpty() ? "DIETARY_CONFLICT"
                : !detectedAllergens.isEmpty() ? "ALLERGENS_DETECTED"
                : "NO_MATCH_FOUND";
        return new AnalysisResponse(
                request.productName() == null || request.productName().isBlank() ? "Unnamed product" : request.productName(),
                status,
                detectedAllergens.stream().distinct().toList(),
                List.copyOf(conflicts),
                matchedIngredients.stream().distinct().toList(),
                List.copyOf(uncertainties),
                "Results are based only on the ingredient text and saved profile allergens. NO_MATCH_FOUND does not mean the product is safe."
        );
    }

    public AnalysisResponse analyzeProduct(
            String productName,
            String ingredients,
            List<String> allergenTags,
            UserFoodProfile profile
    ) {
        List<String> detectedAllergens = allergenTags == null
                ? List.of()
                : allergenTags.stream().filter(Objects::nonNull).distinct().toList();
        List<String> conflicts = new ArrayList<>();
        if (profile != null) {
            Set<String> productAllergenKeys = detectedAllergens.stream()
                    .map(FoodAnalysisService::normalizeAllergenTag)
                    .collect(Collectors.toSet());
            for (String allergy : profile.allergies()) {
                String allergyKey = normalizeAllergenTag(allergy);
                if (productAllergenKeys.contains(allergyKey)) {
                    conflicts.add("ALLERGY " + allergy + " matched Open Food Facts allergen tag: " + allergyKey);
                }
            }
            String ingredientText = normalize(ingredients);
            for (DietaryRestriction restriction : profile.restrictions()) {
                List<String> matches = findMatches(
                        ingredientText, RESTRICTION_TERMS.getOrDefault(restriction, List.of()));
                if (!matches.isEmpty()) {
                    conflicts.add(restriction.name() + " possible conflict: " + String.join(", ", matches));
                }
            }
        }

        List<String> uncertainties = new ArrayList<>();
        if (ingredients == null || ingredients.isBlank()) {
            uncertainties.add("Open Food Facts does not provide ingredient text for this product.");
        }
        if (detectedAllergens.isEmpty()) {
            uncertainties.add("Open Food Facts does not provide allergen tags for this product.");
        }
        uncertainties.add("Open Food Facts product data is community-contributed and may be incomplete or outdated. Verify the package label and contact the manufacturer for severe allergies.");

        String status = conflicts.stream().anyMatch(conflict -> conflict.startsWith("ALLERGY "))
                ? "POTENTIAL_ALLERGEN_CONFLICT"
                : !conflicts.isEmpty() ? "DIETARY_CONFLICT"
                : !detectedAllergens.isEmpty() ? "ALLERGENS_DETECTED"
                : "NO_MATCH_FOUND";
        return new AnalysisResponse(
                productName == null || productName.isBlank() ? "Unnamed product" : productName,
                status,
                detectedAllergens,
                List.copyOf(conflicts),
                List.of(),
                List.copyOf(uncertainties),
                "Results are based on Open Food Facts data. NO_MATCH_FOUND does not mean the product is safe."
        );
    }

    private static String normalizeAllergenTag(String tag) {
        String normalized = tag.toLowerCase(Locale.ROOT).trim();
        int separator = normalized.indexOf(':');
        if (separator >= 0) {
            normalized = normalized.substring(separator + 1);
        }
        return normalized.replace('_', '-');
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static List<String> findMatches(String text, List<String> terms) {
        List<String> found = new ArrayList<>();
        for (String term : terms) {
            Pattern p = Pattern.compile("(?<![a-z])" + Pattern.quote(term) + "(?![a-z])", Pattern.CASE_INSENSITIVE);
            if (p.matcher(text).find()) found.add(term);
        }
        return found;
    }
}
