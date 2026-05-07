# Yemeksepeti Web Black-Box Tests (Playwright + Java)

Web ports of the three Maestro mobile YAMLs under [../mobile/](../mobile/),
targeting [https://www.yemeksepeti.com/](https://www.yemeksepeti.com/).
Covers W3 (search/browse), W4 (cart), W5 (checkout) from
[../../docs/blackbox_test_issues.md](../../docs/blackbox_test_issues.md).

> The test plan calls for `selenium-java:4.40.0` on web (test_plan.pdf §3.10).
> This module uses Playwright/Java instead by request.

## Layout

| Mobile YAML                                                             | Web Java test                                                                                                          | Confirmed pass |
| ----------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | -------------- |
| [../mobile/YemekSepetiSearch.yaml](../mobile/YemekSepetiSearch.yaml)     | [src/test/java/com/yemeksepeti/YemekSepetiSearchTest.java](src/test/java/com/yemeksepeti/YemekSepetiSearchTest.java)   | ✅ 2/2          |
| [../mobile/YemekSepetiPayTest.yaml](../mobile/YemekSepetiPayTest.yaml)   | [src/test/java/com/yemeksepeti/YemekSepetiPayTest.java](src/test/java/com/yemeksepeti/YemekSepetiPayTest.java)         | ✅ 2/2          |
| [../mobile/YemekSepetiCartTest.yaml](../mobile/YemekSepetiCartTest.yaml) | [src/test/java/com/yemeksepeti/YemekSepetiCartTest.java](src/test/java/com/yemeksepeti/YemekSepetiCartTest.java)       | ✅ 1/1          |

[BaseTest.java](src/test/java/com/yemeksepeti/BaseTest.java) handles a
**shared singleton** Playwright Chrome instance (one browser across all
test classes in the JVM run), with a persistent profile, real Chrome
channel, anti-bot stealth init scripts, and auto-loaded
captcha-solver extensions from your normal Chrome profile.

## Captcha workflow

`yemeksepeti.com` uses **PerimeterX + reCAPTCHA**. The setup mitigates this
five ways:

1. Real Google Chrome (`channel: "chrome"`) — drops most fingerprint flags.
2. Persistent profile under `target/chrome-profile/` — cookies survive runs.
3. Stealth init script (modeled on puppeteer-extra-plugin-stealth) — overrides
   `navigator.webdriver`, plugins, languages, WebGL vendor, permissions, etc.
4. Auto-loads any captcha-solver extension found in
   `~/.config/google-chrome/Default/Extensions/` (Buster, NopeCHA, etc.).
5. Singleton browser — only one captcha event per JVM run, not per test class.

If a wall still appears, the test prints `>>> Captcha detected. Solve it in
the open browser; the test will resume automatically (waiting up to 300s).`
Click through the "Press & Hold" or "Ben robot değilim" challenge in the
Chromium window and the test continues.

## Running

```bash
# Headed (default). Solve any captcha that appears in the Chrome window.
DISPLAY=:1 mvn -B test

# One test class
DISPLAY=:1 mvn -B -Dtest=YemekSepetiSearchTest test
DISPLAY=:1 mvn -B -Dtest=YemekSepetiPayTest test
DISPLAY=:1 mvn -B -Dtest=YemekSepetiCartTest test

# One method
DISPLAY=:1 mvn -B -Dtest=YemekSepetiSearchTest#scenario1_addressSuggestionAndRestaurantDetail test

# Bigger captcha-solve window (default 300s)
DISPLAY=:1 mvn -B -DcaptchaTimeoutMs=600000 test

# Use bundled Chromium instead of real Chrome (will hit captcha more often)
DISPLAY=:1 mvn -B -Dchannel=chromium test

# Use the system Chrome profile (your real cookies bypass PerimeterX outright;
# requires that you exit Chrome before running — the profile is locked while
# Chrome is open).
DISPLAY=:1 mvn -B -DuseRealProfile=true test
```

### Pre-warming the profile (recommended)

If captcha keeps appearing on test runs, manually open the test profile in
Chrome once and clear the challenge yourself:

```bash
google-chrome --user-data-dir="$(pwd)/target/chrome-profile" https://www.yemeksepeti.com/
# Solve any captcha. Browse to a restaurant page. Close Chrome.
```

The cookies are now seeded; subsequent test runs skip the challenge for
~24 hours.

## Test deviations from the mobile YAML

The web tests preserve the **flow shape** of each mobile YAML but adapt to
yemeksepeti.com's actual UI:

- **Search / Senaryo 1**: mobile typed an address into the home search bar.
  Web equivalent opens `[data-testid='location-search-button']` (the address
  modal) and types into `#delivery-information-postal-index`, then clicks
  `[data-testid='address-suggestion-item']`.
- **Search / Senaryo 2**: top-bar search via `[data-testid='new-search-input']`,
  then click an autocomplete suggestion.
- **Cart**: mobile asserts hard-coded prices ("210,00 TL" → "420,00 TL" → ...)
  because it ran against one specific restaurant. The web test hits a
  different restaurant per run, so it asserts the **math** instead — doubling
  quantity doubles price; decrement restores it; remove empties cart.
  Quantity stepper selectors:
  `[data-testid='quantity-stepper-collapsed-button']` (+),
  `[data-testid='quantity-stepper-remove-button']` (−),
  `[data-testid='quantity-stepper-quantity']` (current count).
- **Pay / Senaryo 1**: mobile edits saved addresses (requires login on web).
  Web port exercises the un-authed location modal — persistence half is a
  known gap (see `docs/blackbox_test_issues.md`).
- **Pay / Senaryo 2**: mobile picks payment methods inside checkout (also
  login-gated on web). Web port verifies the same payment-type values
  (`cash`, `yemekpay_creditcard`, `yemekpay_cardondelivery`) via the
  home-page filter panel — same backend identifiers, no order placed.
- **No real payment** is ever submitted (test_plan.pdf §1.3).

## Test data

All synthetic, per test_plan.pdf §3.9: `"Üniversite 2"`, `"Hatay"`,
`"lahmacun"`. No real PII.
