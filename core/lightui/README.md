# `:core:lightui` — vendored Light Phone SDK UI

This module contains a **verbatim-as-possible copy** of the Compose UI layer of the
[Light Phone SDK](https://github.com/lightphone/light-sdk). It is not Signal/Molly code and it is
**not** AGPL — see [LICENSE](LICENSE).

| | |
|---|---|
| Upstream project | `light-sdk` (The Light Phone) |
| Upstream path | `sdk/ui/src/main/kotlin/com/thelightphone/sdk/ui/` and `sdk/ui/src/main/res/drawable/` |
| Upstream remote | `https://github.com/lightphone/light-sdk.git` |
| Upstream commit | `fb68d753229282b94c822aa0b709b91c1949fa6d` (2026-09-09, "Merge pull request #175 from lightphone/feat/filemanager") |
| Upstream version | `sdkVersion=0.1.1` (`com.thelightphone:sdk-ui`) |
| Licence | MIT, `Copyright (c) 2026 The Light Phone` |

Every vendored file carries an MIT/SPDX header. Do **not** replace those headers with Signal's
AGPL-3.0 header. MIT is compatible with AGPL-3.0, so redistributing these files inside Molly is
permitted as long as the copyright notice and licence text are preserved.

## Why vendored instead of a Gradle dependency?

Two hard blockers, both verified:

1. **Kotlin version conflict.** light-sdk is built with Kotlin **2.3.20**; Molly is on Kotlin
   **2.2.20**. Kotlin metadata is not forward-compatible, so a 2.3.20-compiled library cannot be
   consumed by the 2.2.20 compiler. Bumping Molly's Kotlin version is far out of scope.
2. **SDK Gradle plugin allowlist.** The `com.thelightphone.light-sdk` Gradle plugin enforces a
   third-party dependency allowlist that Signal's libsignal / WebRTC / SQLCipher / Wire stack
   cannot satisfy, so `includeBuild` of the SDK is not viable either.

Vendoring the *source* sidesteps both: it is compiled by Molly's own Kotlin compiler against
Molly's own Compose BOM. The original package name `com.thelightphone.sdk.ui` is kept so the files
stay diffable against upstream.

## What was vendored

Kotlin (16 files, `src/main/kotlin/com/thelightphone/sdk/ui/`):

`LightBarButton` · `LightBottomBar` · `LightClickable` · `LightFont` · `LightFullscreenModal` ·
`LightGrid` · `LightHapticFeedback` · `LightIcon` · `LightIcons` · `LightProgressBar` ·
`LightScrollView` · `LightText` · `LightTextField` · `LightTheme` · `LightThemeController` ·
`LightTopBar`

Plus `LightScrollViewLayoutTest` (`src/test/kotlin/`), all **105** vector drawables from
`sdk/ui/src/main/res/drawable/` (every one is referenced by `LightIcons`), and
`consumer-rules.pro`.

## What was deliberately **not** vendored

| File | Reason |
|---|---|
| `LightQrCodeScanner` | Needs CameraX + ML Kit barcode scanning. Molly already has its own QR stack (`:lib:qr`). |
| `LightNfcTapReader` | Needs NFC hardware APIs; no Molly use case. |
| `keyboard/LightEmbeddedLp3Keyboard`, `keyboard/TextInputKeyboardCallback` | Depend on `com.github.lightphone:light-keyboard`, an extra third-party artifact. |
| `LightTextInputEditor` | Depends on the two keyboard files above. |
| `LightModalManager` | Not required by anything vendored. |
| `LightKeyHandler` | LP3 hardware-key plumbing; not required by anything vendored. |

**No link had to be severed.** The vendored set is self-contained: the only references from
vendored files to skipped files are in KDoc prose (`LightTextField` mentions `LightTextInputEditor`;
`LightClickable` mentions `com.thelightphone.sdk.shared.LightServiceMethod`). Neither is a code
dependency, and both comments were left intact. Nothing in the vendored set touches `sdk:client` or
`sdk:shared`.

## Local modifications (keep this list current when re-syncing upstream)

All behavioural changes are marked in-source with a `MOLLY-VENDOR:` comment.

1. **`LightHapticFeedback.kt` — minSdk.** Upstream targets `minSdk 34` and calls
   `VibratorManager#getDefaultVibrator` unconditionally (API 31+). Molly's `minSdk` is 27, so this
   now branches on `Build.VERSION.SDK_INT >= S` and falls back to the deprecated `Vibrator` service,
   with a `hasVibrator()` guard.
2. **`LightFont.kt` — minSdk.** `SystemFonts.getAvailableFonts()` and `android.graphics.fonts.Font`
   are API 29+. The system-font lookup is now behind a `Build.VERSION.SDK_INT >= Q` check and the
   helper is annotated `@RequiresApi(Q)`. Behaviour is unchanged on API 29+.
3. **`LightScrollViewLayoutTest.kt` — test framework.** Upstream uses `kotlin.test`; Molly's
   `signal-library` convention plugin puts JUnit 4 on the unit-test classpath instead, so the
   imports and two assertion signatures were swapped to `org.junit.Assert`.
4. **MIT/SPDX headers** were added to the top of every vendored `.kt` and `.xml` file (upstream
   relies on the repo-root LICENSE file instead of per-file headers).
5. **`AndroidManifest.xml`** was rewritten to declare only `android.permission.VIBRATE`. Upstream
   also declares `android.permission.CAMERA` and the `android.hardware.camera` feature for
   `LightQrCodeScanner` / `LightNfcTapReader`, which are not vendored.

### Build-script notes

* `implementation(libs.androidx.lifecycle.runtime.compose)` and the strict
  `androidx.collection:collection:1.5.0` constraint in `build.gradle.kts` exist so that this module
  resolves the *same* AndroidX graph in isolation as it does inside `:app`. Without them, resolving
  `:core:lightui` on its own transiently selects older `androidx.lifecycle` / `androidx.collection`
  versions whose metadata checksums are not in `gradle/verification-metadata.xml`, and every
  `:core:lightui:*` task fails dependency verification even though `:app` builds fine. The alternative
  — regenerating `gradle/verification-metadata.xml` with `./gradlew updateVerificationMetadata` —
  rewrites the whole security-critical file from a full `qa` run, which is out of proportion to this
  change. Pinning locally was the smaller, safer fix and changes nothing about what `:app` ships.

Notably, **no Kotlin 2.3 language feature had to be removed** — the vendored source compiled
unmodified under Kotlin 2.2.20 — and **no Compose API had to be changed** for Molly's Compose BOM
(`2026.04.01`, one release ahead of the SDK's `2026.03.01`).

### Known lint warnings (not errors, left as-is for diffability)

* `LightGrid.kt` — `ConfigurationScreenWidthHeight`: uses `LocalConfiguration.screenWidthDp` /
  `screenHeightDp` rather than `LocalWindowInfo.current.containerSize`. Molly's own `:core:ui` uses
  `LocalConfiguration` in the same way, so this is consistent with the host codebase. Worth
  revisiting if the grid ever mis-sizes under multi-window / insets.
* `LightTopBar.kt` — `ModifierParameter`: `modifier` is not the first optional parameter.
* `LightFont.kt` — `DiscouragedApi`: `Resources#getIdentifier` for the optional bundled Akkurat
  font files.

## About the Akkurat font

`LightFont.kt` resolves "Akkurat" as an on-device **system** font. It is a licensed typeface and is
deliberately not bundled. It only resolves on real Light Phone III hardware; everywhere else
`lightFontFamily()` falls back to `FontFamily.Default`. This is expected — do not try to source the
font files.

## Re-syncing with upstream

1. Copy the files listed above from `light-sdk/sdk/ui/src/main/`.
2. Re-apply the MIT/SPDX headers and the `MOLLY-VENDOR:` patches listed in "Local modifications".
3. `./gradlew :core:lightui:lintDebug :core:lightui:testDebugUnitTest` must be clean before the
   full `:app:assembleProdWebsiteDebug`.
