# Privnum-native

Privnum — private local caller ID — **converted to a 100% Kotlin native
Android app from [Alternate](https://github.com/BioHazard786/Alternate)
(MIT, © BioHazard786)**. Same project converted in place: UI and data
layers rewritten from scratch (the original is React Native + Expo, so no
TypeScript was copied); the existing Kotlin caller module was adapted.
The app requests **zero network permissions**.

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

## Docs

- `THIRD_PARTY.md` — dependency licenses and brand-asset notes
- `SECURITY.md` — vulnerability reporting
- `CONTRIBUTING.md` — setup and conventions
- `LICENSE` — MIT
