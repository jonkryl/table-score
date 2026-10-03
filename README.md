# Счёт на стол / Table Score

An Android scorekeeper for 2–8 players or teams. Positive and negative points, precise score entry, separate rounds, totals, winners and ties, game history, text sharing and one persistent undo. Russian and English. No account or game-data backend.

Package: `com.jonkryl.tablescore`. Android 7.0+ (`minSdk 24`), `compileSdk/targetSdk 36`. Kotlin with native Android Views.

- [Support](https://jonkryl.github.io/table-score/)
- [Privacy policy, RU/EN](https://jonkryl.github.io/table-score/privacy/)
- [CI checks](https://github.com/jonkryl/table-score/actions/workflows/android-ci.yml)
- [Signed release workflow](https://github.com/jonkryl/table-score/actions/workflows/release.yml)
- [Downloadable releases](https://github.com/jonkryl/table-score/releases)
- Contact: [jonkryl@gmail.com](mailto:jonkryl@gmail.com)

## Storage and privacy

Every meaningful mutation saves the complete state with `AtomicFile` before exposing it to the UI. A previous complete state is saved for undo, including after process restart. Games and player names remain in app-private storage; export uses Android’s share sheet. Clearing app data or uninstalling removes local records. The app has no cloud sync and disables Android app backup.

The app contains a Yandex Advertising Network banner through Yandex Mobile Ads SDK 8.5.0. Ad personalization is off by default and can be allowed or withdrawn in Privacy; the banner remains in either mode. Location tracking and SDK analytics reporting are disabled; `AD_ID` permission is removed from the merged manifest. Advertising requests may still transmit IP addresses, technical information and identifiers to Yandex. See the [privacy policy](https://jonkryl.github.io/table-score/privacy/) and [Yandex’s SDK disclosure](https://ads.yandex.com/helpcenter/en/dev/android/app-privacy-android).

## Verification and releases

All Android builds, tests and signing run in GitHub Actions. Do not run local Gradle builds, local Android tests or emulators for this project.

`Android CI` runs meaningful JVM tests for signed scores, rounds, ties, durable undo, malformed saves, score overflow and failed writes, plus Android lint. Device jobs on API 24 and API 36 create a game, add and subtract points, undo, restart and export text. Device proofs and screenshots are workflow artifacts.

`Signed Android release` first requires those checks, then builds a signed APK and AAB in the `production` environment. The workflow verifies signatures and manifests and publishes a downloadable GitHub prerelease. A GitHub prerelease is a test distribution; Play review and Yandex activation are separate publication steps.

Release configuration comes from protected GitHub secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and the real `YANDEX_BANNER_ID` variable. Demo ad units are debug-only. Release builds reject missing signing material or a demo/invalid banner ID. Never commit a signing key or publish secret values.

## Pages

The `docs/` directory contains the static RU/EN support and privacy pages. GitHub Pages serves it from the `main` branch with no external scripts, fonts or tracking integrations added by this site. Hosting requests are handled by GitHub under its own privacy practices.
