# Yemeksepeti Web Black-Box Test Report

- **Module:** [blackbox/web/](.) — Java + Playwright port of the three Maestro mobile YAMLs under [../mobile/](../mobile/)
- **Target:** `https://www.yemeksepeti.com/`
- **Issues covered:** W3 (search/browse), W4 (cart), W5 (checkout) from [../../docs/blackbox_test_issues.md](../../docs/blackbox_test_issues.md)
- **Run date:** 2026-05-07
- **Tester:** Osman Şahin Güler

## Environment

| Component | Value |
| --- | --- |
| OS | Linux 6.17.0-23-generic (Ubuntu) |
| Display | X11 (`DISPLAY=:1`) |
| Java | 17 |
| Build | Maven 3 |
| Test framework | JUnit Jupiter 5.11.3 |
| Browser driver | Playwright Java 1.49.0 |
| Browser channel | Google Chrome 148.0.7778.96 (system-installed, `channel: "chrome"`) |
| Profile mode | Persistent under `target/chrome-profile/` |
| Locale | `tr-TR`, timezone `Europe/Istanbul` |
| Viewport | 1366×900 |

## Test plan compliance

- **Out of scope:** real payment transactions, performance/load/security testing — honored (test_plan.pdf §1.3). The pay test asserts no order-confirmation text appears.
- **Synthetic test data only:** `"Üniversite 2"`, `"Hatay"`, `"lahmacun"`, `"RandomText"` — no real PII (test_plan.pdf §3.9).
- **Tooling deviation:** the test plan specifies `selenium-java:4.40.0` (test_plan.pdf §3.10); this module uses Playwright/Java instead by user direction. Documented in [README.md](README.md).

## Results

| # | Test class                  | Method                                              | Result |  Time | Notes |
| - | --------------------------- | --------------------------------------------------- | :----: | ----: | ----- |
| 1 | `YemekSepetiSearchTest`     | `scenario1_addressSuggestionAndRestaurantDetail`    | ✅ PASS | ~26 s | Mirrors mobile Senaryo 1 |
| 2 | `YemekSepetiSearchTest`     | `scenario2_topbarSearchAndDetail`                   | ✅ PASS | ~28 s | Mirrors mobile Senaryo 2 |
| 3 | `YemekSepetiPayTest`        | `scenario1_addressModalNewAddress`                  | ✅ PASS | ~22 s | Web port — un-authed location modal (persistence half login-gated, see Gaps) |
| 4 | `YemekSepetiPayTest`        | `scenario2_paymentMethodFiltersAreSelectable`       | ✅ PASS | ~23 s | Asserts the same payment-type backend ids as the mobile checkout |
| 5 | `YemekSepetiCartTest`       | `cartAddIncrementDecrementRemove`                   | ✅ PASS | ~59 s | Mirrors mobile cart flow (math-based assertions in lieu of literal TL prices) |

**Aggregate:** 5 / 5 passing · 0 / 5 logic failures.

`YemekSepetiSearchTest`: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` (53.69 s class total)
`YemekSepetiPayTest`:    `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` (45.34 s class total)
`YemekSepetiCartTest`:   `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` (59.19 s class total)

## Test approach

- **Black-box techniques applied** (test_plan.pdf §3.5):
  - **Equivalence partitioning** on the address query (`"Üniversite 2"`, `"Hatay"`).
  - **Use case testing** — full flow: address pick → restaurant card → menu → cart actions.
  - **Decision-table-style** verification of payment-method radio combinations.
  - **Boundary value analysis** on cart quantity transitions: 0 → 1 → 2 → 1 → 0.
- **Selectors** are anchored on `data-testid` attributes (most stable in this codebase) rather than CSS class names.
- **Cart assertions are price-agnostic** — the live restaurant differs per run, so the test verifies the *math* the mobile YAML implicitly checked (doubling quantity doubles total; decrement restores it; remove empties).
- **No real payment** is submitted; the pay test stops at the home-page filter panel and asserts the order-confirmation text never appears.

## Mapping: mobile YAML → web equivalent

| Mobile element                  | Web equivalent (verified)                                      |
| ------------------------------- | -------------------------------------------------------------- |
| `id: HomeSearchBar` (address)   | `[data-testid='location-search-button']`                       |
| `id: AUTOCOMPLETE_SUGGESTION_ENTRY` | `[data-testid='address-suggestion-item']`                  |
| Tap restaurant `IMAGE`          | `a[href*='/restaurant/']`                                      |
| `assertVisible: Menüde ara`     | `[data-testid='search-input']` (placeholder "Menüde Ara")      |
| `id: SEARCH_BAR_CLEAR_BUTTON` / top search | `[data-testid='new-search-input']`                  |
| Mobile cart `+` / `−` controls  | `[data-testid='quantity-stepper-collapsed-button']` / `[data-testid='quantity-stepper-remove-button']` |
| Mobile cart quantity readout    | `[data-testid='quantity-stepper-quantity']`                    |
| Mobile cart line total          | `[data-testid='menu-product-price']` × current quantity (parsed) |
| Mobile payment radios (Nakit, Online Kredi Kartı, Kapıda Temassız) | `input#cash`, `input#yemekpay_creditcard`, `input#yemekpay_cardondelivery` |

## Gaps and known issues

1. **Pay Senaryo 1 (mobile address-book editing) is partially out of reach on web.** On mobile, this scenario exercises an address-book that persists per-user. On the web that page is behind login. The web port exercises the un-authed location modal instead — same DOM, same backend, but it can't verify the "Kaydet → see saved address row" half of the mobile flow. This was already flagged in [../../docs/blackbox_test_issues.md](../../docs/blackbox_test_issues.md) under the W5/M5 ownership notes.

2. **Pay Senaryo 2 (checkout payment selector) is similarly login-gated.** The web port verifies the same `payment_type` backend identifiers via the home-page filter panel — same effect as far as the API contract is concerned, but it doesn't traverse the actual checkout sheet UI. Documented in the test class comment.

3. **Anti-bot interference is the dominant runtime risk**, not test bugs. PerimeterX challenges intermittently on yemeksepeti.com and may break unattended runs. See [HOW_TO_RUN.md](HOW_TO_RUN.md) for the pre-warm workaround that eliminates it for ~24 h.

## Defects observed

None. No production defects were uncovered by these tests during this run. (Each scenario is a happy-path use-case + boundary verification — incident reports per test_plan.pdf §3.4 would be filed separately if defects appeared.)

## Recommendations

- **Pre-warm the persistent profile** before any unattended run (see [HOW_TO_RUN.md](HOW_TO_RUN.md) §"First-time setup"). Eliminates PerimeterX for the next ~24 h.
- For long-term CI: investigate logging in once with a synthetic test account so checkout-side assertions in `YemekSepetiPayTest` can replace the home-filter-panel proxies.
- For Cart: keep the math-based assertions even after a real prod run — they're more durable than the literal `210,00 TL` strings the mobile script used.

## Artifacts

- Source: [src/test/java/com/yemeksepeti/](src/test/java/com/yemeksepeti/)
- Surefire reports (per run): `target/surefire-reports/`
- Persistent browser profile: `target/chrome-profile/` (gitignored)
- DOM probe screenshots used to derive the selector table: `target/probe-*.png` (transient)
