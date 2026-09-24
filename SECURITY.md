# Security policy

## Reporting a vulnerability

Do not open a public issue for security reports. Use
**GitHub Security Advisories** (repo Security tab → Report a vulnerability)
so the report stays private until a fix ships.

What to include: affected version/tag, steps to reproduce, impact
assessment. Expect an acknowledgement within 7 days and coordinated
disclosure once fixed.

## Scope notes for this app

- All contact data stays in the app-private database (`allowBackup=false`);
  there is no account, backend, analytics, or crash reporting.
- Release builds strip `Log.d/v`; signing keys never enter this repo
  (see README signing section).
- Verified posture: zero network permissions, encrypted passcode store,
  SAF-only backup flow. See `THIRD_PARTY.md` for dependency licenses.
