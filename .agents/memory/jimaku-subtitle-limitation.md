---
name: Jimaku subtitle limitation
description: External Jimaku API behavior relevant to subtitle-language selection
---

Jimaku is a Japanese-subtitle directory. Its file endpoint returns filename, URL, size, and modification time, but no language field. Filename-based English filtering can prevent accidental Japanese selection, but cannot create an English-subtitle source when no English-labelled file exists.

**Why:** The player previously selected the highest-ranked file without knowing its language, which could load Japanese subtitles when English was requested.

**How to apply:** Keep English selection conservative. If English is a product requirement, add or choose a provider that actually supplies English subtitles rather than assuming Jimaku can do so.