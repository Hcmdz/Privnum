# Security policy

## Reporting a vulnerability

Do not open a public issue for security reports. Use
**GitHub Security Advisories** (repo Security tab → Report a vulnerability)
so the report stays private until a fix ships.

What to include: affected version or tag, steps to reproduce, and an
assessment of the impact. Expect an acknowledgement within 7 days and
coordinated disclosure once a fix is released.

Reports against a build made from source are as welcome as reports
against a released APK: state which one you mean, since the build classpath
and the shipped artifact do not contain the same code.

## Supported versions

Only the current release receives security fixes.

| Version | Security fixes |
|---|---|
| 1.3.1 | yes |
| 1.2.0 and earlier | no — please upgrade |

## What the app protects

| Control | Implementation |
|---|---|
| Contact database at rest | SQLCipher. A 32-byte key is generated once and stored only wrapped under a non-exportable platform keystore key. A database written before encryption existed is converted on first open: rows are copied, the row count is verified, and only then is the plaintext file replaced |
| Contact photos at rest | AES-GCM per file under a non-exportable platform keystore key. Files written before encryption stay readable and are re-encrypted on their next write |
| Passcode store | The PIN is derived with PBKDF2-HMAC-SHA256 and compared in constant time; the biometric token stays AES-GCM-wrapped under an auth-bound keystore key |
| Backup and device transfer | `allowBackup="false"` plus data-extraction rules excluding every domain from **both** cloud backup and device-to-device transfer. `allowBackup` alone is not sufficient: on Android 12+ it does not disable transfer on every OEM |
| Screenshots and screen recording | `FLAG_SECURE` on the single activity window, so capture stays blocked across every navigation |
| Log hygiene | Release builds strip `Log.d/v/e/w`, which may otherwise carry phone numbers |
| Network | No `INTERNET` permission is declared or requested. No account, backend, analytics, or crash reporting |
| Signing keys | Never enter this repository. The CI secret gate fails the build when a path on the sensitive-filename list is tracked |

## What leaves the device, and what is read

Two things cross the boundary, both deliberate:

- **VCF export and backup.** Only ever on an explicit user action, through
  the Storage Access Framework. Import and export cover every field the app
  stores, photos included.
- **Call history.** Read from the system call-log provider on demand, behind
  an explicit permission request, and used to resolve names and offer
  add-to-contact. It is never copied into the app database.

The app never reads the platform contact list. `READ_CONTACTS` appears only
as the read and write permission of the `CallDirectoryProvider`, so the
*system* resolves callers outside the app rather than the app reading it.

## Known limitations

- **Encryption at rest has no recovery path.** The database key lives only in
  the platform keystore and there is no cloud copy to restore from, so a lost
  key means unrecoverable contacts. Every event that destroys the key —
  uninstall, factory reset, wiping the device — also erases the app data
  holding them, which limits the exposure to keystore corruption. That case
  surfaces as an explicit error rather than an empty contact list.
- **Build-time advisories are triaged, not fixed, when they cannot be.** The
  Android Gradle Plugin and its build classpath carry published advisories
  that no stable release resolves. Those artifacts are not part of the
  shipped APK, verified against the released DEX, so such findings are
  recorded against the build rather than the product. Anyone compiling from
  source should weigh that themselves.

## Third-party components

Dependency licences are inventoried in [THIRD_PARTY.md](THIRD_PARTY.md) and
reproduced in the app under Settings → About → Open source notices.