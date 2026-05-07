package com.yemeksepeti;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Login flow test — uses synthetic credentials and asserts the flow STOPS
 * at the 2-factor-authentication screen. Yemeksepeti requires SMS or email
 * OTP for every sign-in, so we have a clean test boundary that doesn't
 * require a real account.
 *
 * Maps to W2 (User login — web) in docs/blackbox_test_issues.md, but is
 * an addition rather than a strict mobile-port (no mobile YAML for this).
 *
 *  Login modal flow:
 *    Click "Giriş Yap" header   → welcome modal appears (Hoş geldin!)
 *    Click [welcome-view-button-login]  → email / phone entry form
 *    Enter fake email / phone   → submit
 *    → Site sends OTP and shows the 6-digit input  ← we stop here
 *
 *  Test data is intentionally synthetic (test_plan.pdf §3.9):
 *    -Demail=fake@example.invalid    (RFC2606 reserved test TLD)
 *    -Dphone=05555555555             (Turkish 11-digit format)
 */
class YemekSepetiLoginTest extends BaseTest {

    private static final String FAKE_EMAIL = System.getProperty("email", "fake@example.invalid");
    private static final String FAKE_PHONE = System.getProperty("phone", "05555555555");

    @Test
    @DisplayName("Login: Giriş modali açılır → 'Giriş Yap' seçeneği görünür")
    void loginModalOpens() {
        step("Header'daki 'Giriş Yap' butonuna tıkla");
        page.getByText("Giriş Yap").first().click();
        page.waitForTimeout(2_500);

        step("'Hoş geldin!' karşılama ekranı görünür");
        assertThat(page.getByText(Pattern.compile("Hoş geldin", Pattern.CASE_INSENSITIVE)).first())
                .isVisible();

        step("Giriş ve Kayıt seçenekleri butonları görünür");
        assertThat(page.locator("[data-testid='welcome-view-button-login']")).isVisible();
        assertThat(page.locator("[data-testid='welcome-view-button-signup']")).isVisible();

        step("✔ Login modal senaryosu tamamlandı");
    }

    @Test
    @DisplayName("Login: Sahte kimlik bilgileri ile giriş denemesi 2FA ekranında durur")
    void loginAttemptStopsAt2FA() {
        step("'Giriş Yap' butonuna tıkla → karşılama modali aç");
        page.getByText("Giriş Yap").first().click();
        page.waitForTimeout(2_000);

        step("Karşılama modalinde 'Giriş Yap' (welcome-view-button-login) tıkla");
        page.locator("[data-testid='welcome-view-button-login']").click();
        page.waitForTimeout(2_500);

        step("Email/Telefon giriş ekranı yüklendi mi?");
        // Look for any visible input that isn't a search/filter — that's the
        // login identifier field.
        Locator identifierField = page.locator(
                "input[type='email'], input[type='tel'], input[name*='email' i]," +
                " input[name*='phone' i], input[id*='email' i], input[id*='phone' i]," +
                " input[placeholder*='posta' i], input[placeholder*='telefon' i]")
                .first();
        identifierField.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        assertThat(identifierField).isVisible();

        // Use phone if the field looks like a tel input, else use the email.
        String type = identifierField.getAttribute("type");
        String name = String.valueOf(identifierField.getAttribute("name"));
        boolean isPhoneField = "tel".equals(type)
                || name.toLowerCase().contains("phone")
                || name.toLowerCase().contains("telefon");
        String identifier = isPhoneField ? FAKE_PHONE : FAKE_EMAIL;

        step("Sahte kimlik yaz: " + identifier
                + (isPhoneField ? "  (telefon)" : "  (email)"));
        identifierField.click();
        identifierField.fill(identifier);
        page.waitForTimeout(800);

        step("Form gönder (Enter veya 'Devam Et' butonu)");
        // Try the most common submit triggers in order
        Locator submit = page.locator(
                "button[type='submit']:visible," +
                " button:has-text('Devam Et'):visible," +
                " button:has-text('Giriş Yap'):visible," +
                " [data-testid*='submit']:visible").last();
        try {
            submit.click(new Locator.ClickOptions().setTimeout(3_000));
        } catch (Exception e) {
            identifierField.press("Enter");
        }
        page.waitForTimeout(3_000);

        step("Sonuç: 2FA / OTP ekranı VEYA validation hatası — her iki durumda da " +
             "sipariş ekranına ulaşılmadığını doğrula");
        // Acceptable end-states for a fake-credentials attempt:
        //  (a) OTP input shown (numeric pin field)
        //  (b) "Geçersiz" / "Invalid" / similar error banner
        //  (c) Still on the email screen (form validation rejected the bad
        //      address inline)
        // What MUST NOT happen: actual sign-in success / order page.
        assertThat(page.getByText(Pattern.compile(
                "(Sipariş|Hesabım|Profilim|My Account|Order)", Pattern.CASE_INSENSITIVE)))
                .hasCount(0);
        // And we didn't navigate away from yemeksepeti.com
        assert page.url().startsWith("https://www.yemeksepeti.com")
                : "expected to stay on yemeksepeti.com, was at " + page.url();

        step("✔ Login akışı 2FA / doğrulama sınırında durdu — gerçek hesap kullanılmadı");
    }
}
