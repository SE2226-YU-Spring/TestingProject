package com.yemeksepeti;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Web equivalent of blackbox/mobile/YemekSepetiCartTest.yaml.
 *
 * The mobile script asserts hard-coded prices ("210,00 TL" → "420,00 TL" → ...)
 * against one specific restaurant on mobile. On the live web list the chosen
 * restaurant differs per run, so we assert the *math* the mobile test
 * implicitly verified: doubling the quantity doubles the line total,
 * decrementing returns it to the original, and removing empties the cart.
 *
 *  Web selectors discovered for this restaurant menu:
 *    [data-testid='menu-product']                — product card LI
 *    [data-testid='menu-product-name']           — product name span
 *    [data-testid='menu-product-price']          — "860 TL" etc.
 *    [data-testid='quantity-stepper-collapsed-button'] — the "+" / add button
 *    [data-testid='quantity-stepper-quantity']   — current quantity (text)
 *    [data-testid='quantity-stepper-remove-button'] — "−" / remove button
 */
class YemekSepetiCartTest extends BaseTest {

    private static final Pattern PRICE_TL = Pattern.compile(
            "(\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{2})?)\\s*(?:TL|₺)");

    @Test
    @DisplayName("Sepet: ürün ekle, miktar artır/azalt ve kaldır akışı (fiyat matematiği)")
    void cartAddIncrementDecrementRemove() {
        // Use the "Express teslimat" lane — those restaurants are
        // pre-filtered by yemeksepeti to deliver to the user's geo-IP
        // location, so add-to-cart works without "Adresiniz nedir?" prompts.
        step("Express teslimat lane'inden bir restoran seç ve ilk ürünü sepete ekle");
        String chosenUrl = openExpressRestaurantAndAddFirstProduct(8);
        assertTrue(chosenUrl != null,
                "no Express-lane restaurant accepted the add-to-cart flow");
        System.out.println("    chosen: " + chosenUrl);

        step("Eklenen ürünü ve birim fiyatını oku");
        Locator firstProduct = page.locator("[data-testid='menu-product']").first();
        firstProduct.scrollIntoViewIfNeeded();
        double unitPrice = parseTL(firstProduct.locator("[data-testid='menu-product-price']")
                .first().innerText());
        assertTrue(unitPrice > 0, "expected a parseable unit price, got " + unitPrice);
        System.out.println("    unit price = " + unitPrice + " TL");

        Locator removeBtn = firstProduct.locator("[data-testid='quantity-stepper-remove-button']").first();
        Locator qty = firstProduct.locator("[data-testid='quantity-stepper-quantity']").first();
        java.util.function.Supplier<Locator> plusBtn = () -> firstProduct.locator(
                "[data-testid='quantity-stepper-collapsed-button']," +
                " [data-testid='quantity-stepper-add-button']," +
                " button:has([data-testid='quantity-stepper-plus-icon'])").first();

        step("Sepetteki ürün miktarı = 1 (findDelivering... + 1 ekledi)");
        assertThat(qty).hasText("1");

        step("(+1) Aynı üründen bir tane daha — qty 1 → 2");
        plusBtn.get().click();
        assertThat(qty).hasText("2");
        double doubled = unitPrice * 2;
        System.out.println("    expecting doubled total " + doubled + " TL on screen");
        assertThat(page.getByText(Pattern.compile(formatTL(doubled), Pattern.CASE_INSENSITIVE)).first())
                .isVisible();

        step("Sepet ikonuna tıkla — sepet kenar paneli açılsın ve içeriği göster");
        String productName = firstProduct.locator("[data-testid='menu-product-name']")
                .first().innerText();
        System.out.println("    expected product in sidebar: " + productName);
        openCartSidebar();
        // The sidebar lists the product we added with the doubled total
        assertThat(page.getByText(productName).first()).isVisible();
        assertThat(page.getByText(Pattern.compile(formatTL(doubled), Pattern.CASE_INSENSITIVE)).first())
                .isVisible();

        step("Sepet panelini kapat (Escape)");
        page.keyboard().press("Escape");
        page.waitForTimeout(800);

        step("(−1) Bir tane azalt — qty 2 → 1");
        removeBtn.click();
        assertThat(qty).hasText("1");

        step("(−1) Son ürünü kaldır — qty 1 → 0, sepet boş");
        removeBtn.click();
        page.waitForTimeout(800);
        assertThat(plusBtn.get()).isVisible();
        assertThat(page.locator("[data-testid='menu-product']").first()).isVisible();

        step("Sepet ikonu tekrar pasif duruma döndü");
        assertThat(page.locator("button[aria-label*='boş görünüyor' i]").first()).isVisible();

        step("✔ Sepet senaryosu tamamlandı");
    }

    /** Parse a Turkish-formatted TL price like "860 TL" or "210,00 TL" → double. */
    private static double parseTL(String s) {
        Matcher m = PRICE_TL.matcher(s);
        if (!m.find()) return 0;
        String raw = m.group(1).replace(".", "").replace(" ", "").replace(",", ".");
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Format a price as "1720 TL" or "210,00 TL" — accepts both formats since
     *  the site uses either depending on context. */
    private static String formatTL(double v) {
        long whole = (long) v;
        if (Math.abs(v - whole) < 0.005) {
            return whole + "\\s*TL";
        }
        return String.format("%d,%02d\\s*TL", whole, Math.round((v - whole) * 100));
    }
}
