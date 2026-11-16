# Changelog

All released versions of Privnum, newest first. Releases are published on
[GitHub](https://github.com/Hcmdz/Privnum/releases).

## Unreleased

- Dependency refresh: AGP 9.5.1, Kotlin 2.4.21, KSP 2.3.13, Room 2.8.5, Compose BOM 2026.09.00, SQLCipher 4.20.0, kotlinx-serialization 1.13.0, exifinterface 1.5.0-beta01, activity 1.14.0-alpha03, navigation3 1.3.0-alpha04, lifecycle 2.12.0-alpha05, test libs 1.4.0/1.8.0-alpha04, jsoup 1.24.1, Jackson 2.23.0, libphonenumber 9.0.43, DataStore 1.3.0, Biometric 1.4.0-beta01.
- CI actions: setup-java 6.2.0, setup-gradle 6.4.1, codeql-action 4.39.3, Gradle wrapper 9.9.0.

## v1.3.2

- **Security hardening** — Caller overlay window now protected by `FLAG_SECURE` (prevents screenshots/recording of caller PII); `CallDirectoryProvider` declares `grantUriPermissions` for defense-in-depth; main Activity excluded from recents to remove task thumbnail leakage.
- Backup exclusion verified: `allowBackup="false"` + full data-extraction rules block both cloud backup and device-to-device transfer.

## v1.3.1

- **The call popup shows the caller's name again.** Operators that report a number in national form were trimmed before lookup, so the number never matched your contacts and no popup appeared for callers you know. It is now normalised the same way the editor does, and an unknown caller is shown with their raw number instead of nothing. A withheld number shows nothing rather than a blank popup.
- **No more flash between screens in dark mode.** The app's own background was being drawn from the default light scheme.
- Search has a back arrow. The system back gesture used to leave the app from there.
- Call history asks the system provider once per visit, and keeps the list when you come back rather than reloading it behind a spinner.
- Contact numbers keep their country code in the details screen. A foreign number used to read like a national one, without its calling code.
- Dependencies carrying known advisories pinned to their fixed versions. None of them ship in the APK.

## v1.3.0

- **Call history**: recent calls now appear in the app, with private names resolved from your own contacts. Unknown numbers get an add button that opens the editor pre-filled. Access is requested explicitly, never on screen entry, and the history is read on demand — nothing is copied into the app database.
- The app now opens straight into contacts, with the system splash held until your contacts finish loading, instead of a separate startup page.
- The primary phone number now survives a vCard backup round trip. Previously the flag followed the first listed number, so a different primary could shift on restore.
- Call popup ignores touches covered by another app's window.

## v1.2.0

- **The contact database is now encrypted at rest.** SQLCipher, with the key held in the platform keystore. An existing database is converted on first open with no action from you. The package grows by about 2 MB, the database engine shipping as a native library.
- Added an in-app language selector with System plus 11 supported app languages.
- Localized UI resources, country names, and dates, including RTL layouts for Arabic.
- vCard 2.1 backups now import in release builds.
- Third-party licences are listed in-app under Settings → About → Open source notices.
- Build: Gradle 9.8.0, Kotlin 2.4.20

## v1.1.0

- Multiple phone numbers per contact (Room v1→v2 migration, primary flag, per-number actions, multi-TEL VCF)

## v1.0.0

- Native conversion: contacts, search, editor, details, settings, lock, VCF backup, caller ID
- Build: AGP 9.4.1, Kotlin 2.4.10
