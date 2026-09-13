# Fix spec: AI diagnosis assistant appears completely unresponsive

Repo: `shan-test-project/AniZen` (fork of `salmanbappi/AniZen`)
File: `app/src/main/java/eu/kanade/tachiyomi/data/ai/AiManager.kt`
Branch: `master`

## Symptom

The in-app "AniZen System Assistant" (AI diagnostics chat, under
Settings → Advanced Analytics / AI Config) sometimes does nothing at all when
a message is sent — no reply, no error message, no toast, nothing visibly
happens.

## Already ruled out — do not re-investigate

`AiManager.kt` contains a remote "kill switch" check:

```kotlin
private val REMOTE_KILL_SWITCH_URL = "https://raw.githubusercontent.com/salmanbappi/anikku-config/main/ai_kill_switch.json"

private suspend fun isRemoteKillSwitchActive(): Boolean = withIOContext {
    try {
        ...
        client.newCall(request).execute().use {
            if (it.isSuccessful) { ... } else false
        }
    } catch (e: Exception) {
        false // Default to enabled if network fails
    }
}
```

That URL points at a repository (`salmanbappi/anikku-config`) that does not
exist — the request 404s, `isSuccessful` is `false`, and the function
correctly returns `false` (fails open / not disabled). This is **not** the
cause of the symptom. Don't spend time on it.

## Root cause

```kotlin
fun chatWithAssistantStream(query: String, history: List<ChatMessage>): Flow<String> = flow {
    if (!aiPreferences.enableAi().get() || !aiPreferences.enableAiAssistant().get()) return@flow
    ...
```

There are two independent settings toggles gating this feature:
`enableAi()` (a global "AI features" switch) and `enableAiAssistant()` (a
switch specifically for the assistant chat; there's a separate third one,
`enableAiStatistics()`, gating a different AI feature). If **either** of the
first two is off, this function returns immediately from inside a `flow {}`
builder with **zero emissions** — no text, no error string, nothing collected
downstream. From the chat UI this is indistinguishable from the assistant
being completely broken: the user sends a message and the screen just never
updates.

This is easy to hit in practice: the app has three separate AI toggles that
look similar in Settings, so turning on "AI features" without separately
confirming "AI Assistant" is also on (or vice versa, or after one gets reset)
silently produces this exact "nothing happens" behavior with no diagnostic
trail at all — including no logcat line, since it returns before any logging
occurs.

## Fix

Replace the single silent guard with two explicit checks that each emit a
clear, actionable message identifying which specific setting is off:

```kotlin
fun chatWithAssistantStream(query: String, history: List<ChatMessage>): Flow<String> = flow {
    if (!aiPreferences.enableAi().get()) {
        emit("AI features are turned off. Enable 'AI Integration' in Settings > Advanced Analytics (AI Config) to use the assistant.")
        return@flow
    }
    if (!aiPreferences.enableAiAssistant().get()) {
        emit("The AI Assistant is turned off. Enable 'AI Assistant' in Settings > Advanced Analytics (AI Config) to chat here.")
        return@flow
    }

    // A request can leave these preferences set when Android kills the
    // process while the provider is streaming. Recover that state once for
    // this manager instance so it cannot lock the assistant forever.
    recoverInterruptedRequestState()
    if (isRemoteKillSwitchActive()) {
        emit("Service Maintenance: AI Assistant is currently offline.")
        return@flow
    }

    val engine = aiPreferences.aiEngine().get()
    val apiKey = when (engine) {
        "gemini" -> aiPreferences.geminiApiKey().get()
        "deepseek" -> aiPreferences.deepseekApiKey().get()
        "opencode" -> aiPreferences.opencodeApiKey().get()
        "literouter" -> aiPreferences.literouterApiKey().get()
        "tokenreply" -> aiPreferences.tokenreplyApiKey().get()
        "openai" -> aiPreferences.openaiApiKey().get()
        "anthropic" -> aiPreferences.anthropicApiKey().get()
        "openrouter" -> aiPreferences.openrouterApiKey().get()
        "together" -> aiPreferences.togetherApiKey().get()
        else -> aiPreferences.groqApiKey().get()
    }.ifBlank {
        emit("Please set an API Key in Settings > AI Integration")
        return@flow
    }

    // ...rest of the function is unchanged from here on...
```

Everything after the API-key check (`customPrompt`, `systemInstruction`,
building `messages`, the provider `when` dispatch, the `finally` block) is
**unchanged** — only the top guard clause is being split into two explicit,
user-visible branches instead of one silent `return@flow`.

### Why this is the right fix, not a workaround

- It doesn't change any gating logic or default values — a toggle that was
  off still blocks the assistant exactly as before. It only makes the
  blocked state observable to the user instead of indistinguishable from a
  crash/hang.
- It matches the existing pattern already used a few lines below for the
  missing-API-key case (`emit(...); return@flow`), so it's consistent with
  the rest of the function rather than introducing a new convention.

## Acceptance criteria

1. With `enableAi` off (regardless of `enableAiAssistant`), sending a message
   shows: *"AI features are turned off. Enable 'AI Integration' in Settings >
   Advanced Analytics (AI Config) to use the assistant."*
2. With `enableAi` on and `enableAiAssistant` off, sending a message shows:
   *"The AI Assistant is turned off. Enable 'AI Assistant' in Settings >
   Advanced Analytics (AI Config) to chat here."*
3. With both on and a valid API key, behavior is unchanged from before this
   fix (normal streamed response from the configured provider).
4. With both on and no API key set, the existing *"Please set an API Key in
   Settings > AI Integration"* message still appears (unchanged).
5. `getStatisticsAnalysisStream()` (the separate AI Statistics feature) is
   **not** touched by this fix — it has its own toggle (`enableAiStatistics`)
   and its own silent-return guard, which is out of scope here.
