package com.yemeksepeti;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Web equivalent of blackbox/mobile/YemekSepetiPayTest.yaml.
 *
 * Senaryo 1 (mobile address-book editing) lives behind login on the web. The
 * web port exercises the un-authed location modal —
 * docs/blackbox_test_issues.md flags the persistence half as a known gap.
 *
 * Senaryo 2 (mobile payment-method picker inside checkout) also requires
 * login. The same payment-type values (`cash`, `yemekpay_creditcard`,
 * `yemekpay_cardondelivery`, `yemekpay_cardpayment`, `craftgate_edenred`)
 * are exposed as filter radios on the home page, so we verify those.
 *
 * No real payment is ever submitted (test_plan.pdf §1.3).
 */
class YemekSepetiPayTest extends BaseTest {

    @Test
    @DisplayName("Senaryo 1 (web port): Adres modali — yeni adres yaz, öneri seç")
    void scenario1_addressModalNewAddress() {
        step("Adres modalını aç");
        page.locator("[data-testid='location-search-button']").click();
        page.waitForTimeout(1_500);

        step("'Hatay' yaz");
        Locator addressInput = page.locator("#delivery-information-postal-index").first();
        addressInput.click();
        addressInput.pressSequentially("Hatay",
                new Locator.PressSequentiallyOptions().setDelay(60));
        page.waitForTimeout(2_000);

        step("Mobile YAML index:1 → ikinci öneriye tıkla");
        Locator suggestions = page.locator("[data-testid='address-suggestion-item']");
        if (suggestions.count() > 1) {
            suggestions.nth(1).click();
        } else {
            suggestions.first().click();
        }
        page.waitForTimeout(2_000);
        page.keyboard().press("Escape");
        page.waitForTimeout(800);

        step("Konum butonu hâlâ görünür (modal kapandı)");
        assertThat(page.locator("[data-testid='location-search-button']").first()).isVisible();

        step("✔ Senaryo 1 tamamlandı");
    }

    @Test
    @DisplayName("Senaryo 2 (web port): Ödeme yöntemi filtreleri seçilebilir")
    void scenario2_paymentMethodFiltersAreSelectable() {
        Locator cash = page.locator("input#cash, input[data-testid='cash']").first();
        Locator creditCard = page.locator(
                "input#yemekpay_creditcard, input[data-testid='yemekpay_creditcard']").first();
        Locator cardOnDelivery = page.locator(
                "input#yemekpay_cardondelivery, input[data-testid='yemekpay_cardondelivery']").first();

        Locator.CheckOptions force = new Locator.CheckOptions().setForce(true);

        step("'Nakit' filtresini seç (mobile: Nakit → Onayla)");
        cash.scrollIntoViewIfNeeded();
        cash.check(force);
        assertThat(cash).isChecked();

        step("'Online Kredi Kartı' filtresini seç");
        creditCard.check(force);
        assertThat(creditCard).isChecked();

        step("'Kapıda Temassız Kartla Ödeme' filtresini seç");
        cardOnDelivery.check(force);
        assertThat(cardOnDelivery).isChecked();

        step("Sipariş Onaylandı ekranına ulaşılmadığını doğrula (gerçek ödeme yok)");
        Pattern unreachable = Pattern.compile(
                "(Sipariş Onaylandı|Order Placed)", Pattern.CASE_INSENSITIVE);
        assertThat(page.getByText(unreachable)).hasCount(0);

        step("✔ Senaryo 2 tamamlandı");
    }

    @Test
    @DisplayName("Senaryo 3 (web port): Sepet panelinde 'Sepeti Onayla' login'e yönlendiriyor")
    void scenario3_cartCheckoutRedirectsToLogin() {
        step("Adres seç (Üniversite 2) ve doğrula");
        selectAddress("Üniversite 2");

        step("İlk restorana git ve onboarding'i kapat");
        Locator firstRestaurant = page.locator("a[href*='/restaurant/']").first();
        firstRestaurant.waitFor();
        clickAndWait(firstRestaurant);
        dismissFloatingTooltips();

        step("İlk ürünü sepete ekle");
        Locator firstProduct = page.locator("[data-testid='menu-product']").first();
        firstProduct.waitFor();
        firstProduct.scrollIntoViewIfNeeded();
        firstProduct.locator("[data-testid='quantity-stepper-collapsed-button']").first().click();
        page.waitForTimeout(1500);

        step("Sepet kenar panelini aç");
        openCartSidebar();

        step("Sepet panelinde teslimat ücreti / toplam tutar görünür");
        // After items are added, the sidebar surfaces a "Sepeti Onayla" CTA
        // and a total. The exact testid varies; we look for either by text.
        Locator confirmCta = page.getByText(
                Pattern.compile("(Sepeti Onayla|Devam Et|Confirm Cart|Checkout)",
                        Pattern.CASE_INSENSITIVE)).first();
        assertThat(confirmCta).isVisible();

        step("'Sepeti Onayla' tıklanır → giriş zorunlu (no real payment)");
        confirmCta.click();
        page.waitForTimeout(2500);
        // Either a login modal opens (Hoş geldin!) OR the URL navigates to
        // /giris / /login, OR an inline auth wall shows. Any of those is
        // acceptable proof that checkout is gated by login.
        Locator loginIndicator = page.locator(
                "[data-testid='welcome-view-button-login']," +
                " text=/Hoş geldin|Giriş Yap|Sign in|Log in/i").first();
        assertThat(loginIndicator).isVisible();

        step("Sipariş onayı ekranına asla ulaşılmadığını doğrula");
        assertThat(page.getByText(Pattern.compile(
                "(Sipariş Onaylandı|Order Placed)", Pattern.CASE_INSENSITIVE))).hasCount(0);

        step("✔ Senaryo 3 tamamlandı (test_plan.pdf §1.3 — gerçek ödeme yok)");
    }
}
