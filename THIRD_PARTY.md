# Third-party components

Project code is MIT (`LICENSE`). This file lists third-party components
shipped or used at build time. Licenses verified against the artifacts
resolved by Gradle (POM `license` blocks in the dependency cache).

## Adapted upstream code

`core/caller` (screening service, receiver, directory provider, Room
entity) is adapted from the pre-existing Kotlin native module of
[Alternate](https://github.com/BioHazard786/Alternate)
(`modules/caller-id`, MIT © BioHazard786): same classes, rebranded
package, Expo bridge removed, screening role added. Everything else
(UI, data, settings, lock, VCF, editor, preview) is a from-scratch
Kotlin rewrite — the original app is React Native + Expo, so no
TypeScript was copied. Inherited upstream thanks: dmkvsk
(native-module inspiration) and SimpleNexus (call directory
implementation), per Alternate's acknowledgments.

## Runtime dependencies

| Project | Version | License (SPDX) | Usage |
|---|---|---|---|
| libphonenumber (`com.googlecode.libphonenumber`) | 9.0.40 | Apache-2.0 | Number parsing, validation, formatting |
| ez-vcard (`com.googlecode.ez-vcard`) | 0.12.2 | FreeBSD (BSD-2-clause style) | VCF import/export |
| Coil 3 (`io.coil-kt.coil3:coil-compose`) | 3.3.0 | Apache-2.0 | Contact photo loading |
| AndroidX libraries (room, datastore, biometric, security-crypto, activity, lifecycle, navigation3, exifinterface, core) | catalog | Apache-2.0 | Persistence, settings, crypto, UI, camera metadata |
| Hilt / Dagger (`com.google.dagger`) | 2.60.1 | Apache-2.0 | Dependency injection |
| kotlinx.coroutines / serialization / org.json | catalog | Apache-2.0 | Concurrency, JSON |

## Test-only dependencies

| Project | Version | License (SPDX) | Usage |
|---|---|---|---|
| JUnit 4 | 4.13.2 | EPL-1.0 | Unit tests |
| androidx.test (core, runner, ext.junit), room-testing, arch core-testing | catalog | Apache-2.0 | Device tests |

## Brand assets (not code)

`feature/preview/.../drawable-nodpi/{whatsapp,telegram}.png` are the
WhatsApp/Telegram brand icons, used solely to label interoperability rows
that deep-link to those apps. Trademarks belong to their respective owners.

## Data

The country table (`core/data/.../Countries.kt`: names, ISO codes, dial
codes, flag emoji) is factual numbering data, same class as ITU records.
