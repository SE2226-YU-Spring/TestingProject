package com.yemeksepeti;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseTest {

    protected static final String BASE_URL = "https://www.yemeksepeti.com/";

    /** Singleton playwright/context/page — shared across ALL test classes
     *  in the JVM so PerimeterX is a one-time event per test run, not per
     *  class. */
    private static Playwright playwrightSingleton;
    private static BrowserContext contextSingleton;
    private static Page pageSingleton;

    protected Playwright playwright;
    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    void setUpClass() throws Exception {
        if (contextSingleton == null) {
            initSharedBrowser();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (contextSingleton != null) try { contextSingleton.close(); } catch (Exception ignored) {}
                if (playwrightSingleton != null) try { playwrightSingleton.close(); } catch (Exception ignored) {}
            }));
        }
        playwright = playwrightSingleton;
        context = contextSingleton;
        page = pageSingleton;
    }

    private void initSharedBrowser() throws Exception {
        playwrightSingleton = Playwright.create();
        playwright = playwrightSingleton;
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));

        // Default: a dedicated profile under target/. Set -DuseRealProfile=true
        // to clone the system Chrome profile (cookies + installed extensions
        // like Buster) into a separate directory — Chrome refuses DevTools
        // debugging on the default profile path, so we copy first.
        Path userDataDir = Paths.get(System.getProperty(
                "userDataDir",
                Paths.get("target", "chrome-profile").toAbsolutePath().toString()));

        if (Boolean.parseBoolean(System.getProperty("useRealProfile", "false"))) {
            Path realProfile = Paths.get(System.getProperty("user.home"),
                    ".config", "google-chrome");
            Path clone = Paths.get("target", "chrome-profile-cloned").toAbsolutePath();
            if (!Files.exists(clone.resolve("Default"))) {
                System.out.println("[profile] cloning " + realProfile + " → " + clone
                        + "  (one-time, ~30 s)");
                Files.createDirectories(clone);
                // Copy Default subdir (where extensions / cookies / preferences live).
                Path src = realProfile.resolve("Default");
                Path dst = clone.resolve("Default");
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            "cp", "-r", src.toString(), dst.toString());
                    pb.inheritIO();
                    pb.start().waitFor();
                    // Also copy Local State (registers known profiles)
                    Path localState = realProfile.resolve("Local State");
                    if (Files.exists(localState)) {
                        Files.copy(localState, clone.resolve("Local State"));
                    }
                    // Wipe session-restore so Chrome doesn't reopen all your
                    // tabs (would queue behind our about:blank navigation
                    // and trip the load timeout).
                    String[] sessionFiles = {
                            "Current Session", "Current Tabs",
                            "Last Session", "Last Tabs",
                            "Sessions"
                    };
                    for (String f : sessionFiles) {
                        Path p = dst.resolve(f);
                        if (Files.exists(p)) {
                            new ProcessBuilder("rm", "-rf", p.toString())
                                    .inheritIO().start().waitFor();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[profile] clone failed: " + e.getMessage());
                }
            } else {
                System.out.println("[profile] reusing existing clone at " + clone);
            }
            userDataDir = clone;
        }
        Files.createDirectories(userDataDir);

        // Real Google Chrome (channel "chrome") clears the bulk of yemeksepeti.com's
        // anti-bot fingerprinting that bundled Chromium trips. Set -Dchannel=chromium
        // to fall back to bundled.
        String channel = System.getProperty("channel", "chrome");

        // Load any captcha-solver extensions found in the user's regular Chrome
        // profile (Buster, Anti-Captcha Blocker, NopeCHA, etc.). They live as
        // unpacked dirs at ~/.config/google-chrome/Default/Extensions/<id>/<ver>/.
        String extensionPaths = discoverExtensionPaths();

        java.util.ArrayList<String> args = new java.util.ArrayList<>(List.of(
                "--disable-blink-features=AutomationControlled",
                "--exclude-switches=enable-automation",
                "--disable-infobars"));
        if (!extensionPaths.isEmpty()) {
            args.add("--load-extension=" + extensionPaths);
            args.add("--disable-extensions-except=" + extensionPaths);
            System.out.println("[ext] loading extensions: " + extensionPaths);
        }

        // -DslowMo=500 inserts a 500ms pause between every Playwright action,
        // so you can watch the demo at a human pace.
        long slowMoMs = Long.parseLong(System.getProperty("slowMo", "0"));

        BrowserType.LaunchPersistentContextOptions opts =
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(headless)
                        .setLocale("tr-TR")
                        .setTimezoneId("Europe/Istanbul")
                        .setViewportSize(1366, 900)
                        .setSlowMo(slowMoMs)
                        .setArgs(args)
                        .setIgnoreDefaultArgs(List.of("--enable-automation"));
        if (!"chromium".equalsIgnoreCase(channel)) {
            opts.setChannel(channel);
        }

        contextSingleton = playwright.chromium().launchPersistentContext(userDataDir, opts);
        context = contextSingleton;

        // Comprehensive stealth init script — overrides the navigator/window
        // properties PerimeterX sniffs to detect automation. Modeled on the
        // tactics used by puppeteer-extra-plugin-stealth.
        context.addInitScript(
                "(() => {" +
                "  Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                "  Object.defineProperty(navigator, 'languages', {get: () => ['tr-TR','tr','en-US','en']});" +
                "  Object.defineProperty(navigator, 'plugins', {get: () => [" +
                "    {name:'Chrome PDF Plugin',description:'Portable Document Format'}," +
                "    {name:'Chrome PDF Viewer',description:''}," +
                "    {name:'Native Client',description:''}" +
                "  ]});" +
                "  Object.defineProperty(navigator, 'mimeTypes', {get: () => [{type:'application/pdf'}]});" +
                "  window.chrome = window.chrome || {};" +
                "  window.chrome.runtime = window.chrome.runtime || {};" +
                "  window.chrome.app = window.chrome.app || {isInstalled:false};" +
                "  window.chrome.csi = window.chrome.csi || (function(){return {};});" +
                "  window.chrome.loadTimes = window.chrome.loadTimes || (function(){return {};});" +
                "  const origPerm = window.navigator.permissions && window.navigator.permissions.query;" +
                "  if (origPerm) {" +
                "    window.navigator.permissions.query = (params) =>" +
                "      params.name === 'notifications'" +
                "        ? Promise.resolve({state: Notification.permission})" +
                "        : origPerm.call(window.navigator.permissions, params);" +
                "  }" +
                "  const getParam = WebGLRenderingContext.prototype.getParameter;" +
                "  WebGLRenderingContext.prototype.getParameter = function(p) {" +
                "    if (p === 37445) return 'Intel Inc.';" +
                "    if (p === 37446) return 'Intel Iris OpenGL Engine';" +
                "    return getParam.call(this, p);" +
                "  };" +
                "  Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});" +
                "  Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});" +
                "  Object.defineProperty(navigator, 'platform', {get: () => 'Linux x86_64'});" +
                "  if (navigator.userAgentData) {" +
                "    Object.defineProperty(navigator, 'userAgentData', {get: () => undefined});" +
                "  }" +
                "})();");

        pageSingleton = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        page = pageSingleton;
        // Use domcontentloaded — yemeksepeti's analytics scripts can take >30s
        // to fully load, but the DOM is ready well before that.
        page.navigate(BASE_URL, new Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(60_000));
        page.waitForTimeout(2_000);
        waitOutCaptchaIfPresent();
        dismissCookieAndLocationPrompts();
    }

    @BeforeEach
    void resetToHome() {
        if (!page.url().equals(BASE_URL) && !page.url().startsWith(BASE_URL + "?")) {
            page.navigate(BASE_URL, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(60_000));
            page.waitForTimeout(1_000);
            waitOutCaptchaIfPresent();
            dismissCookieAndLocationPrompts();
        }
        dismissFloatingTooltips();
    }

    /**
     * yemeksepeti.com renders onboarding/discovery tooltips as floating-ui
     * portals (e.g., "Tüm sepetlerin tek bir yerde"). They intercept clicks on
     * underlying elements. Press Escape and click any close buttons to clear.
     */
    protected void dismissFloatingTooltips() {
        page.keyboard().press("Escape");
        page.waitForTimeout(300);
        clickIfVisible("[data-floating-ui-portal] button[aria-label*='kapat' i]");
        clickIfVisible("[data-floating-ui-portal] button[aria-label*='close' i]");
        clickIfVisible("[data-floating-ui-portal] button:has-text('Anladım')");
        clickIfVisible("[data-floating-ui-portal] button:has-text('Tamam')");
        clickIfVisible("[data-floating-ui-portal] button:has-text('Kapat')");
        // Yemeksepeti's onboarding tooltips ("Super Restoran nedir?", "Tüm
        // sepetlerin tek bir yerde", etc.) put the close-X as the FIRST
        // button in the portal subtree (tooltip header). Click it.
        try {
            com.microsoft.playwright.Locator portals = page.locator("[data-floating-ui-portal]");
            int n = portals.count();
            for (int i = 0; i < Math.min(n, 4); i++) {
                com.microsoft.playwright.Locator portal = portals.nth(i);
                if (!portal.isVisible()) continue;
                com.microsoft.playwright.Locator firstBtn = portal.locator("button").first();
                if (firstBtn.count() > 0 && firstBtn.isVisible()) {
                    try {
                        firstBtn.click(new com.microsoft.playwright.Locator.ClickOptions()
                                .setTimeout(1_000));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        page.keyboard().press("Escape");
        page.waitForTimeout(200);
    }

    // Browser cleanup runs via the JVM shutdown hook registered in setUpClass.
    // We deliberately do NOT close the context per-class so it stays warm
    // across YemekSepetiSearchTest → YemekSepetiCartTest → YemekSepetiPayTest.

    /**
     * If the page is currently the PerimeterX captcha wall, wait for the user
     * to solve it (headed mode). In headless we fail loudly so the user knows
     * to run headed once.
     */
    protected void waitOutCaptchaIfPresent() {
        if (!isOnCaptcha()) return;
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));
        if (headless) {
            throw new IllegalStateException(
                    "Hit a PerimeterX/reCAPTCHA wall in headless mode. Run once with "
                  + "-Dheadless=false, solve the captcha, then re-run.");
        }
        long timeoutMs = Long.parseLong(System.getProperty("captchaTimeoutMs", "300000"));
        System.out.println(">>> Captcha detected. Solve it in the open browser; "
                + "the test will resume automatically (waiting up to "
                + (timeoutMs / 1000) + "s).");
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (isOnCaptcha() && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(1_000);
        }
        if (isOnCaptcha()) {
            throw new IllegalStateException(
                    "Captcha not solved within " + (timeoutMs / 1000) + " seconds.");
        }
        page.waitForLoadState();
        page.waitForTimeout(1_500);
    }

    private boolean isOnCaptcha() {
        String title;
        try {
            title = page.title();
        } catch (Exception e) {
            return false;
        }
        if (title != null && Pattern.compile(
                "(Access to this page has been denied|Devam edebilmemiz)",
                Pattern.CASE_INSENSITIVE).matcher(title).find()) {
            return true;
        }
        try {
            return page.locator(
                    "text=/Devam edebilmemiz|Ben robot değilim|Press & Hold/i")
                    .first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    /** Click a locator and wait for the destination to settle, transparently
     *  handling a captcha challenge that appears on navigation. */
    protected void clickAndWait(com.microsoft.playwright.Locator target) {
        target.click();
        // Wait for DOM ready, not "load" — yemeksepeti's analytics scripts
        // can take >30s to settle and would block us on the load event.
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(30_000));
        } catch (Exception ignored) {
        }
        page.waitForTimeout(1_500);
        waitOutCaptchaIfPresent();
    }

    /**
     * Pick "Üniversite 2" via the address modal and COMMIT it. Selecting a
     * suggestion only fills the input — the modal stays open until you click
     * the "Bu Adresi Kullan" (Use This Address) confirm button. Without that
     * step, cart actions later fail with the "Adresiniz nedir?" prompt.
     */
    protected void selectAddress(String query) {
        page.locator("[data-testid='location-search-button']").click();
        page.waitForTimeout(1_500);

        com.microsoft.playwright.Locator input = page.locator("#delivery-information-postal-index").first();
        input.click();
        input.pressSequentially(query,
                new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(60));
        page.waitForTimeout(2_000);

        page.locator("[data-testid='address-suggestion-item']").first().click();
        page.waitForTimeout(1_500);

        // Commit the address — modal stays open without this click.
        com.microsoft.playwright.Locator confirm = page.locator(
                "[data-testid='location-search-go-icon'], button[aria-label='Bu Adresi Kullan']").first();
        confirm.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(10_000));
        confirm.click();

        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(30_000));
        } catch (Exception ignored) {
        }
        page.waitForTimeout(2_000);
        // Belt-and-braces: dismiss any leftover overlay (onboarding tooltip,
        // sign-in prompt, etc.) so subsequent clicks aren't intercepted.
        page.keyboard().press("Escape");
        page.waitForTimeout(800);
    }

    protected void dismissCookieAndLocationPrompts() {
        clickIfVisible("button:has-text('Kabul Et')");
        clickIfVisible("button:has-text('Tümünü Kabul Et')");
        clickIfVisible("button:has-text('Anladım')");
        clickIfVisible("#onetrust-accept-btn-handler");
        clickIfVisible("[data-testid='app-download-reminder__close-button']");
        clickIfVisible("[data-testid='pink-banner__close-button']");
    }

    protected void clickIfVisible(String selector) {
        try {
            var locator = page.locator(selector).first();
            locator.waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(2_000));
            locator.click();
        } catch (Exception ignored) {
        }
    }

    /** Print a labeled, visually distinct step marker so you can follow what's
     *  happening in the console while the browser does it. */
    protected void step(String label) {
        System.out.println("\n  ► " + label);
    }

    /**
     * Add a product to the cart via the "+" button. Handles three flavors:
     *  1. Direct add — qty just increments.
     *  2. Customization dialog opens — click "Sepete Ekle" inside it.
     *  3. "Adresiniz nedir?" modal opens — this restaurant doesn't deliver
     *     to our address. Dismiss the modal and return false so the caller
     *     can try a different restaurant.
     * Returns true if the cart quantity actually increased.
     */
    protected boolean addProductToCart(com.microsoft.playwright.Locator productCard) {
        com.microsoft.playwright.Locator plus = productCard.locator(
                "[data-testid='quantity-stepper-collapsed-button']," +
                " [data-testid='quantity-stepper-add-button']," +
                " button:has([data-testid='quantity-stepper-plus-icon'])").first();
        com.microsoft.playwright.Locator qty = productCard.locator(
                "[data-testid='quantity-stepper-quantity']").first();
        int before = readIntFromLocator(qty);

        plus.click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true));
        page.waitForTimeout(900);

        // "Adresiniz nedir?" — restaurant wants address re-verified for its
        // delivery zone. Click its "Adres Seç/Ekle" button to open the
        // restaurant-context address modal, re-pick first suggestion, retry.
        com.microsoft.playwright.Locator addrPrompt = page.locator(
                "[role='dialog']:has-text('Adresiniz nedir')," +
                " .bds-c-modal__dialog:has-text('Adresiniz nedir')").first();
        if (addrPrompt.count() > 0 && addrPrompt.isVisible()) {
            System.out.println("    (Adresiniz nedir? — re-verifying address for this restaurant)");
            try {
                page.locator("[role='dialog'] button:has-text('Adres Seç')," +
                             " [role='dialog'] button:has-text('Ekle')").first()
                        .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(2_000));
                page.waitForTimeout(2_000);
                // Now we're in the address-search modal. Re-type and confirm.
                com.microsoft.playwright.Locator input = page.locator("#delivery-information-postal-index").first();
                if (input.count() > 0 && input.isVisible()) {
                    String fallback = System.getProperty("address", "Maslak");
                    input.click();
                    input.fill("");
                    input.pressSequentially(fallback,
                            new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(50));
                    page.waitForTimeout(1_500);
                    com.microsoft.playwright.Locator suggestion = page.locator(
                            "[data-testid='address-suggestion-item']").first();
                    if (suggestion.count() > 0) {
                        suggestion.click();
                        page.waitForTimeout(1_500);
                    }
                    // Confirm with "Bu Adresi Kullan"
                    com.microsoft.playwright.Locator confirm = page.locator(
                            "[data-testid='location-search-go-icon']," +
                            " button[aria-label='Bu Adresi Kullan']").first();
                    if (confirm.count() > 0) {
                        confirm.click();
                        page.waitForTimeout(2_500);
                        page.keyboard().press("Escape");
                        page.waitForTimeout(500);
                    }
                }
            } catch (Exception e) {
                System.out.println("    (address re-verify failed: " + e.getMessage() + ")");
            }
            // Whether or not the address took, retry the + click ONCE
            page.waitForTimeout(800);
            try {
                plus.click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true));
                page.waitForTimeout(1_500);
            } catch (Exception ignored) {
            }
            return readIntFromLocator(qty) > before;
        }

        // Customization dialog opened (product needs options)
        com.microsoft.playwright.Locator dialogAdd = page.locator(
                "[role='dialog'] button:has-text('Sepete Ekle')," +
                " .bds-c-modal__dialog button:has-text('Sepete Ekle')," +
                " [role='dialog'] [data-testid*='add-to-cart']," +
                " [role='dialog'] [data-testid*='product-detail-add']").first();
        if (dialogAdd.count() > 0 && dialogAdd.isVisible()) {
            System.out.println("    (product opens customization dialog — clicking 'Sepete Ekle')");
            try {
                dialogAdd.click();
                page.waitForTimeout(1_500);
            } catch (Exception ignored) {
            }
        }

        return readIntFromLocator(qty) > before;
    }

    /**
     * Reach a restaurant from the home-page "Express teslimat" lane —
     * those are filtered by yemeksepeti to be in the user's actual delivery
     * zone, so add-to-cart will not get blocked by the "Adresiniz nedir?"
     * prompt. Adds the first product on the chosen restaurant.
     *
     * Tries the first {@code maxToTry} cards in the Express lane and
     * returns the URL of the first one whose first product successfully
     * adds, or null if none work.
     */
    protected String openExpressRestaurantAndAddFirstProduct(int maxToTry) {
        // The Express lane is the swimlane under H2 "Express teslimatlı restoranlar".
        com.microsoft.playwright.Locator expressHeader = page
                .getByText("Express teslimatlı restoranlar").first();
        try {
            expressHeader.scrollIntoViewIfNeeded();
            expressHeader.waitFor(
                    new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(8_000));
        } catch (Exception e) {
            System.out.println("    no Express lane on home page");
            return null;
        }

        // Get all restaurant links in the SAME swimlane container as the header.
        com.microsoft.playwright.Locator expressLane = page.locator(
                "section:has(h2:has-text('Express teslimatlı'))," +
                " div:has(> h2:has-text('Express teslimatlı'))").first();
        com.microsoft.playwright.Locator cards = expressLane.locator("a[href*='/restaurant/']");
        int n = Math.min(cards.count(), maxToTry);
        System.out.println("    Express lane restaurant count: " + cards.count() + " (will try " + n + ")");

        java.util.Set<String> tried = new java.util.HashSet<>();
        for (int i = 0; i < n; i++) {
            com.microsoft.playwright.Locator card = cards.nth(i);
            String href = card.getAttribute("href");
            if (href == null || tried.contains(href)) continue;
            tried.add(href);
            try {
                card.scrollIntoViewIfNeeded();
                clickAndWait(card);
            } catch (Exception e) {
                continue;
            }
            dismissFloatingTooltips();
            com.microsoft.playwright.Locator firstProduct = page.locator(
                    "[data-testid='menu-product']").first();
            try {
                firstProduct.waitFor(
                        new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(8_000));
            } catch (Exception e) {
                System.out.println("    [express #" + (i + 1) + "] " + href + " — no menu, skipping");
                page.goBack();
                page.waitForTimeout(1_000);
                continue;
            }
            firstProduct.scrollIntoViewIfNeeded();
            for (int retry = 1; retry <= 2; retry++) {
                dismissFloatingTooltips();
                if (addProductToCart(firstProduct)) {
                    System.out.println("    [express #" + (i + 1) + "] " + href + " — accepted");
                    return page.url();
                }
            }
            System.out.println("    [express #" + (i + 1) + "] " + href + " — add failed, next");
            page.goBack();
            page.waitForTimeout(1_500);
            dismissFloatingTooltips();
        }
        return null;
    }

    /**
     * Reach a restaurant detail page that's already address-validated for
     * the current user, by typing a chain-restaurant brand into the home
     * search bar (chains deliver across most of the country). Adds the first
     * product to the cart and returns the restaurant URL on success.
     */
    protected String openChainRestaurantAndAddFirstProduct(String brand) {
        com.microsoft.playwright.Locator topSearch = page.locator("[data-testid='new-search-input']");
        topSearch.waitFor();
        topSearch.click();
        topSearch.fill(brand);
        page.waitForTimeout(2_000);

        com.microsoft.playwright.Locator firstAutocomplete = page.locator(
                "li[role='option'], .search-autocomplete-item, [data-testid*='autocomplete']")
                .first();
        firstAutocomplete.waitFor(
                new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(8_000));
        clickAndWait(firstAutocomplete);

        // If the autocomplete landed us on a filtered list rather than a
        // detail page, click the first restaurant card from THAT list.
        if (!page.url().contains("/restaurant/")) {
            // Close any leftover search dropdown / cookie overlay first.
            page.keyboard().press("Escape");
            page.waitForTimeout(500);
            clickIfVisible("#usercentrics-root button:has-text('Tamam')");
            clickIfVisible("#usercentrics-root button[aria-label*='close' i]");
            clickIfVisible("button:has-text('Tümünü Kabul Et')");
            page.waitForTimeout(500);

            com.microsoft.playwright.Locator firstCard = page.locator("a[href*='/restaurant/']").first();
            firstCard.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(8_000));
            firstCard.scrollIntoViewIfNeeded();
            firstCard.click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true));
            try {
                page.waitForLoadState(
                        com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(30_000));
            } catch (Exception ignored) {
            }
            page.waitForTimeout(2_000);
            waitOutCaptchaIfPresent();
        }

        dismissFloatingTooltips();
        com.microsoft.playwright.Locator firstProduct = page.locator("[data-testid='menu-product']").first();
        firstProduct.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(10_000));
        firstProduct.scrollIntoViewIfNeeded();

        for (int attempt = 1; attempt <= 3; attempt++) {
            dismissFloatingTooltips();
            if (addProductToCart(firstProduct)) {
                return page.url();
            }
            System.out.println("    add attempt " + attempt + " failed — retrying");
        }
        return null;
    }

    private static int readIntFromLocator(com.microsoft.playwright.Locator l) {
        try {
            String t = l.innerText().trim().replaceAll("[^0-9]", "");
            return t.isEmpty() ? 0 : Integer.parseInt(t);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Click the header cart icon to open the cart sidebar. Yemeksepeti has
     * NO /sepet page (returns 404) — the cart is sidebar-only. The icon is
     * disabled (class `bds-is-disabled`) when the cart is empty, so this
     * MUST be called only after at least one product has been added.
     */
    protected void openCartSidebar() {
        com.microsoft.playwright.Locator cartBtn = page.locator(
                "button[aria-label*='Sepet' i]:not(.bds-is-disabled)").first();
        cartBtn.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(10_000));
        cartBtn.click();
        page.waitForTimeout(1_500);
    }

    protected void screenshot(String name) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("target", "screenshots", name + ".png"))
                .setFullPage(true));
    }

    /**
     * Returns a comma-separated list of unpacked extension directories from
     * ~/.config/google-chrome/Default/Extensions/<id>/<version>/. Empty string
     * if none are found. Override with -DextensionPaths=&lt;comma-list&gt;.
     */
    private String discoverExtensionPaths() {
        String override = System.getProperty("extensionPaths", "");
        if (!override.isEmpty()) return override;

        Path extRoot = Paths.get(System.getProperty("user.home"),
                ".config", "google-chrome", "Default", "Extensions");
        if (!Files.isDirectory(extRoot)) return "";

        java.util.List<String> found = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> ids = Files.list(extRoot)) {
            ids.filter(Files::isDirectory).forEach(idDir -> {
                try (java.util.stream.Stream<Path> versions = Files.list(idDir)) {
                    versions.filter(Files::isDirectory)
                            .filter(v -> Files.exists(v.resolve("manifest.json")))
                            .max(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                            .ifPresent(v -> found.add(v.toAbsolutePath().toString()));
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        return String.join(",", found);
    }
}
