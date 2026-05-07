package com.yemeksepeti;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Web equivalent of blackbox/mobile/YemekSepetiSearch.yaml.
 *
 *  Mobile element                   →  Web equivalent
 *  HomeSearchBar (address)          →  [data-testid='location-search-button']
 *  AUTOCOMPLETE_SUGGESTION_ENTRY    →  [data-testid='address-suggestion-item']
 *  IMAGE (restaurant card)          →  a[href*='/restaurant/']
 *  "Menüde ara" placeholder         →  [data-testid='search-input'] (placeholder "Menüde Ara")
 *  Top-bar restaurant search        →  [data-testid='new-search-input']
 */
class YemekSepetiSearchTest extends BaseTest {

    @Test
    @DisplayName("Senaryo 1: Adres seç → öneri tıkla → restoran kartından detaya git")
    void scenario1_addressSuggestionAndRestaurantDetail() {
        step("Adres modali aç ve 'Üniversite 2' adresini doğrula");
        selectAddress("Üniversite 2");

        step("İlk restorana git");
        Locator firstRestaurant = page.locator("a[href*='/restaurant/']").first();
        firstRestaurant.waitFor();
        clickAndWait(firstRestaurant);

        step("URL /restaurant/ içeriyor ve menü arama görünür");
        assertThat(page).hasURL(Pattern.compile(".*/restaurant/.*"));
        assertThat(page.locator("[data-testid='search-input']").first()).isVisible();

        step("✔ Senaryo 1 tamamlandı");
    }

    @Test
    @DisplayName("Senaryo 2: Üst arama çubuğunda 'lahmacun' arat → ilk öneriye tıkla")
    void scenario2_topbarSearchAndDetail() {
        step("Üst arama çubuğuna tıkla ve 'lahmacun' yaz");
        Locator topSearch = page.locator("[data-testid='new-search-input']");
        topSearch.waitFor();
        topSearch.click();
        topSearch.fill("lahmacun");
        page.waitForTimeout(2_000);

        step("Otomatik öneri listesinden ilk öğeye tıkla");
        Locator firstAutocomplete = page.locator(
                "li[role='option'], .search-autocomplete-item, [data-testid*='autocomplete']")
                .first();
        firstAutocomplete.waitFor();
        clickAndWait(firstAutocomplete);

        step("Sonuç: restoran detayı ya da filtreli restoran listesi");
        if (page.url().contains("/restaurant/")) {
            assertThat(page.locator("[data-testid='search-input']").first()).isVisible();
        } else {
            assertThat(page.locator("a[href*='/restaurant/']").first()).isVisible();
        }

        step("✔ Senaryo 2 tamamlandı");
    }
}
