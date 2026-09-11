---
name: AniList relation availability
description: Why tracked-anime relation cards need local persistence during AniList outages.
---

Public AniList relation metadata should be retained locally after a successful fetch. A
tracked row stores the remote media ID and tracking fields, but it does not contain the
prequel/sequel graph, so tracking alone cannot reconstruct relation cards while AniList is
unavailable.

**Why:** AniList outages otherwise make relation cards disappear after an app restart even
though the anime remains tracked.

**How to apply:** Prefer stored tracker IDs and cached relation metadata; isolate failed
tracker/network lookups so one unavailable provider does not suppress results from another.