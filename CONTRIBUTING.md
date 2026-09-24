# Contributing

## Setup

Prerequisites: JDK 17, Android SDK (compileSdk 37).

```bash
export JAVA_HOME=<jdk-17> ANDROID_HOME=<sdk>
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

Release signing keys live outside the repo (`RELEASE_*` in
`~/.gradle/gradle.properties`) — debug builds need nothing.

## Conventions

- Conventional Commits (`feat/fix/test/chore(scope): …`), one concern per
  commit, no co-author lines or generated-with footers.
- Smallest correct diff; match existing architecture (Hilt, Room, MVI);
  no second DI framework, no new dependency without need
  (declare it in `gradle/libs.versions.toml`).
- New behavior ships with tests (unit for logic, device for
  platform paths); gate is `testDebugUnitTest` + `lintDebug` green.
- Never commit secrets, keystores, screenshots of personal data, or
  `AGENTS.md` (untracked local notes).
