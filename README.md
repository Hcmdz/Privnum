# Privnum
<a id="readme-top"></a>

[![Privnum](docs/assets/feature-graphic.png)](https://github.com/Hcmdz/Privnum)

<div align="center">
<a href="https://github.com/Hcmdz/Privnum">
    <img src="assets/app-icon/icon.png" alt="Privnum logo" width="100" height="100" style="border-radius:15px">
</a>
<br />
<br />
    <a href="https://github.com/Hcmdz/Privnum/issues">Report Bug</a>
    ·
    <a href="https://github.com/Hcmdz/Privnum/issues">Request Feature</a>
    <br />
    <br />
</div>

<div align="center">
   <a href="https://github.com/Hcmdz/Privnum/releases">
      <img src="get-it-on-github.png" width="170">
   </a>
   <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/Hcmdz/Privnum/">
      <img src="get-it-on-obtainium.png" width="170">
   </a>
</div>

<br />

[![Android](https://img.shields.io/badge/Platform-Android-green.svg?logo=android)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![MinSDK](https://img.shields.io/badge/MinSDK-29-orange.svg)](#)
[![TargetSDK](https://img.shields.io/badge/TargetSDK-36-blue.svg)](#)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Stars](https://img.shields.io/github/stars/Hcmdz/Privnum)](https://github.com/Hcmdz/Privnum/stargazers)
[![Forks](https://img.shields.io/github/forks/Hcmdz/Privnum)](https://github.com/Hcmdz/Privnum/network/members)
[![Compose M3](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)

Privnum — private local caller ID — **converted to a 100% Kotlin native
Android app from [Alternate](https://github.com/BioHazard786/Alternate)
(MIT, © BioHazard786)**. Same project converted in place: UI and data
layers rewritten from scratch (the original is React Native + Expo, so no
TypeScript was copied); the existing Kotlin caller module was adapted.

Unknown callers identified from a private on-device database — never
touching the system contacts, WhatsApp, Telegram, or the network: the app
requests **zero network permissions**.

- **Package**: `com.hcmdz.privnum`
- **Version**: 1.1.0 (versionCode 2)

<details>
<summary>Table of Contents</summary>
<ol>
<li><a href="#-screenshots">Screenshots</a></li>
<li><a href="#-key-features">Key Features</a></li>
<li><a href="#️-tech-stack--architecture">Tech Stack &amp; Architecture</a></li>
<li><a href="#-getting-started">Getting Started</a></li>
<li><a href="#-project-structure">Project Structure</a></li>
<li><a href="#-signing">Signing</a></li>
<li><a href="#-apk-size">APK Size</a></li>
<li><a href="#-changelog">Changelog</a></li>
<li><a href="#-license">License</a></li>
<li><a href="#-related-docs">Related Docs</a></li>
</ol>
</details>

---

## 📸 Screenshots

| Contacts | Editor | Details |
|---|---|---|
| [![Contacts light](screenshots/contacts-light.png?v=2)](screenshots/contacts-light.png?v=2) | [![Editor light](screenshots/editor-light.png?v=2)](screenshots/editor-light.png?v=2) | [![Details light](screenshots/preview-light.png?v=2)](screenshots/preview-light.png?v=2) |
| [![Contacts dark](screenshots/contacts-dark.png?v=2)](screenshots/contacts-dark.png?v=2) | [![Editor dark](screenshots/editor-dark.png?v=2)](screenshots/editor-dark.png?v=2) | [![Details dark](screenshots/preview-dark.png?v=2)](screenshots/preview-dark.png?v=2) |

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## ✨ Key Features

- **Private caller ID.** Incoming/outgoing call popup with name, photo and
  notes resolved from the local database; system call-directory provider
  included.
- **Contacts stay out of the system.** Add, search (FTS prefix, text and
  digits combined), preview, edit, import/export/share VCF (photos
  included) — nothing ever leaves the app database.
- **Several numbers per contact.** Dynamic editor rows with per-row
  country, one primary flag, and duplicate protection across contacts
  (a taken number names its owner instead of merging); caller lookup,
  preview actions, WhatsApp/Telegram rows and VCF backup all cover
  every number.
- **Lock it down.** Optional passcode (5-attempt lockout), biometrics,
  auto-lock timeouts, instant lock, encrypted storage.
- **Yours to theme.** Dynamic (Material You) colors with toggle, pure-black
  AMOLED mode, light/dark/system.
- **Faces with names.** Optional contact photos (gallery or camera, cropped
  square on-device), shown in list, details and call popup, kept through
  VCF backup.
- **Offline by construction.** No accounts, no analytics, no crash
  reporting, no `INTERNET` permission.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## 🛠️ Tech Stack & Architecture

Multi-module `:app` + `core/*` + `feature/*`, Nav3 graph, MVI screens with
Hilt ViewModels and StateFlow; Room repository pattern; no network layer.

### Core Libraries & Tools

| Category | Library | Version |
|---|---|---|
| UI | Jetpack Compose + Material 3 | BOM 2026.09.00 |
| Async | Kotlin Coroutines & Flow | 1.11.0 |
| DI | Hilt | 2.60.1 |
| Database | Room (+FTS4) | 2.8.5 |
| Settings | DataStore Preferences | 1.1.3 |
| Phone numbers | libphonenumber | 9.0.40 |
| VCF | ez-vcard | 0.12.2 |
| Images | Coil 3 | 3.6.3 |
| Camera metadata | Exifinterface | 1.4.2 |
| Biometrics | AndroidX Biometric | 1.4.0-alpha07 |
| JSON | kotlinx.serialization | 1.11.0 |
| Build | AGP 9.4.1, Kotlin 2.4.10, KSP 2.3.12, compileSdk 37, minSdk 29 | — |

### Security Features

| Control | Implementation |
|---|---|
| Encryption at rest | EncryptedSharedPreferences (AES256-GCM, Keystore-backed master key) for passcode store |
| Backup disabled | `allowBackup="false"` |
| Log hygiene | Release builds strip `Log.d/v` (R8 + shrink) |
| No network | Zero network permissions declared or requested |

### Permissions

- `READ_PHONE_STATE` — read incoming/outgoing call state for the popup
- `READ_CALL_LOG` — call-state resolution where the system requires it
- `SYSTEM_ALERT_WINDOW` — draw the caller popup over other apps (opt-in, with rationale)
- `POST_NOTIFICATIONS` — call-related notifications (Android 13+)
- `DISABLE_KEYGUARD` — show the popup over the lock screen
- `CAMERA` — not requested; photos come from the gallery picker or capture intent (no permission needed)

Entry points: `MainActivity` (launcher), `CallReceiver` (phone-state broadcasts), `CallDetectScreeningService` (role-gated, `BIND_SCREENING_SERVICE`), `CallDirectoryProvider` (`READ_CONTACTS`-guarded lookup).

### CI & Quality

- GitHub Actions: `.github/workflows/ci.yml` (unit tests + lint + debug build), CodeQL (java-kotlin, weekly), Dependabot (gradle + actions, weekly)
- Static analysis: detekt (`./gradlew detekt`, config in `config/detekt/`)
- Gate: `./gradlew testDebugUnitTest lintDebug`

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (Ladybug or newer) — IDE ([doc](https://developer.android.com/studio))
- **JDK 17** — Gradle toolchain ([doc](https://docs.gradle.org/current/userguide/build_java_projects.html))
- **Android SDK** with compileSdk 37; emulator or device on API 29+

### Installation

```bash
git clone https://github.com/Hcmdz/Privnum.git
cd Privnum
export JAVA_HOME=<jdk-17> ANDROID_HOME=<sdk>
./gradlew assembleDebug     # app/build/outputs/apk/debug/Privnum-debug.apk
./gradlew installDebug      # run on device
```

### Testing

```bash
./gradlew testDebugUnitTest   # VCF round-trip, phone utils, FTS, screening, countries
./gradlew lintDebug
```

DAO/device tests run on demand against the emulator (caller overlay
verified end to end with `adb emu gsm call`).

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## 📁 Project Structure

```
app/src/main/java/com/hcmdz/privnum/
├── MainActivity.kt              # Entry point, lock gate, theme
├── AppNav.kt                    # Nav3 graph (contacts/search/editor/preview/settings/lock)
├── PrivnumApplication.kt        # Hilt application
└── res/xml/file_paths.xml       # FileProvider paths (VCF/photo sharing)
core/caller/                    # Screening service, receiver, directory provider, overlay UI
core/data/                      # Room (contacts + phone_numbers + FTS4,
│   │                           # exportSchema=true, v1→v2 migration), repository,
│   │                           # DataStore settings, encrypted passcode, VCF, photo store
│   └── db/                      # Entities, DAO, database + schemas/
core/ui/                        # Material 3 theme + shared ContactAvatar
feature/contacts|editor|        # MVI screens + Hilt ViewModels + unit tests
  search|settings|lock|preview/
gradle/libs.versions.toml       # Single source for dependency versions
.github/workflows/ci.yml        # test + lint + debug build
screenshots/                    # Light- and dark-mode captures used above
docs/privacy|terms/             # Published Privacy Policy and Terms of Service
config/detekt/                  # Static-analysis rules
```

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## 🔏 Signing

Release signing reads the shared `RELEASE_*` keys from
`~/.gradle/gradle.properties` (same identity as the author's other
apps, one rotation point). Unsigned/misconfigured builds fail loudly
at `packageRelease` — never silently.

```properties
RELEASE_STORE_FILE=/path/to/your/release.keystore
RELEASE_KEY_ALIAS=ALIAS
RELEASE_STORE_PASSWORD=YOUR_KEYSTORE_PASSWORD
RELEASE_KEY_PASSWORD=YOUR_KEY_PASSWORD
```

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/Privnum-release.apk
apksigner verify --print-certs app/build/outputs/apk/release/Privnum-release.apk
```

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## 📏 APK Size

Current release APK: **~5.0 MB** (universal, single APK, R8 + shrink enabled, V3-signed).

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## 📝 Changelog

### v1.1.0
- Multiple phone numbers per contact (Room v1→v2 migration, primary flag, per-number actions, multi-TEL VCF)

### v1.0.0
- Native conversion: contacts, search, editor, details, settings, lock, VCF backup, caller ID
- Build: AGP 9.4.1, Kotlin 2.4.10

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## ⚖️ Legal

- [Terms of Service](docs/terms/)
- [Privacy Policy](docs/privacy/)

---

## 📄 License

Copyright (c) 2026 Hcmdz. This project is licensed under the
[GNU General Public License v3.0 or later](https://www.gnu.org/licenses/gpl-3.0) —
see the [LICENSE](LICENSE) file for details.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## 🔗 Related Docs

- [Contributing](CONTRIBUTING.md) · [Third-Party Components](THIRD_PARTY.md) · [Security](SECURITY.md)

## 📬 Contact

Hcmdz — [@Hcmdz](https://github.com/Hcmdz)

Project link: [https://github.com/Hcmdz/Privnum](https://github.com/Hcmdz/Privnum)

## 🙏 Acknowledgments

- [Alternate](https://github.com/BioHazard786/Alternate) by BioHazard786 — the converted project (caller native module adapted from its Kotlin code)
- [dmkvsk](https://github.com/dmkvsk/react-native-detect-caller-id) — native-module inspiration (via upstream)
- [SimpleNexus](https://github.com/SimpleNexus/simplecallerid) — call-directory implementation (via upstream)
