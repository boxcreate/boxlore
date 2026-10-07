# Contributing to boxlore

Thank you for your interest in **boxlore**!

## Licensing & Contribution Policy

boxlore is source-available under the [PolyForm Strict License 1.0.0](LICENSE). Under the terms of this license, personal and noncommercial use is permitted, but redistribution, modifications, and derivative works are restricted.

Because of this licensing model, **we do not recommend, solicit, or welcome code contributions or pull requests**. 

We ask that you please do not spend time writing code or opening pull requests for new features, refactors, or fixes, as external pull requests will generally not be accepted or merged.

## We Welcome Suggestions & Feedback Instead!

While we don't accept code contributions, we **deeply appreciate and actively encourage your suggestions, feedback, and bug reports**. Your ideas directly shape the roadmap and design of boxlore.

Here is how you can help:

### 1. Suggesting Features & Enhancements
If you have an idea for a new capability, UI improvement, or workflow enhancement:
- Open an issue using the **Feature Request** or **Enhancement** template in our [GitHub Issues](https://github.com/boxcreate/boxlore/issues/new/choose).
- Join the conversation and share ideas on [GitHub Discussions](https://github.com/boxcreate/boxlore/discussions).
- Clearly describe your use case, why it matters, and how you envision the feature working.

### 2. Reporting Bugs & Issues
If you encounter unexpected behavior, crashes, or visual glitches:
- Submit an issue using the **Bug Report** or **Crash Report** template in our [GitHub Issues](https://github.com/boxcreate/boxlore/issues/new/choose).
- Include device specifications (model, Android version), the boxlore version, and clear steps to reproduce the problem.

### 3. Security Vulnerabilities
If you discover a security vulnerability or sensitive data exposure, please report it privately through [GitHub Security Advisories](https://github.com/boxcreate/boxlore/security/advisories/new) rather than opening a public issue.

---

## API & Backend Inquiries

The recommendation, search, and catalog backend proxy is tracked in a separate private repository for security reasons. If you would like to request new search filters, catalog capabilities, or endpoint changes, please submit an issue on this repository using the **Feature Request** template, and it will be evaluated for implementation.

## Local Development & Personal Use

In accordance with the [PolyForm Strict License 1.0.0](LICENSE), you are welcome to build and run boxlore locally for personal study, testing, and private noncommercial use. Refer to the [README.md](README.md) and [ARCHITECTURE.md](ARCHITECTURE.md) for build setup and module details.

### Requirements

| Tool | Requirement |
| :--- | :--- |
| Java | JDK 17; set `JAVA_HOME` and the Android Studio Gradle JDK to this installation |
| Android SDK | Platform 36, Platform-Tools, and the Build-Tools version requested by the Android Gradle plugin |
| Gradle | Use the checked-in `./gradlew` wrapper; no separate Gradle installation is needed |
| Node.js | Version 20 and npm for scripts; not needed for ordinary Android builds |
| Python | Python 3 for local configuration and release-tooling tests |
| Device | Android 12 / API 31 or newer when running the app |

Set `ANDROID_HOME` to the SDK installation and add its `platform-tools` directory to `PATH`. Accept SDK licenses through Android Studio or `sdkmanager --licenses`. Verify that `java -version` reports 17 before building.

### Build configuration

From the repository root, create non-secret local configuration:

```bash
bash scripts/ci/write-cloud-agent-local-config.sh
./gradlew assembleDebug
```

The configuration script writes `sdk.dir` in `local.properties` and a non-secret `app/google-services.json` stub. It preserves either file when it already exists. Verify `sdk.dir` if the script cannot find your SDK. Both files are ignored by Git; keep them untracked and never commit real service configuration, keys, or signing material.

The stub supports building and JVM checks without production credentials. It does not provide working Firebase services or API access. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Running locally

Building and launching have different configuration requirements. The app constructs its API client during startup, so an empty `BOXLORE_API_BASE_URL` causes startup to fail. For a local launch without backend access, add these non-secret placeholders to the existing `local.properties`, retaining `sdk.dir`:

```properties
BOXLORE_API_BASE_URL=https://api.boxlore.example/
BOXLORE_PUBLIC_KEY=demo-placeholder-key
```

Rebuild after changing these values. The placeholder has a valid URL format but supplies no backend: search, recommendations, and briefing cannot retrieve live results. Use local library, downloads, and RSS/OPML flows for checks that do not need the API. Working backend and Firebase services require separately provisioned configuration.

Connect a device or start an emulator, then install and launch:

```bash
adb devices
./gradlew installDebug
adb shell am start -n cx.aswin.boxlore/.MainActivity
```

Use the [test selection guide](docs/TESTING.md#choose-checks-for-a-change) for validation commands and [architecture guide](ARCHITECTURE.md) for module ownership. Local maintainer environments also use root `AGENTS.internal.md` for private workflow and operational instructions.
