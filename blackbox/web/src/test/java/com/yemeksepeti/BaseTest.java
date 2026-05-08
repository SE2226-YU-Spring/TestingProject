package com.yemeksepeti;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

import java.net.Socket;
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
    private static Browser cdpBrowserSingleton;
    private static Process chromeProcessSingleton;

    protected Playwright playwright;
    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    void setUpClass() throws Exception {
        if (contextSingleton == null) {
            // Default to CDP-attached real Chrome so installed extensions
            // (Buster captcha solver, etc.) are actually live for every
            // test class — Search/Login/Pay/Cart all benefit. Pass
            // -DconnectCDP=false to fall back to Playwright's launched
            // Chromium.
            if (Boolean.parseBoolean(System.getProperty("connectCDP", "true"))) {
                initCdpAttachedBrowser();
            } else {
                initSharedBrowser();
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (contextSingleton != null) try { contextSingleton.close(); } catch (Exception ignored) {}
                if (cdpBrowserSingleton != null) try { cdpBrowserSingleton.close(); } catch (Exception ignored) {}
                if (chromeProcessSingleton != null && chromeProcessSingleton.isAlive()) {
                    try { chromeProcessSingleton.destroy(); } catch (Exception ignored) {}
                }
                if (playwrightSingleton != null) try { playwrightSingleton.close(); } catch (Exception ignored) {}
            }));
        }
        playwright = playwrightSingleton;
        context = contextSingleton;
        page = pageSingleton;
    }

    /**
     * Auto-launch real Google Chrome under our control with the user's
     * extensions live (Buster captcha solver, etc.) and a remote debugging
     * port, then attach Playwright to it via CDP.
     *
     * Why this mode exists: when Playwright launches Chrome itself via
     * launchPersistentContext, real installed extensions are silently
     * disabled by Chrome's automation policy. --load-extension only
     * loads them as "unpacked dev" extensions, which Chrome neuters for
     * many real-world extensions (Buster being the classic one). The
     * fix is to launch Chrome outside Playwright's harness, against a
     * cloned copy of the user's actual profile dir — so extensions are
     * properly installed (with their stored permissions, IDs, and
     * background scripts), not loaded as developer-mode strangers.
     *
     * Activation: -DconnectCDP=true (off by default — only the cart
     * tests need it; the other 5 tests pass without).
     *
     * Profile: target/chrome-profile-cdp/ (a clone of
     * ~/.config/google-chrome/Default — Chrome refuses
     * --remote-debugging-port on the system profile path, hence the
     * clone). Cloning happens once on first run; pass
     * -DrecloneProfile=true to force re-clone, or
     * -DuserDataDir=/some/other/path to point elsewhere.
     */
    private void initCdpAttachedBrowser() throws Exception {
        playwrightSingleton = Playwright.create();
        playwright = playwrightSingleton;

        int port = Integer.parseInt(System.getProperty("cdpPort", "9223"));
        String chromeBin = System.getProperty("chromeBinary",
                resolveChromeBinaryOrThrow());
        Path userDataDir = Paths.get(System.getProperty(
                "userDataDir",
                Paths.get("target", "chrome-profile-cdp").toAbsolutePath().toString()));
        Files.createDirectories(userDataDir);

        // Clone the user's real Chrome profile (with all extensions
        // properly installed) into the CDP dir. Without this Chrome
        // launches with an empty profile + --load-extension dev mode,
        // and most real extensions are silently neutered.
        boolean reclone = Boolean.parseBoolean(System.getProperty("recloneProfile", "false"));
        cloneRealProfileIfNeeded(userDataDir, reclone);

        // Always strip stale SingletonLock/Cookie/Socket symlinks before
        // launch — if the previous test run died or the user's normal
        // Chrome opened on this dir, those symlinks point at a foreign PID
        // and Chrome would silently forward our `about:blank` to that
        // existing instance and exit ("Opening in existing browser
        // session.") leaving us without a CDP-attached process.
        for (String f : new String[]{"SingletonLock", "SingletonCookie", "SingletonSocket"}) {
            Path p = userDataDir.resolve(f);
            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
        }

        // If something is already listening on the port (e.g. the user
        // ran a helper script), skip launch and attach to that.
        if (!isPortOpen("127.0.0.1", port)) {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(chromeBin);
            cmd.add("--remote-debugging-port=" + port);
            cmd.add("--user-data-dir=" + userDataDir.toAbsolutePath().toString());
            cmd.add("--profile-directory=Default");
            cmd.add("--no-first-run");
            cmd.add("--no-default-browser-check");
            cmd.add("--disable-blink-features=AutomationControlled");
            cmd.add("--disable-features=Translate,InterestFeedContentSuggestions");
            cmd.add("--lang=tr-TR");
            cmd.add("--window-size=1366,900");
            cmd.add("about:blank");

            System.out.println("[cdp] launching: " + String.join(" ", cmd));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(
                    Paths.get("target", "chrome-cdp.log").toFile()));
            chromeProcessSingleton = pb.start();
        } else {
            System.out.println("[cdp] reusing existing Chrome on port " + port);
        }

        // Wait for the DevTools endpoint to come up. Cold-start of Chrome
        // with extensions can take 30-60 s on a freshly cloned profile, so
        // give it a generous window before declaring failure.
        long deadline = System.currentTimeMillis() + 90_000;
        while (!isPortOpen("127.0.0.1", port)) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException(
                        "Chrome did not open --remote-debugging-port=" + port
                                + " within 90 s. Check target/chrome-cdp.log.");
            }
            Thread.sleep(250);
        }
        Thread.sleep(800); // give DevTools a beat to settle

        cdpBrowserSingleton = playwright.chromium().connectOverCDP(
                "http://127.0.0.1:" + port);

        // The first context is Chrome's default profile context; that's
        // the one with our extensions and persistent cookies.
        if (cdpBrowserSingleton.contexts().isEmpty()) {
            throw new IllegalStateException("CDP-attached Chrome reported no contexts");
        }
        contextSingleton = cdpBrowserSingleton.contexts().get(0);
        context = contextSingleton;

        applyStealthInitScript(contextSingleton);

        pageSingleton = context.pages().isEmpty()
                ? context.newPage()
                : context.pages().get(0);
        page = pageSingleton;

        page.navigate(BASE_URL, new Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(60_000));
        page.waitForTimeout(2_000);
        waitOutCaptchaIfPresent();
        dismissCookieAndLocationPrompts();
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * One-time clone of ~/.config/google-chrome/Default into the CDP
     * user data dir, so launched Chrome sees all real installed
     * extensions (Buster, etc.) as already-installed rather than
     * dev-loaded. Wipes session-restore so Chrome doesn't reopen the
     * user's everyday tabs into our test session. Pass
     * -DrecloneProfile=true to wipe and re-clone.
     */
    private void cloneRealProfileIfNeeded(Path userDataDir, boolean force) throws Exception {
        Path defaultDir = userDataDir.resolve("Default");
        Path realProfile = Paths.get(System.getProperty("user.home"),
                ".config", "google-chrome");
        Path realDefault = realProfile.resolve("Default");

        if (!Files.isDirectory(realDefault)) {
            System.out.println("[cdp] no real Chrome profile at " + realDefault
                    + " — launching with an empty profile (no extensions).");
            return;
        }

        if (force && Files.exists(defaultDir)) {
            System.out.println("[cdp] -DrecloneProfile=true → wiping " + defaultDir);
            new ProcessBuilder("rm", "-rf", defaultDir.toString())
                    .inheritIO().start().waitFor();
            Path localState = userDataDir.resolve("Local State");
            if (Files.exists(localState)) Files.delete(localState);
        }

        if (Files.exists(defaultDir)) {
            // Pre-flight: warn if the user's real Chrome is currently running
            // on the same profile — it would have a SingletonLock that
            // prevents us starting a second instance from the cloned dir.
            // (Cloning still works, but launch may fail with profile-locked.)
            Path lock = realProfile.resolve("SingletonLock");
            if (Files.exists(lock) && Files.isSymbolicLink(lock)) {
                System.out.println("[cdp] note: your normal Chrome is running. "
                        + "The cloned profile will launch fine, but extensions installed "
                        + "AFTER the last clone won't be in it — pass "
                        + "-DrecloneProfile=true after closing Chrome to refresh.");
            }
            return;
        }

        System.out.println("[cdp] one-time clone of " + realDefault + " → " + defaultDir
                + "  (this can take ~30 s on a big profile)");
        Files.createDirectories(userDataDir);
        ProcessBuilder cp = new ProcessBuilder(
                "cp", "-r", realDefault.toString(), defaultDir.toString());
        cp.redirectErrorStream(true);
        cp.inheritIO();
        int rc = cp.start().waitFor();
        if (rc != 0) {
            throw new IllegalStateException("Profile clone failed (cp -r returned " + rc + ")");
        }

        // Carry over Local State (registers known profiles + tracks extensions)
        Path realLocalState = realProfile.resolve("Local State");
        if (Files.exists(realLocalState)) {
            Files.copy(realLocalState, userDataDir.resolve("Local State"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // Wipe session-restore so Chrome doesn't try to reopen all your
        // normal tabs in the test session.
        for (String f : new String[]{
                "Current Session", "Current Tabs",
                "Last Session", "Last Tabs",
                "Sessions"
        }) {
            Path p = defaultDir.resolve(f);
            if (Files.exists(p)) {
                new ProcessBuilder("rm", "-rf", p.toString())
                        .inheritIO().start().waitFor();
            }
        }
        // Also wipe the SingletonLock from the source — it points to your
        // running Chrome's PID and would refuse our launch.
        for (String f : new String[]{"SingletonLock", "SingletonCookie", "SingletonSocket"}) {
            Path p = userDataDir.resolve(f);
            if (Files.exists(p)) {
                try { Files.delete(p); } catch (Exception ignored) {}
            }
        }

        java.io.File extDir = defaultDir.resolve("Extensions").toFile();
        java.io.File[] extEntries = extDir.exists() && extDir.isDirectory()
                ? extDir.listFiles() : null;
        int extCount = extEntries == null ? 0 : extEntries.length;
        System.out.println("[cdp] cloned profile has " + extCount + " extension dir(s)");
    }

    private static String resolveChromeBinaryOrThrow() {
        for (String c : new String[]{
                "/usr/bin/google-chrome-stable",
                "/usr/bin/google-chrome",
                "/opt/google/chrome/chrome",
                "/snap/bin/chromium"
        }) {
            if (Files.isExecutable(Paths.get(c))) return c;
        }
        throw new IllegalStateException(
                "google-chrome binary not found. Pass -DchromeBinary=/path/to/chrome.");
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

        applyStealthInitScript(contextSingleton);

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

    /**
     * Apply the stealth init script to a context — overrides the
     * navigator/window properties PerimeterX sniffs to detect automation.
     * Modeled on the tactics from puppeteer-extra-plugin-stealth.
     */
    private void applyStealthInitScript(BrowserContext target) {
        target.addInitScript(
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
    }

    @BeforeEach
    void resetToHome() {
        // Always re-navigate to a clean home page between tests. Even when
        // the previous test left us on the same URL (e.g. scenario2 of
        // YemekSepetiSearchTest stays on '/'), the top-bar autocomplete
        // dropdown / typed search query / floating tooltip can persist
        // and intercept the next test's first click. A fresh navigation
        // resets that DOM state cheaply.
        page.navigate(BASE_URL, new Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(60_000));
        page.waitForTimeout(1_000);
        waitOutCaptchaIfPresent();
        dismissCookieAndLocationPrompts();
        dismissFloatingTooltips();
    }

    /**
     * Returns true when the page indicates a logged-in session.
     *
     * Multi-signal because no single selector is reliable:
     *   1) URL must NOT be on a /login/* route.
     *   2) "Giriş Yap" must NOT be visible inside the page header. (A welcome
     *      modal rendered at body root also contains "Giriş Yap" — that's
     *      not a signal, hence the header restriction.)
     *
     * Cheap probe — total budget under 1 s.
     */
    protected boolean isLoggedIn() {
        String url;
        try { url = page.url(); } catch (Exception e) { return false; }
        if (url.contains("/login/") || url.endsWith("/login")) return false;

        try {
            boolean loginInHeader = page.locator(
                    "header :is(button, a):has-text('Giriş Yap'),"
                  + " [class*='header'] :is(button, a):has-text('Giriş Yap'),"
                  + " nav :is(button, a):has-text('Giriş Yap')")
                    .first()
                    .isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(800));
            return !loginInHeader;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Clear the Yemeksepeti session and reload, leaving the browser in a
     * logged-out state. Used by the first Login test to start from a clean
     * slate so the welcome modal ("Hoş geldin!") is the entry point.
     *
     * Tries the user-menu logout first (which preserves the Google session
     * cookie on accounts.google.com, so the next test can sign back in
     * without driving the chooser). Falls back to clearing all cookies and
     * reloading.
     */
    protected void signOutIfSignedIn() {
        if (!isLoggedIn()) {
            System.out.println("    [auth] already signed out");
            return;
        }
        System.out.println("    [auth] currently signed in — signing out for the test");

        try {
            com.microsoft.playwright.Locator userMenu = page.locator(
                    "[data-testid='profile-button']," +
                    " [data-testid*='user-menu']," +
                    " header button:has-text('Hesabım')," +
                    " header [aria-label*='Hesab' i]").first();
            if (userMenu.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(2_000))) {
                userMenu.click();
                page.waitForTimeout(1_500);
                com.microsoft.playwright.Locator logout = page.locator(
                        "text=/Çıkış Yap|Çıkış yap|Sign out|Log out/i").first();
                if (logout.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(2_000))) {
                    logout.click();
                    page.waitForTimeout(3_000);
                    if (!isLoggedIn()) {
                        System.out.println("    [auth] signed out via user menu");
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback: clear ONLY yemeksepeti cookies, preserve everything
        // else (especially the accounts.google.com session cookie — the
        // next test signs back in via the Google OAuth chooser, which
        // depends on that cookie being intact).
        try {
            java.util.List<com.microsoft.playwright.options.Cookie> keep = new java.util.ArrayList<>();
            for (com.microsoft.playwright.options.Cookie c : context.cookies()) {
                String d = c.domain == null ? "" : c.domain.toLowerCase();
                if (d.contains("yemeksepeti")) continue;
                keep.add(c);
            }
            context.clearCookies();
            if (!keep.isEmpty()) context.addCookies(keep);
            page.navigate(BASE_URL, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(60_000));
            page.waitForTimeout(2_000);
            waitOutCaptchaIfPresent();
            dismissCookieAndLocationPrompts();
            System.out.println("    [auth] signed out (yemeksepeti cookies cleared, "
                    + "Google session preserved)");
        } catch (Exception e) {
            System.out.println("    [auth] sign-out fallback failed: " + e.getMessage());
        }
    }

    /**
     * Sign in via "Google ile devam et". Relies on the cloned Chrome profile
     * already having a valid Google session cookie — Yemeksepeti's OAuth
     * popup will then auto-select the account and redirect back without
     * user interaction. If a popup needs manual driving (e.g. account
     * chooser, consent screen), we wait up to {@code loginTimeoutMs}
     * (default 90 s) for the user to complete it in the open browser.
     *
     * IMPORTANT (per user instruction and test_plan.pdf §1.3): this only
     * authenticates. It MUST NEVER be used as a stepping-stone toward
     * placing a real order. Cart tests stop at add/increment/decrement
     * inside the menu and the cart sidebar.
     */
    protected void ensureLoggedInWithGoogle() {
        if (isLoggedIn()) {
            System.out.println("    [auth] already logged in (cloned-profile cookie still valid)");
            return;
        }
        System.out.println("    [auth] not logged in — opening welcome modal");
        try {
            page.getByText("Giriş Yap").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5_000));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not find 'Giriş Yap' button on header: " + e.getMessage());
        }
        page.waitForTimeout(2_000);

        com.microsoft.playwright.Locator googleBtn = page.locator(
                "[data-testid*='google' i]," +
                " button:has-text('Google ile devam')," +
                " button:has-text('Google ile')," +
                " button:has(img[alt*='Google' i])," +
                " button:has(svg[aria-label*='Google' i])").first();
        try {
            googleBtn.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5_000));
        } catch (Exception e) {
            System.out.println("    [auth] no Google button found on welcome modal — "
                    + "drive sign-in manually in the open browser");
        }

        // Click the Google button. Yemeksepeti opens OAuth in a popup;
        // capture it so we can drive the account chooser. If there's no
        // popup (e.g. site uses an in-page redirect), the catch falls
        // through and we just poll the main page.
        com.microsoft.playwright.Page popup = null;
        try {
            popup = context.waitForPage(() -> {
                System.out.println("    [auth] clicking 'Google ile devam et'");
                googleBtn.click();
            });
        } catch (Exception e) {
            System.out.println("    [auth] no popup opened — assuming in-page OAuth redirect");
        }
        if (popup != null) {
            try {
                drivOAuthPopup(popup);
            } catch (Exception e) {
                System.out.println("    [auth] popup driver hit: " + e.getMessage()
                        + " — continuing with main-page polling");
            }
        }

        long timeoutMs = Long.parseLong(System.getProperty("loginTimeoutMs", "180000"));
        System.out.println("    [auth] waiting up to " + (timeoutMs / 1000)
                + "s for login (drive the Google chooser/consent in the browser if it pauses)");
        long deadline = System.currentTimeMillis() + timeoutMs;
        long lastStatus = 0;
        while (System.currentTimeMillis() < deadline) {
            if (isLoggedIn()) {
                System.out.println("    [auth] login confirmed (url=" + page.url() + ")");
                page.waitForTimeout(1_500);
                return;
            }
            // Every 15 s, tell the user what page we're stuck on so they
            // know whether to act in the browser.
            long now = System.currentTimeMillis();
            if (now - lastStatus > 15_000) {
                System.out.println("    [auth] still waiting... current url=" + safeUrl());
                lastStatus = now;
            }
            page.waitForTimeout(1_000);
        }
        throw new IllegalStateException(
                "Google sign-in did not complete within " + (timeoutMs / 1000) + " s. "
              + "Run with -DloginTimeoutMs=300000 for more time, OR sign in once manually "
              + "in the open Chrome window — the cloned profile keeps the cookie for next run. "
              + "Current url: " + safeUrl());
    }

    private String safeUrl() {
        try { return page.url(); } catch (Exception e) { return "(unavailable)"; }
    }

    /**
     * Drive Google's OAuth popup: pick the first listed account, click any
     * "Devam et" / "Continue" consent button, and wait for the popup to
     * close. We do NOT enter passwords — the cloned profile carries the
     * user's Google session cookie, so the chooser is the only step.
     */
    private void drivOAuthPopup(com.microsoft.playwright.Page popup) {
        try {
            popup.waitForLoadState(
                    com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new com.microsoft.playwright.Page.WaitForLoadStateOptions().setTimeout(15_000));
        } catch (Exception ignored) {
        }
        popup.waitForTimeout(1_000);
        System.out.println("    [auth] OAuth popup at " + popup.url());

        // 1) Account chooser tile.
        com.microsoft.playwright.Locator account = popup.locator(
                "div[data-identifier]," +
                " li[data-identifier]," +
                " div[data-account-id]," +
                " div[role='link'][data-identifier]").first();
        try {
            account.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(15_000));
            String who = account.getAttribute("data-identifier");
            if (who == null) who = account.getAttribute("data-email");
            System.out.println("    [auth] picking Google account: " + (who == null ? "(first listed)" : who));
            account.click();
        } catch (Exception e) {
            System.out.println("    [auth] no account-chooser tile within 15s — "
                    + "either Google asked for password (manual) or popup auto-redirected");
        }

        // 2) Possible consent / "Devam et" button on the OAuth scope page.
        try {
            popup.waitForLoadState(
                    com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new com.microsoft.playwright.Page.WaitForLoadStateOptions().setTimeout(8_000));
        } catch (Exception ignored) {
        }
        popup.waitForTimeout(500);
        for (String sel : new String[]{
                "button:has-text('Devam et')",
                "button:has-text('Devam Et')",
                "button:has-text('İzin ver')",
                "button:has-text('Continue')",
                "button:has-text('Allow')",
                "[role='button']:has-text('Devam')",
                "[role='button']:has-text('Continue')"
        }) {
            try {
                com.microsoft.playwright.Locator btn = popup.locator(sel).first();
                if (btn.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(800))) {
                    System.out.println("    [auth] clicking consent button: " + sel);
                    btn.click();
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        // 3) Wait for popup to close (means OAuth completed and Yemeksepeti
        //    received the callback). Cap at 30 s.
        long deadline = System.currentTimeMillis() + 30_000;
        while (!popup.isClosed() && System.currentTimeMillis() < deadline) {
            try { popup.waitForTimeout(500); } catch (Exception e) { break; }
        }
        if (popup.isClosed()) {
            System.out.println("    [auth] OAuth popup closed");
        } else {
            System.out.println("    [auth] OAuth popup still open after 30s — "
                    + "user may need to finish the consent flow manually");
        }
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
     * If the page is currently the PerimeterX captcha wall, click the
     * challenge to activate it (press-and-hold start, or reCAPTCHA
     * checkbox), then wait for installed extensions (Buster, NopeCHA, …)
     * to actually solve it. In headless we fail loudly so the user knows
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
        System.out.println(">>> Captcha detected — clicking the challenge so the "
                + "captcha-solver extension can take over (waiting up to "
                + (timeoutMs / 1000) + "s).");

        triggerCaptchaInteraction();

        long deadline = System.currentTimeMillis() + timeoutMs;
        long lastRetrigger = System.currentTimeMillis();
        while (isOnCaptcha() && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(1_000);
            // Re-poke the challenge every 20 s so a stuck press-and-hold
            // gets another mouse-down, and Buster's content script gets
            // another chance to attach to the reCAPTCHA bframe.
            if (System.currentTimeMillis() - lastRetrigger > 20_000) {
                triggerCaptchaInteraction();
                lastRetrigger = System.currentTimeMillis();
            }
        }
        if (isOnCaptcha()) {
            throw new IllegalStateException(
                    "Captcha not solved within " + (timeoutMs / 1000) + " seconds.");
        }
        page.waitForLoadState();
        page.waitForTimeout(1_500);
    }

    /**
     * Auto-click the visible captcha so the user's installed solver
     * extension (Buster for reCAPTCHA, manual for press-and-hold) can do
     * the actual work. The challenge often lives inside a child iframe
     * (PerimeterX hosts its press-and-hold UI in `_pxCaptcha` /
     * `#captcha-frame`; Google hosts the checkbox in `recaptcha/anchor`),
     * so we sweep the main frame AND every child frame.
     *
     * Tries, in order:
     *   1) PerimeterX press-and-hold — issues a real mousedown for ~12 s
     *      on the button's screen position, then mouseup. We use page
     *      coordinates because the button can be inside an iframe whose
     *      bounding box still maps onto the page's mouse area.
     *   2) Google reCAPTCHA v2 checkbox — Buster takes over from there.
     */
    private void triggerCaptchaInteraction() {
        // ---- 1) PerimeterX press-and-hold ----------------------------
        String[] holdSelectors = new String[]{
                "button:has-text('Press & Hold')",
                "button:has-text('Press and Hold')",
                "button:has-text('Basılı Tutun')",
                "button:has-text('Basılı Tut')",
                "div[role='button']:has-text('Press')",
                "div[role='button']:has-text('Basılı')",
                "[id*='px-captcha'] [role='button']",
                "[id*='px-captcha'] button",
                ".px-captcha-container [role='button']",
                "#px-captcha button",
                // Bare-text fallbacks — PerimeterX sometimes ships the
                // button as a non-semantic <div> with only inner text.
                "text=/Press & Hold/i",
                "text=/Basılı Tutun/i"
        };

        // Sweep the main frame plus every nested frame.
        for (com.microsoft.playwright.Frame frame : allFramesIncludingMain()) {
            for (String sel : holdSelectors) {
                try {
                    com.microsoft.playwright.Locator btn = frame.locator(sel).first();
                    if (btn.count() == 0) continue;
                    if (!btn.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions()
                            .setTimeout(400))) continue;
                    System.out.println("    [captcha] press-and-hold detected (sel=" + sel
                            + ", frame=" + (frame == page.mainFrame() ? "main" : frame.url())
                            + ") — holding for 12 s");
                    com.microsoft.playwright.options.BoundingBox box = btn.boundingBox();
                    if (box != null) {
                        double cx = box.x + box.width / 2;
                        double cy = box.y + box.height / 2;
                        page.mouse().move(cx, cy);
                        page.mouse().down();
                        page.waitForTimeout(12_000);
                        page.mouse().up();
                        page.waitForTimeout(1_500);
                    } else {
                        // Fallback if bounding box can't be resolved.
                        btn.click(new com.microsoft.playwright.Locator.ClickOptions()
                                .setForce(true).setDelay(12_000));
                    }
                    return;
                } catch (Exception ignored) {
                }
            }
        }

        // ---- 2) reCAPTCHA — challenge bframe (with Buster button) ----
        // When PerimeterX hands off to a reCAPTCHA challenge ("Devam
        // edebilmemiz için..."), the actual challenge UI lives in the
        // bframe (`/recaptcha/.../bframe`). Buster injects its own
        // yellow person-with-check button into that bframe — clicking
        // it triggers Buster's audio-challenge solver, which is what
        // actually clears the captcha. We click the bframe Buster
        // button first; if Buster isn't installed/visible there, we
        // fall back to the anchor-frame checkbox click.
        try {
            com.microsoft.playwright.Frame bframe = null;
            com.microsoft.playwright.Frame anchorFrame = null;
            for (com.microsoft.playwright.Frame f : page.frames()) {
                String url = f.url();
                if (url == null) continue;
                if (url.contains("/recaptcha/") && url.contains("bframe")) bframe = f;
                else if (url.contains("/recaptcha/") && url.contains("anchor")) anchorFrame = f;
            }

            if (bframe != null) {
                String[] busterSelectors = new String[]{
                        "#solver-button",
                        ".help-button-holder",
                        "button[title*='Buster' i]",
                        "button[aria-label*='Buster' i]",
                        "[id*='solver']",
                        // Generic: the Buster button is added next to the
                        // audio (headphone) and refresh buttons in the
                        // bottom-left of the bframe. Last button in that
                        // toolbar that isn't the verify CTA.
                        ".rc-buttons button:not([id*='verify'])"
                };
                for (String sel : busterSelectors) {
                    try {
                        com.microsoft.playwright.Locator btn = bframe.locator(sel).first();
                        if (btn.count() == 0) continue;
                        if (!btn.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions()
                                .setTimeout(400))) continue;
                        System.out.println("    [captcha] reCAPTCHA bframe — clicking Buster button (sel=" + sel + ")");
                        btn.click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true));
                        page.waitForTimeout(3_000);
                        return;
                    } catch (Exception ignored) {
                    }
                }
                System.out.println("    [captcha] reCAPTCHA bframe visible but Buster button not found — "
                        + "is the extension active?");
            }

            if (anchorFrame != null) {
                com.microsoft.playwright.Locator box = anchorFrame.locator(
                        "#recaptcha-anchor, .recaptcha-checkbox").first();
                if (box.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(800))) {
                    System.out.println("    [captcha] reCAPTCHA anchor checkbox — clicking to open challenge");
                    box.click();
                    page.waitForTimeout(2_000);
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        System.out.println("    [captcha] could not auto-click a known challenge — "
                + "solve it manually in the browser, the test is still polling");
    }

    /** Main frame plus every descendant frame, in iteration-safe order. */
    private java.util.List<com.microsoft.playwright.Frame> allFramesIncludingMain() {
        java.util.List<com.microsoft.playwright.Frame> out = new java.util.ArrayList<>();
        out.add(page.mainFrame());
        for (com.microsoft.playwright.Frame f : page.frames()) {
            if (f != page.mainFrame()) out.add(f);
        }
        return out;
    }

    private boolean isOnCaptcha() {
        // Detect ONLY the structural signals of a real captcha — title
        // pattern, px-captcha container, or a visible reCAPTCHA challenge
        // iframe. Pure text matching is too noisy: words like "Basılı Tut"
        // appear in unrelated UI hints on yemeksepeti, and matching them
        // produced false-positive captcha detection that turned every
        // resetToHome into a 2-minute wait for a captcha that never was.
        String title;
        try {
            title = page.title();
        } catch (Exception e) {
            return false;
        }
        if (title != null && Pattern.compile(
                "(Access to this page has been denied|Devam edebilmemiz için)",
                Pattern.CASE_INSENSITIVE).matcher(title).find()) {
            return true;
        }
        try {
            // PerimeterX press-and-hold container — most reliable
            // structural signal. Visible only when challenge is up.
            if (page.locator(
                    "#px-captcha:visible, [id^='px-captcha'][class*='captcha']:visible," +
                    " .px-captcha-container:visible")
                    .first().isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(300))) {
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            // reCAPTCHA challenge iframe (the bigger "select all images" frame),
            // not the anchor frame which is always present on pages that embed
            // reCAPTCHA invisibly.
            for (com.microsoft.playwright.Frame f : allFramesIncludingMain()) {
                String url = f.url();
                if (url != null && url.contains("/recaptcha/") && url.contains("bframe")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Click a locator and wait for the destination to settle, transparently
     *  handling a captcha challenge OR the "Adresiniz nedir?" overlay
     *  that appears on a restaurant detail page. */
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
        handleAddressPromptIfPresent();
    }

    /**
     * If the "Adresiniz nedir?" modal is currently overlaying the page,
     * recover by clicking "Adres Seç/Ekle", re-typing the configured
     * `-Daddress` (default "Üniversite 2"), picking the first suggestion,
     * and clicking "Bu Adresi Kullan". Returns true if the modal was
     * present and we attempted recovery, false if no modal was visible.
     *
     * This modal can appear two ways:
     *   1) On a restaurant detail page load — Yemeksepeti hasn't
     *      validated that the home-page address is in the restaurant's
     *      delivery zone, so it asks again. The overlay backdrop blocks
     *      ALL clicks underneath.
     *   2) When clicking '+' on a product whose restaurant's zone
     *      doesn't match the home address. (Already handled inside
     *      addProductToCart — this helper unifies the recovery.)
     */
    protected boolean handleAddressPromptIfPresent() {
        com.microsoft.playwright.Locator dialog = page.locator(
                "[role='dialog']:has-text('Adresiniz nedir')," +
                " .bds-c-modal__dialog:has-text('Adresiniz nedir')").first();
        try {
            if (!dialog.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(800))) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        System.out.println("    [addr-prompt] 'Adresiniz nedir?' modal visible — re-selecting address");
        try {
            // Click the "Adres Seç/Ekle" / "Adres Ekle" CTA inside the modal.
            com.microsoft.playwright.Locator cta = page.locator(
                    "[role='dialog'] button:has-text('Adres Seç')," +
                    " [role='dialog'] button:has-text('Adres Ekle')," +
                    " [role='dialog'] button:has-text('Ekle')," +
                    " [role='dialog'] button:has-text('Seç')").first();
            cta.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(3_000));
            page.waitForTimeout(1_800);

            // Now we're in the address-search modal. Re-type and confirm.
            String fallback = System.getProperty("address", "Üniversite 2");
            com.microsoft.playwright.Locator input = page.locator("#delivery-information-postal-index").first();
            try {
                input.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5_000));
            } catch (Exception e) {
                System.out.println("    [addr-prompt] expected address-search input but it didn't appear");
                return true;
            }
            input.click();
            input.fill("");
            input.pressSequentially(fallback,
                    new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(50));
            page.waitForTimeout(1_500);
            try {
                page.locator("[data-testid='address-suggestion-item']").first().click();
            } catch (Exception e) {
                System.out.println("    [addr-prompt] no suggestion to click for '" + fallback + "'");
                return true;
            }
            page.waitForTimeout(1_500);
            com.microsoft.playwright.Locator confirm = page.locator(
                    "[data-testid='location-search-go-icon']," +
                    " button[aria-label='Bu Adresi Kullan']").first();
            try {
                confirm.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5_000));
            } catch (Exception ignored) {
            }
            page.waitForTimeout(2_500);
            page.keyboard().press("Escape");
            page.waitForTimeout(800);
            System.out.println("    [addr-prompt] re-selected address: " + fallback);
            return true;
        } catch (Exception e) {
            System.out.println("    [addr-prompt] recovery failed: " + e.getMessage());
            return true;
        }
    }

    /**
     * Pick "Üniversite 2" via the address modal and COMMIT it. Selecting a
     * suggestion only fills the input — the modal stays open until you click
     * the "Bu Adresi Kullan" (Use This Address) confirm button. Without that
     * step, cart actions later fail with the "Adresiniz nedir?" prompt.
     */
    /**
     * Returns true when the home page already shows a confirmed delivery
     * address — i.e., the location-search button shows a real address
     * string (not the placeholder "Adresiniz nedir?" / "Adresini gir").
     * Used by the cart tests to skip selectAddress() when the
     * setup-cart-profile.sh helper already saved one.
     */
    protected boolean hasConfirmedAddress() {
        try {
            com.microsoft.playwright.Locator btn = page.locator("[data-testid='location-search-button']").first();
            if (!btn.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(2_000))) {
                return false;
            }
            String text = btn.innerText().trim();
            // "Adresiniz nedir?" or "Adresini gir" → no confirmed address.
            // Anything else (a real address string) → confirmed.
            if (text.isEmpty()) return false;
            String lc = text.toLowerCase(java.util.Locale.ROOT);
            if (lc.contains("adresiniz nedir") || lc.contains("adresini gir")
                    || lc.contains("teslimat adresini")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    protected void selectAddress(String query) {
        if (hasConfirmedAddress()) {
            System.out.println("    [addr] already have a confirmed address — skipping");
            return;
        }
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
        // The location-search modal sometimes leaves its backdrop
        // (`toolbox-search-overlay`) attached after navigation, intercepting
        // pointer events on the restaurant cards underneath. Wait for it to
        // detach; if it persists, click the backdrop to dismiss.
        com.microsoft.playwright.Locator overlay = page.locator(
                "[data-testid='toolbox-search-overlay']").first();
        try {
            overlay.waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(WaitForSelectorState.DETACHED)
                    .setTimeout(2_000));
        } catch (Exception e) {
            try {
                overlay.click(new com.microsoft.playwright.Locator.ClickOptions()
                        .setForce(true).setTimeout(1_000));
                page.waitForTimeout(500);
            } catch (Exception ignored) {
            }
        }
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

        // "Adresiniz nedir?" can pop up here too (restaurant wants address
        // re-verified for its delivery zone). Recover via the shared
        // helper, then retry the + click ONCE.
        if (handleAddressPromptIfPresent()) {
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

        // Resolve all restaurant URLs UP FRONT. The DOM Locator goes stale
        // every time we navigate to a restaurant and back, so we can't keep
        // a Locator across iterations — but we can keep the URL strings
        // (relative hrefs are absolutized below).
        com.microsoft.playwright.Locator expressLane = page.locator(
                "section:has(h2:has-text('Express teslimatlı'))," +
                " div:has(> h2:has-text('Express teslimatlı'))").first();
        com.microsoft.playwright.Locator cards = expressLane.locator("a[href*='/restaurant/']");
        int total = cards.count();
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        for (int i = 0; i < total && urls.size() < maxToTry; i++) {
            try {
                String href = cards.nth(i).getAttribute("href",
                        new com.microsoft.playwright.Locator.GetAttributeOptions().setTimeout(2_000));
                if (href == null || href.isEmpty()) continue;
                String absolute = href.startsWith("http") ? href : BASE_URL.replaceAll("/$", "") + href;
                urls.add(absolute);
            } catch (Exception ignored) {
            }
        }
        System.out.println("    Express lane restaurant count: " + total
                + " (resolved " + urls.size() + " urls, will try up to " + maxToTry + ")");

        int idx = 0;
        for (String url : urls) {
            idx++;
            try {
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(15_000));
            } catch (Exception e) {
                System.out.println("    [express #" + idx + "] " + url + " — navigate failed, next");
                continue;
            }
            page.waitForTimeout(800);
            waitOutCaptchaIfPresent();
            // The "Adresiniz nedir?" overlay can appear on page load,
            // not only after the + click — recover before we even look
            // for the menu, otherwise its backdrop intercepts clicks.
            handleAddressPromptIfPresent();
            dismissFloatingTooltips();

            com.microsoft.playwright.Locator firstProduct = page.locator(
                    "[data-testid='menu-product']").first();
            try {
                firstProduct.waitFor(
                        new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5_000));
            } catch (Exception e) {
                System.out.println("    [express #" + idx + "] " + url + " — no menu in 5s, skipping");
                continue;
            }
            try {
                firstProduct.scrollIntoViewIfNeeded();
            } catch (Exception ignored) {
            }
            dismissFloatingTooltips();
            if (addProductToCart(firstProduct)) {
                System.out.println("    [express #" + idx + "] " + url + " — accepted");
                return page.url();
            }
            System.out.println("    [express #" + idx + "] " + url + " — add failed, next");
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

        handleAddressPromptIfPresent();
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
