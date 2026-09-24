# Contributing

## Requirements & Setup

- Android Studio (latest stable), JDK 17+, Android SDK with compileSdk 37
- Emulator or device on API 29+ for device tests

```bash
export JAVA_HOME=<jdk-17> ANDROID_HOME=<sdk>
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

Release signing keys live outside the repo (`RELEASE_*` in
`~/.gradle/gradle.properties`) — debug builds need nothing.

## Workflow

1. Fork the repository, create a feature branch from `main`
2. Make focused changes (smallest correct diff)
3. Keep `./gradlew testDebugUnitTest lintDebug` green
4. Write clear Conventional Commits (`feat/fix/test/chore(scope): …`),
   one concern per commit, no co-author lines or generated-with footers
5. Open a PR describing what changed and how it was verified

## Code Style

- Official Kotlin conventions, Jetpack Compose + Material 3
- Match the existing architecture (Hilt, Room, MVI, StateFlow)
- No second DI framework, no new dependency without need
  (declare it in `gradle/libs.versions.toml`)
- New behavior ships with tests (unit for logic, device for
  platform paths)

## Issues

- Use the provided issue templates
- Include device model, Android version, and app version
  (Settings > About)
- Steps to reproduce for bug reports, expected vs actual

## Never Commit

Secrets, keystores, screenshots of personal data, `AGENTS.md`
(untracked local notes).
