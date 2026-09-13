---
name: GitHub Actions APK builds
description: How this project should validate Android APK changes without running Gradle locally.
---

Use the repository's GitHub Actions workflow for APK validation when local Android builds are intentionally avoided. A GitHub token may be available to shell commands as `GITHUB_KEY` even when the secure secret-request callback does not return a value; use it only in authenticated commands and never print it.

**Why:** The arm64 maximum-R8 build caught Kotlin/Android API issues that static file checks could not detect, and the remote build is the required source of truth for the APK.

**How to apply:** Push the corrected branch, dispatch `.github/workflows/debug-v8a.yml` for that branch, monitor the exact run, and retrieve the named artifact only after the run succeeds.