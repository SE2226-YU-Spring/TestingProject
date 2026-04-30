# Yemeksepeti Black-Box Issue List

This document turns the Yemeksepeti black-box test scope from [test_plan.pdf](test_plan.pdf) into concrete issues, one per feature × platform.

- Scope of testing: System Testing level, Functional + UI + Regression types (test_plan.pdf §1.1, §3.2, §3.3).
- Out of scope: real payment transactions, performance/load/security testing (test_plan.pdf §1.3).
- Web tooling: Google Chrome + Selenium IDE, with `org.seleniumhq.selenium:selenium-java:4.40.0` for WebDriver scripts (test_plan.pdf §3.10).
- Mobile tooling: Maestro Studio on a physical Android or iOS device, Android Platform Tools for USB debugging (test_plan.pdf §3.10).
- Black-box techniques to apply: Equivalence Partitioning, Boundary Value Analysis, Use Case Testing, Decision Table Testing (test_plan.pdf §3.5).
- Deadline: all activities and documentation completed before the project presentation on 2026-05-04 (test_plan.pdf §3.7).

Library-DB-Express white-box issues are tracked in [whitebox_test_issues.md](whitebox_test_issues.md).

## Team

All five members share the work equally. There is no separate reviewer role; whoever is not on an issue reviews it. Each member owns two web (Selenium) issues and two mobile (Maestro) issues.

- Atakan Sezginer
- Batuhan Yerebasmaz
- Ege Çınar
- İsmet Sayğın Koç
- Osman Şahin Güler

## Web Issues (Chrome + Selenium IDE / selenium-java 4.40.0)

### W1. User registration — web

- Feature: account creation flow on the Yemeksepeti web app
- Goal: verify that valid registrations succeed and invalid inputs are rejected with appropriate UI feedback
- Test focus:
  - Equivalence partitioning on email (valid format, missing `@`, missing domain, empty)
  - Boundary value analysis on password length (min length − 1, min length, min length + 1, very long)
  - Decision table on required-field combinations (name / email / phone / password missing or present)
  - Use case: register → verify confirmation message / email step
- Tooling: Selenium IDE recording exported to selenium-java
- Test data: synthetic emails and phone numbers only (no real personal info, test_plan.pdf §3.9)
- Owners: Atakan Sezginer, Batuhan Yerebasmaz

### W2. User login — web

- Feature: sign-in flow on the Yemeksepeti web app
- Goal: verify successful login with valid credentials and proper error handling for invalid ones
- Test focus:
  - Decision table on (email valid / invalid) × (password valid / invalid) × (account exists / not)
  - Equivalence partitioning on email format
  - Lockout / rate-limit behavior after repeated failures (observable only)
  - Use case: login → land on home / restaurant list
- Tooling: Selenium IDE
- Owners: İsmet Sayğın Koç, Ege Çınar

### W3. Restaurant search and browsing — web

- Feature: search restaurants by name and browse by category
- Goal: verify search returns relevant results and category filters narrow the list correctly
- Test focus:
  - Equivalence partitioning on search query (exact name, partial name, non-existent, empty, special characters)
  - Boundary value analysis on result-list pagination (first page, last page, single result, no results)
  - Use case: search → open restaurant card; browse category → open restaurant card
  - UI testing: filter chips, sort order, "no results" empty state
- Tooling: Selenium IDE
- Owners: Osman Şahin Güler, Atakan Sezginer

### W4. Restaurant menu and cart operations — web

- Feature: viewing a restaurant menu and cart add / remove / quantity change
- Goal: verify menu rendering and that cart reflects user actions and price math
- Test focus:
  - Boundary value analysis on item quantity (0, 1, max allowed, max + 1)
  - Equivalence partitioning on item options / extras (none, one, multiple)
  - Decision table on cart state transitions (empty → 1 item → multiple items → all removed)
  - Cross-restaurant cart behavior (adding from a second restaurant — replace vs warn)
  - UI testing: subtotal updates, remove-item button, increment / decrement controls
- Tooling: Selenium IDE
- Owners: İsmet Sayğın Koç, Batuhan Yerebasmaz

### W5. Checkout and order confirmation — web

- Feature: delivery address entry, checkout steps, and order confirmation screen
- Goal: verify the checkout flow up to the order-confirmation step **without performing real payment** (test_plan.pdf §1.3)
- Test focus:
  - Equivalence partitioning on address fields (valid, missing, invalid postcode, special characters)
  - Decision table on payment-method selection × delivery-time selection
  - Use case: full checkout → confirmation page shows order summary
  - Negative path: empty cart → checkout button disabled / blocked
- Tooling: Selenium IDE
- Owners: Ege Çınar, Osman Şahin Güler

## Mobile Issues (Maestro Studio)

### M1. User registration — mobile

- Feature: account creation flow on the Yemeksepeti mobile app
- Goal: verify mobile-specific registration flow (SMS / OTP, permissions prompts) succeeds with valid data and rejects invalid inputs
- Test focus:
  - Equivalence partitioning on phone number format
  - Boundary value analysis on OTP code length and resend cooldown
  - Decision table on required-field combinations
  - UI testing: keyboard handling, field focus, error toasts
- Tooling: Maestro Studio script; Android Platform Tools for ADB if needed
- Owners: Batuhan Yerebasmaz, Ege Çınar

### M2. User login — mobile

- Feature: sign-in flow on the Yemeksepeti mobile app
- Goal: verify login works on mobile and that session persists across app restarts
- Test focus:
  - Decision table on credential validity
  - Persistence: kill app → reopen → still logged in
  - "Forgot password" entry path (observable only)
  - UI testing: biometric prompt if offered, soft keyboard behavior
- Tooling: Maestro Studio
- Owners: Atakan Sezginer, Osman Şahin Güler

### M3. Restaurant search and browsing — mobile

- Feature: search by name and browse by category on mobile
- Goal: verify mobile search and category filters return correct results and gestures behave as expected
- Test focus:
  - Equivalence partitioning on search query
  - UI testing: pull-to-refresh, infinite scroll boundary, "no results" empty state
  - Use case: search → tap card → restaurant detail
  - Network-quality variation note (test_plan.pdf §2: results may vary by connection speed)
- Tooling: Maestro Studio
- Owners: İsmet Sayğın Koç, Ege Çınar

### M4. Restaurant menu and cart operations — mobile

- Feature: menu view and cart add / remove / quantity change on mobile
- Goal: verify cart actions and price math on mobile, including gesture-driven controls
- Test focus:
  - Boundary value analysis on item quantity (0, 1, max, max + 1)
  - Decision table on cart transitions
  - UI testing: swipe-to-remove if present, sticky cart bar, quantity stepper
  - Cross-restaurant cart behavior
- Tooling: Maestro Studio
- Owners: Atakan Sezginer, Batuhan Yerebasmaz

### M5. Checkout and order confirmation — mobile

- Feature: delivery address selection, checkout, and order confirmation on mobile
- Goal: verify the mobile checkout flow up to the order-confirmation screen **without performing real payment**
- Test focus:
  - Equivalence partitioning on address selection (saved address, new address, missing fields)
  - Decision table on payment-method × delivery-time
  - Use case: full mobile checkout → confirmation screen
  - UI testing: address-picker modal, map interaction if shown, back-button behavior mid-flow
- Tooling: Maestro Studio
- Owners: İsmet Sayğın Koç, Osman Şahin Güler

## Workload Check

Each member is on exactly four issues, with two web and two mobile each.

| Member | Web | Mobile | Total |
| --- | --- | --- | --- |
| Atakan Sezginer | W1, W3 | M2, M4 | 4 |
| Batuhan Yerebasmaz | W1, W4 | M1, M4 | 4 |
| Ege Çınar | W2, W5 | M1, M3 | 4 |
| İsmet Sayğın Koç | W2, W4 | M3, M5 | 4 |
| Osman Şahin Güler | W3, W5 | M2, M5 | 4 |

## Notes

- Test data must be synthetic; no real personal information (test_plan.pdf §3.9).
- Real payment must not be executed; checkout coverage stops at the confirmation step (test_plan.pdf §1.3).
- Results may vary by device, browser version, mobile OS, and network speed (test_plan.pdf §2). Record the environment for every run.
- Defects found while executing these issues should be filed as separate Incident Reports (test_plan.pdf §3.4).
