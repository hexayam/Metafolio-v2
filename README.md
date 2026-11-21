# Metalfolio — Android market watchlist

A minimal, ad-free, local-first Android watchlist for gold, silver, ETFs, and international stocks.

## Setup
1. Install a recent **Android Studio** with **Android SDK 37** and **JDK 17**.
2. Open the `Metalfolio` folder as a Gradle project. Let Android Studio download the required Gradle distribution and libraries.
3. Build > Build APK(s), or click Run to install on your Android phone via USB debugging.
4. Create your own API key at https://twelvedata.com/ and enter it under Preferences.
5. Refresh, then customize the watchlist from the main screen.

**No APK is bundled**: this environment does not have the Android SDK, so the project has not been compiled or device-tested. Versions are pinned but may require compatible upgrades in Android Studio.

## Design choices
- One compact batch HTTPS request for up to 8 symbols (more than 8 handled as additional batches; see below).
- Android WorkManager sync approximately once per hour, network required; no service kept running.
- Cached quotes persist in Android SharedPreferences; offline watchlist works.
- No ad SDK, tracking SDK, account, notification, background location, WebView or WebSocket.
- User-controlled watchlist, category filters, USD / approximate AED display, gold and silver per troy ounce or gram.
- Quote comparisons are **since last different fetched price**, not exchange-reported daily returns.
- Quote currency for custom symbols is user supplied; provider symbol/currency must be confirmed by the user.
- Free Twelve Data access is subject to rate limits, instrument access, venue licensing and market delays. Individual assets can remain unavailable.

## Limitations / production hardening
- This is a source project, not a verified production release. Before publishing, test device configurations, TLS/network failure handling and accessibility.
- Free tier typically allows 8 credits per minute. To respect this quota, additional batches beyond eight symbols are delayed by 61 seconds; keeping the watchlist at eight or fewer is more battery-efficient.
- No price history graph or push alerts; avoiding these keeps the CPU/memory/network workload low.
- PeriodicWorkRequest is inexact; Android's Doze/App Standby may defer work.
- USD/AED conversion uses a fixed approximate peg, not live FX. International securities are not converted automatically.
- API credentials are saved in app-private SharedPreferences; for a stronger production threat model, use Android Keystore encryption.

## Licence
Personal use and modification permitted. API data is governed by the provider's licence and terms.

## Build the APK from a phone (GitHub Actions)

The repository includes `.github/workflows/build-apk.yml`. Upload the **contents of this Metalfolio directory** to the root of a new GitHub repository (not the enclosing directory). Open **Actions > Build Metalfolio APK > Run workflow**. When the workflow completes successfully, download `Metalfolio-debug-APK` under **Artifacts**, extract the ZIP, and install `app-debug.apk` on your own Android device.

This workflow uses the Android SDK preinstalled on the GitHub-hosted Linux runner to avoid the earlier `android-actions/setup-android@v3` failure. It still requires that Gradle dependencies and Android SDK 37 can be downloaded at build time. The source has not been compiled in this environment. If a job fails, inspect the **first error in the failed step**; the last Java/Node stack-trace line often hides the actual cause.
