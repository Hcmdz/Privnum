# Third-party components

Project code is GPL-3.0-or-later (`LICENSE`). This file lists third-party
components shipped or used at build time. Licenses verified against the
artifacts resolved by Gradle (POM `license` blocks in the dependency
cache). Copyleft note: distributing the app (e.g. APKs) requires making
the corresponding source available under GPL-3.0-or-later — this public
repository satisfies that.

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
| ez-vcard (`com.googlecode.ez-vcard`) | 0.12.2 | BSD-2-Clause | VCF import/export. Also vendors Apache Commons Codec, Apache-2.0, under `ezvcard/commons-codec.license`, and pulls in jsoup, FreeMarker, Jackson core, Jakarta Inject and Vinnie |
| jsoup (`org.jsoup`) | 1.22.1 | MIT | HTML sanitising, pulled in transitively by ez-vcard |
| FreeMarker (`org.freemarker`) | 2.3.34 | Apache-2.0 | Templating, pulled in transitively by ez-vcard |
| Jackson core (`com.fasterxml.jackson.core`) | 2.21.0 | Apache-2.0 | JSON processing, pulled in transitively by ez-vcard. Ships a `NOTICE`, reproduced in [NOTICE](NOTICE) |
| Jakarta Dependency Injection (`jakarta.inject`) | catalog | Apache-2.0 | DI annotations, pulled in transitively by ez-vcard. Ships a `NOTICE`, reproduced in [NOTICE](NOTICE) |
| Vinnie (`com.github.mangstadt`) | 2.0.2 | MIT | MIME parsing, pulled in transitively by ez-vcard |
| Coil 3 (`io.coil-kt.coil3:coil-compose`) | 3.6.3 | Apache-2.0 | Contact photo loading |
| SQLCipher (`net.zetetic:sqlcipher-android`) | 4.19.0 | BSD-3-Clause | On-device database encryption; ships `libsqlcipher.so` (2.00 MB) per ABI. Full licence text in [NOTICE](NOTICE) |
| AndroidX libraries (room, datastore, biometric, security-crypto, activity, lifecycle, navigation3, exifinterface, core) | catalog | Apache-2.0 | Persistence, settings, crypto, UI, camera metadata |
| AndroidX datastore protobuf (`androidx.datastore:datastore-preferences-external-protobuf`) | 1.1.3 | BSD-3-Clause | Protobuf runtime behind DataStore preferences |
| Hilt / Dagger (`com.google.dagger`) | 2.60.1 | Apache-2.0 | Dependency injection |
| kotlinx.coroutines / serialization / org.json | catalog | Apache-2.0 | Concurrency, JSON |

Every runtime artefact is enumerated by `./gradlew :app:licensee`, which fails
the build on a licence not explicitly allowed. The accepted exceptions and the
reason for each are in `app/build.gradle.kts`; the generated verdict per
artefact lands in `app/build/reports/licensee/`.

## Test-only dependencies

| Project | Version | License (SPDX) | Usage |
|---|---|---|---|
| JUnit 4 | 4.13.2 | EPL-1.0 | Unit tests |
| androidx.test (core, runner, ext.junit), room-testing, arch core-testing | catalog | Apache-2.0 | Device tests |

## Data

The country table (`core/data/.../Countries.kt`: names, ISO codes, dial
codes, flag emoji) is factual numbering data, same class as ITU records.
