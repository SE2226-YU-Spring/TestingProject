#!/usr/bin/env bash
# One-time interactive setup for the cart tests.
#
# Launches Google Chrome against the SAME --user-data-dir that the cart
# tests use, so whatever you do here (sign in via Google, pick a delivery
# address, accept cookie banners) is persisted in cookies + Local Storage
# and the automated test can skip those steps next time.
#
# Workflow:
#   1. Run this script.
#   2. In the Chrome window that opens:
#        a. If yemeksepeti.com isn't open, navigate to https://www.yemeksepeti.com/
#        b. Solve any "Press & Hold" / captcha challenge if shown.
#        c. Click "Giriş Yap" → "Google ile devam et" → pick your account.
#           Complete any phone-verification step Yemeksepeti asks for. (Test
#           code will NEVER complete checkout — only this manual setup
#           ever sees those screens.)
#        d. Click the address bar at the top, type "Üniversite 2"
#           (or your preferred area), pick the suggestion,
#           click "Bu Adresi Kullan".
#        e. Open one restaurant and confirm you can add an item to the cart
#           without an "Adresiniz nedir?" prompt — that proves the session
#           is wired correctly.
#        f. Close Chrome (don't just close the tab — File → Quit, or ⌘Q,
#           or close the window).
#   3. From now on, run:
#        DISPLAY=:1 mvn -B -DconnectCDP=true -Dtest=YemekSepetiCartTest test
#      and the test will reuse the session you just set up.
#
# Re-run this script if the session expires (the script auto-detects an
# absent profile by re-cloning), or to re-pick an address.

set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROFILE_DIR="${PROFILE_DIR:-$HERE/target/chrome-profile-cdp}"

# Resolve a Chrome binary
for c in /usr/bin/google-chrome-stable /usr/bin/google-chrome /opt/google/chrome/chrome /snap/bin/chromium; do
    if [ -x "$c" ]; then CHROME="$c"; break; fi
done
if [ -z "${CHROME:-}" ]; then
    echo "ERROR: google-chrome binary not found. Install with: sudo apt install google-chrome-stable" >&2
    exit 1
fi

# Clone real profile on first run so installed extensions (Buster, etc.) come along
if [ ! -d "$PROFILE_DIR/Default" ]; then
    REAL="$HOME/.config/google-chrome"
    if [ -d "$REAL/Default" ]; then
        echo "[setup] one-time clone of $REAL/Default → $PROFILE_DIR/Default (~30 s)..."
        mkdir -p "$PROFILE_DIR"
        cp -r "$REAL/Default" "$PROFILE_DIR/Default"
        [ -f "$REAL/Local State" ] && cp "$REAL/Local State" "$PROFILE_DIR/Local State"
        # Wipe session-restore so Chrome doesn't reopen all your other tabs
        rm -rf "$PROFILE_DIR/Default/"{"Current Session","Current Tabs","Last Session","Last Tabs",Sessions} 2>/dev/null || true
        rm -f  "$PROFILE_DIR/"{SingletonLock,SingletonCookie,SingletonSocket} 2>/dev/null || true
    else
        echo "[setup] no real Chrome profile at $REAL — starting fresh"
        mkdir -p "$PROFILE_DIR"
    fi
fi

# Make sure no automated run is holding the profile
rm -f "$PROFILE_DIR/SingletonLock" "$PROFILE_DIR/SingletonCookie" "$PROFILE_DIR/SingletonSocket" 2>/dev/null || true

cat <<'EOF'
========================================================================
 Cart-test profile setup
========================================================================
A Chrome window is opening. Do the following ONCE:

  1. (If captcha) solve "Press & Hold" / Yemeksepeti's PerimeterX wall.
  2. Click "Giriş Yap" → "Google ile devam et" → pick your account.
  3. Click the address bar, type "Üniversite 2", pick first suggestion,
     click "Bu Adresi Kullan".
  4. Open one restaurant and check that you can add an item to the cart
     (don't actually order — just verify the + button works).
  5. Close Chrome completely (File → Quit, NOT just the tab).

After Chrome exits, the test will reuse this session.

DO NOT proceed to checkout / payment. The cart tests stop at add /
increment / remove only — never at order placement.
========================================================================
EOF

exec "$CHROME" \
    --user-data-dir="$PROFILE_DIR" \
    --profile-directory=Default \
    --no-first-run \
    --no-default-browser-check \
    --lang=tr-TR \
    --window-size=1366,900 \
    https://www.yemeksepeti.com/
