# How to Run the Yemeksepeti Web Black-Box Tests

This document is the step-by-step recipe to run the tests on a clean machine.
For test results and what each scenario covers, see [TEST_REPORT.md](TEST_REPORT.md).
For the high-level overview, see [README.md](README.md).

## Prerequisites (one-time)

| Requirement | Check command | If missing |
| --- | --- | --- |
| Java 17+ | `java -version` | `sudo apt install openjdk-17-jdk` |
| Maven 3 | `mvn -v` | `sudo apt install maven` |
| Real Google Chrome (the test uses `channel: "chrome"`) | `google-chrome --version` | `sudo apt install -y google-chrome-stable` — or download `.deb` from `https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb` and `sudo dpkg -i ...` |
| X11 display | `echo $DISPLAY` should print something like `:0` or `:1` | Run on a desktop session (the tests run **headed**; a virtual `xvfb-run` works too) |

> **Note:** the bundled Chromium that Playwright downloads on first run is *not* enough — yemeksepeti.com's anti-bot fingerprinting flags it. The `channel: "chrome"` setting in `BaseTest.java` requires real Google Chrome.

## First-time setup (≈ 5 minutes)

### 1. Make sure you can reach the project

```bash
cd /home/osm/Documents/School/SE2226/TestingProject/blackbox/web
ls pom.xml src/test/java/com/yemeksepeti/   # should list the 3 test classes + BaseTest
```

### 2. Compile (downloads Playwright + JUnit on first run)

```bash
mvn -B clean test-compile
```

Expected: `BUILD SUCCESS`. First run takes ~30–60 s while Maven fetches dependencies.

### 3. Pre-warm the persistent browser profile (RECOMMENDED — eliminates captcha for ~24 h)

The very first time the tests open Chrome with a fresh profile, yemeksepeti.com's PerimeterX firewall *may* serve a "Press & Hold to confirm you are a human" wall. Solve it manually **once** and the cookies stick:

```bash
google-chrome --user-data-dir="$(pwd)/target/chrome-profile" https://www.yemeksepeti.com/
```

In the Chrome window that opens:
1. If you see "Press & Hold to confirm you are a human" — press and hold the button until it clears.
2. If you see "Ben robot değilim" / reCAPTCHA — tick the checkbox.
3. Click into one restaurant card so the deeper-page cookie also gets cleared.
4. **Close Chrome completely** (the profile is locked while Chrome is running, and the test won't be able to open it).

You're now warmed up. Re-do this step only if a test run starts complaining about captchas again (typically once per day).

## Running the tests

All commands below are run from `blackbox/web/` (`cd /home/osm/Documents/School/SE2226/TestingProject/blackbox/web`).

### Run everything

```bash
DISPLAY=:1 mvn -B test
```

Adjust `:1` to match your `echo $DISPLAY`. Tests open in a Chrome window — don't close it manually until they finish.

### Run one test class

```bash
DISPLAY=:1 mvn -B -Dtest=YemekSepetiSearchTest test
DISPLAY=:1 mvn -B -Dtest=YemekSepetiPayTest test
DISPLAY=:1 mvn -B -Dtest=YemekSepetiCartTest test
```

### Run one test method

```bash
DISPLAY=:1 mvn -B -Dtest=YemekSepetiSearchTest#scenario1_addressSuggestionAndRestaurantDetail test
DISPLAY=:1 mvn -B -Dtest=YemekSepetiSearchTest#scenario2_topbarSearchAndDetail test
DISPLAY=:1 mvn -B -Dtest=YemekSepetiPayTest#scenario1_addressModalNewAddress test
DISPLAY=:1 mvn -B -Dtest=YemekSepetiPayTest#scenario2_paymentMethodFiltersAreSelectable test
DISPLAY=:1 mvn -B -Dtest=YemekSepetiCartTest#cartAddIncrementDecrementRemove test
```

### Useful flags

| Flag | What it does | Default |
| --- | --- | --- |
| `-Dheadless=true` | Run with no visible browser window. **Only works after pre-warming** — captcha can't be solved without a window. | `false` |
| `-DcaptchaTimeoutMs=600000` | How long to wait for you to solve a captcha that pops up mid-test. Set higher (10 min) when you're not sure. | `300000` (5 min) |
| `-Dchannel=chromium` | Fall back to Playwright's bundled Chromium instead of system Chrome. **Will hit captchas more often** — use only if `google-chrome` is missing. | `chrome` |
| `-DuseRealProfile=true` | Use your actual `~/.config/google-chrome/Default` profile instead of `target/chrome-profile/`. Bypasses PerimeterX outright because your real cookies are already cleared. **Requires that you fully close your normal Chrome before running.** | `false` |
| `-DextensionPaths=<dir1>,<dir2>` | Override which Chrome extensions to load. Default auto-discovers Buster, NopeCHA, etc. from your normal profile. | (auto) |

Examples:

```bash
# Use your real Chrome profile (skip pre-warm step entirely; close normal Chrome first)
DISPLAY=:1 mvn -B -DuseRealProfile=true test

# Give yourself 10 minutes to solve a captcha if one pops up
DISPLAY=:1 mvn -B -DcaptchaTimeoutMs=600000 test
```

## What you should see

A successful run prints something like:

```
[INFO] Running com.yemeksepeti.YemekSepetiSearchTest
[ext] loading extensions: /home/osm/.config/google-chrome/Default/Extensions/...
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 53.69 s -- in com.yemeksepeti.YemekSepetiSearchTest
[INFO] Running com.yemeksepeti.YemekSepetiPayTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 45.34 s -- in com.yemeksepeti.YemekSepetiPayTest
[INFO] Running com.yemeksepeti.YemekSepetiCartTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: ~30 s -- in com.yemeksepeti.YemekSepetiCartTest
[INFO] Results:
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

If a captcha appears mid-run you'll see in the console:

```
>>> Captcha detected. Solve it in the open browser; the test will resume automatically (waiting up to 300s).
```

Bring the Chrome window to focus, solve the challenge, and the test continues automatically.

## Troubleshooting

### `Captcha not solved within N seconds`
A captcha appeared but you weren't at the keyboard. Re-run with `-DcaptchaTimeoutMs=600000` and stay at the keyboard, **or** do the pre-warm step above.

### `[ext] loading extensions: ` (empty)
The auto-discovery didn't find anything in `~/.config/google-chrome/Default/Extensions/`. That's OK — Buster only solves Google reCAPTCHA, not PerimeterX press-and-hold, so its absence is mostly cosmetic. Use the pre-warm or `-DuseRealProfile=true` instead.

### Browser closes immediately / `Target page, context or browser has been closed`
You either closed the Chrome window manually, or another instance of Chrome with the same `--user-data-dir` is already running. Close all Chrome windows (`pkill chrome`) and re-run.

### `BUILD FAILURE` with `403 Forbidden` / `px-captcha`
PerimeterX is in an aggressive mood. Either:
- Wait an hour and try again, or
- Run `-DuseRealProfile=true` (close your normal Chrome first), or
- Re-do the pre-warm step.

### `Selectors timed out` after the page loaded
Yemeksepeti A/B-tests its DOM. If a `data-testid` we depend on disappeared, run any one test method with `--debug` (set `PWDEBUG=1` env var) to inspect the live DOM and update the selector.

### `google-chrome: command not found`
Either install Chrome (`sudo apt install -y google-chrome-stable`) or pass `-Dchannel=chromium` to fall back to the bundled browser (you'll hit captchas more often).

## Quick reference

```bash
# 1. Pre-warm once (skip if using -DuseRealProfile=true)
google-chrome --user-data-dir="$(pwd)/target/chrome-profile" https://www.yemeksepeti.com/
# Solve any captcha. Open one restaurant. Close Chrome.

# 2. Run all 5 tests
DISPLAY=:1 mvn -B test

# 3. Or run a single class while iterating
DISPLAY=:1 mvn -B -Dtest=YemekSepetiCartTest test
```
