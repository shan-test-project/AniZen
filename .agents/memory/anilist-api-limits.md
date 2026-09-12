---
name: AniList request quota
description: The external AniList API quota applies across authenticated and public GraphQL requests.
---

Authenticated and public AniList GraphQL requests share the same request quota. Any public relation or title-resolution request must use the same rate limiter as tracker mutations.

**Why:** Requests that bypass the tracker limiter can consume the quota before a tracking mutation runs, causing the user-visible HTTP 429 error.

**How to apply:** When adding an AniList API call, route it through the shared limited client rather than the base OkHttp client.