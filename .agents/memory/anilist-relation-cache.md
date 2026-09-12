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

**How to apply:** Prefer stored tracker IDs and cached relation metadata; for AniList-source
items use the numeric source URL as the media ID; title fallback must require one exact
canonical-title match and never choose a merely similar result.