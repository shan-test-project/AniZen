---
name: AniZen checkout location
description: The workspace contains a nested stale AniZen tree and the current GitHub checkout at its root.
---

When fixing or triggering the AniZen GitHub repository, use the Android sources and workflows at the workspace root, not the nested `anizen/` directory.

**Why:** Fetching the GitHub repository into the workspace-level Git repository places its tracked files at the root; editing the nested copy can produce a correct-looking local change that never reaches the requested repository.

**How to apply:** Confirm the active Git root and current remote commit before editing, then patch root-level `app/` and `.github/` paths.