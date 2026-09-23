# Privnum-native

100% Kotlin rewrite of [Privnum](https://github.com/Hcmdz/Privnum)
(itself an MIT fork of [Alternate](https://github.com/BioHazard786/Alternate)
by BioHazard786) — private local caller ID without Expo, Metro, accounts
or network: the app requests **zero network permissions**.

## Modules

- `:app` — Nav3 graph, lock gate, MainActivity
- `:core:ui` — Material 3 Expressive theme engine (dynamic color)
- `:core:data` — Room (contacts + FTS4, `exportSchema=true`),
  DataStore settings, encrypted passcode store, VCF (`ez-vcard`),
  `libphonenumber`, 194-country table
- `:core:caller` — `CallScreeningService` (primary, role-gated),
  `CallReceiver` fallback, `CallDirectoryProvider`, overlay UI
- `:feature:contacts|editor|search|settings|lock` — MVI screens,
  Hilt ViewModels

## Build

Prerequisites: JDK 17, Android SDK (compileSdk 37).

```bash
export JAVA_HOME=<jdk-17> ANDROID_HOME=<sdk>
./gradlew assembleDebug     # universal APK, standalone (bundled JS-free: pure native)
./gradlew assembleRelease   # arm64, R8 + shrink, signed with shared key
```

Release signing reads the shared `RELEASE_*` keys from
`~/.gradle/gradle.properties` (same identity as the author's other
apps, one rotation point). Unsigned/misconfigured builds fail loudly
at `packageRelease` — never silently.

```bash
apksigner verify --print-certs app/build/outputs/apk/release/Privnum-release.apk
bash ~/.config/opencode/skills/android-app-signing/scripts/verify-signing.sh <apk>
```

## Tests

```bash
./gradlew testDebugUnitTest   # VCF round-trip + TS-format interop, phone utils, FTS builder, screening decisions, countries
./gradlew lintDebug
```

DAO/device tests run on demand against the emulator (caller overlay
verified end to end with `adb emu gsm call`).

## Privacy model

All data stays in the app-private Room database (`allowBackup=false`).
Release builds strip `Log.d/v`. No analytics, no crash reporting,
no network access.
