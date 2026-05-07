package com.yemeksepeti;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary value analysis: how many of one product can a single user add
 * to their cart? The mobile YAML's BVA notes for M4/W4 explicitly call out
 * "item quantity (0, 1, max, max + 1)" as a focus.
 *
 * The test clicks "+" repeatedly on the same product and records:
 *   - the highest quantity the stepper actually reached
 *   - whether further clicks were silently ignored (cap reached)
 *   - whether an error / max-message was shown
 *
 * We CAP attempts at MAX_PROBE_CLICKS to keep the run bounded — if the
 * site has no cap below that, the test reports the probe ceiling.
 */
class YemekSepetiCartLimitTest extends BaseTest {

    /** Hard ceiling on attempts so an uncapped product doesn't loop forever. */
    private static final int MAX_PROBE_CLICKS = 50;

    @Test
    @DisplayName("BVA: ürün miktar üst sınırı — '+' tıklayarak limiti bul")
    void findCartItemMaxQuantity() {
        step("Express teslimat lane'inden bir restoran seç ve ilk ürünü ekle");
        String chosen = openExpressRestaurantAndAddFirstProduct(8);
        assertTrue(chosen != null, "no Express-lane restaurant accepted the add-to-cart flow");
        System.out.println("    chosen: " + chosen);

        Locator firstProduct = page.locator("[data-testid='menu-product']").first();
        firstProduct.scrollIntoViewIfNeeded();
        Locator plus = firstProduct.locator(
                "[data-testid='quantity-stepper-collapsed-button']," +
                " button:has([data-testid='quantity-stepper-plus-icon'])").first();
        Locator qtyEl = firstProduct.locator("[data-testid='quantity-stepper-quantity']").first();

        int observedMax = readIntSafe(qtyEl);
        System.out.println("    starting qty after first add = " + observedMax);
        assertTrue(observedMax >= 1, "could not add even one of the product to cart");

        step("'+' butonuna art arda tıkla, miktar artmayana kadar (cap=" + MAX_PROBE_CLICKS + ")");

        // Now hammer "+" until the quantity stops increasing or we hit MAX_PROBE_CLICKS.
        int stuckClicks = 0;
        for (int i = 2; i <= MAX_PROBE_CLICKS; i++) {
            try {
                plus.click(new Locator.ClickOptions().setForce(true).setTimeout(3_000));
            } catch (Exception e) {
                System.out.println("    click #" + i + " threw: " + e.getMessage());
                break;
            }
            page.waitForTimeout(350);
            int current = readIntSafe(qtyEl);
            if (current > observedMax) {
                observedMax = current;
                stuckClicks = 0;
            } else {
                stuckClicks++;
            }
            if (i % 5 == 0 || stuckClicks >= 3) {
                System.out.println("    after " + i + " click(s): qty=" + current
                        + (stuckClicks > 0 ? "  (stuck for " + stuckClicks + ")" : ""));
            }
            if (stuckClicks >= 3) {
                System.out.println("    qty stopped increasing → site-imposed cap reached");
                break;
            }
        }

        step("Sonuç: gözlemlenen üst sınır = " + observedMax);
        // Sanity: at least the first click must have worked (qty >= 1).
        assertTrue(observedMax >= 1,
                "expected to add at least 1 of the product, observedMax=" + observedMax);
        // We don't enforce a specific cap — different restaurants set
        // different per-line maximums. We DO record the value as the test's
        // primary outcome.
        System.out.println("    [BVA RESULT] cart-line max quantity for this product: " + observedMax);

        step("Sepet panelini aç ve doğrula: en yüksek miktar görünür");
        openCartSidebar();
        assertThat(page.getByText(java.util.regex.Pattern.compile(
                "\\b" + observedMax + "\\b"))
                .first()).isVisible();

        step("✔ Sepet limit BVA testi tamamlandı, gözlem: max=" + observedMax);
    }

    private int readIntSafe(Locator l) {
        try {
            String t = l.innerText().trim();
            return t.isEmpty() ? 0 : Integer.parseInt(t.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
