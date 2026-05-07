package com.yemeksepeti;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary value analysis on the home-page restaurant/food search input —
 * mobile YAML & W3 issue both call out equivalence partitioning on search
 * queries. Here we specifically probe the maximum input length the field
 * accepts (its `maxlength` attribute or implicit truncation).
 *
 * Approach: type a long sentinel string, then read the actual value from
 * the field. The reported length tells us the field's cap.
 */
class YemekSepetiSearchLimitTest extends BaseTest {

    /** How many chars to attempt. Should comfortably exceed any reasonable cap. */
    private static final int PROBE_LENGTH = 1500;

    @Test
    @DisplayName("BVA: arama çubuğu giriş limiti — uzun bir metin yazıp gerçek limiti ölç")
    void findSearchInputMaxLength() {
        step("Üst arama çubuğunu bul ve odakla");
        Locator topSearch = page.locator("[data-testid='new-search-input']").first();
        topSearch.waitFor();
        topSearch.click();

        // Read any explicit maxlength attribute first
        String mlAttr = topSearch.getAttribute("maxlength");
        System.out.println("    maxlength attribute = " + mlAttr);

        step("Sentinel uzun metin oluştur (" + PROBE_LENGTH + " karakter)");
        String longText = repeat("yemek", PROBE_LENGTH / 5 + 1).substring(0, PROBE_LENGTH);

        step("Metni alana doldur (fill — anlık atama, paste benzeri)");
        topSearch.fill(longText);
        page.waitForTimeout(800);

        String afterFill = String.valueOf(topSearch.inputValue());
        System.out.println("    after fill(): length=" + afterFill.length());

        step("Karakter karakter yaz (pressSequentially) — bazı alanlar fill'i kabul edip" +
             " keystroke filtrelemesini atlar; bu daha gerçekçi bir kullanıcı simülasyonu");
        topSearch.fill("");
        page.waitForTimeout(300);
        // Type slowly only the first 200 chars to keep the run bounded;
        // any per-keystroke cap will reveal itself well before that.
        topSearch.pressSequentially(longText.substring(0, Math.min(200, PROBE_LENGTH)),
                new Locator.PressSequentiallyOptions().setDelay(8));
        page.waitForTimeout(800);
        String afterType = String.valueOf(topSearch.inputValue());
        System.out.println("    after pressSequentially(200): length=" + afterType.length());

        int observed = Math.max(afterFill.length(), afterType.length());
        System.out.println("    [BVA RESULT] observed search-input max length: " + observed
                + (mlAttr != null ? "  (declared maxlength=" + mlAttr + ")" : ""));

        step("Sanity: en az 1 karakter kabul edildi");
        assertTrue(observed >= 1, "search input rejected even one char");

        step("✔ Arama girişi limit testi tamamlandı, gözlem: max=" + observed);
    }

    @Test
    @DisplayName("BVA: özel karakter / boş / tek karakter — sınır değerleri")
    void searchInputBoundaryPartitions() {
        Locator topSearch = page.locator("[data-testid='new-search-input']").first();
        topSearch.waitFor();

        // Boş / 1 karakter / typical / unicode / SQL-like / HTML-like
        String[] cases = new String[] {
                "",                 // empty
                "a",                // single char
                "lahmacun",         // typical
                "ÇĞİÖŞÜçğıöşü",     // Turkish unicode
                "🍕🍔🍣",           // emoji
                "<script>alert(1)</script>",  // HTML/JS sentinel (XSS smoke)
                "'; DROP TABLE x;--", // SQL sentinel
                "    leading spaces",
                "a".repeat(256)     // 256 chars
        };

        for (int i = 0; i < cases.length; i++) {
            String input = cases[i];
            step("Case #" + (i + 1) + ": " + summarize(input));
            topSearch.click();
            topSearch.fill(input);
            page.waitForTimeout(500);
            String accepted = String.valueOf(topSearch.inputValue());
            System.out.println("    accepted='" + summarize(accepted)
                    + "'  (in.len=" + input.length() + " out.len=" + accepted.length() + ")");
            // We don't assert specific behavior — purely characterisation.
            // What we DO assert: the page didn't crash (URL stayed on yemeksepeti).
            assertTrue(page.url().startsWith("https://www.yemeksepeti.com"),
                    "navigated away unexpectedly after input '" + summarize(input) + "'");
        }

        step("✔ Sınır değer eşdeğerlik testleri tamamlandı");
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    private static String summarize(String s) {
        if (s == null) return "<null>";
        String head = s.length() <= 30 ? s : s.substring(0, 27) + "...";
        return head.replace("\n", "\\n");
    }
}
