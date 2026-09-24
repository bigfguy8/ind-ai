# Ind AI

A personal Android AI assistant with voice, memory, device control, screen vision, and an autonomous agent.

**One-click install:** [Download the latest APK](https://github.com/bigfguy8/ind-ai/releases/latest) → install → pick a provider → done.

---

## Features

- **Streaming chat** with markdown rendering
- **Voice input & output** — text-to-speech, speech recognition, continuous hands-free mode
- **Persistent semantic memory** — facts you tell it are stored, embedded, and retrieved by relevance
- **Auto-extraction** — remembers things you say without being asked
- **Device control** — open apps, tap buttons, type text, scroll, read the screen (via AccessibilityService)
- **Screen vision** — captures the screen and describes what is visually there (Android 11+)
- **Autonomous agent** — give it a goal, it plans, executes, and reports
- **Agent history** — last 30 runs saved locally
- **Reminders** — schedule, list, cancel via chat or UI
- **Search, regenerate, export** — conversation management built in
- **Stacked providers** — primary + fallback for uninterrupted chat
- **Zero setup** — first launch asks for a provider; free options included

---

## Getting started

### Install

1. Download the latest `app-debug.apk` from [Releases](https://github.com/bigfguy8/ind-ai/releases/latest)
2. Enable "Install unknown apps" for your file manager
3. Tap the APK → Install → Open

### First launch

Ind AI shows a welcome dialog. Pick one of the free providers:

| Provider | Free tier | Requires key |
|---|---|---|
| **Pollinations** | Unlimited (fair use) | No |
| **KeylessAI** | Unlimited (fair use) | No |
| **Groq** | 14,400 requests/day | Yes (free) |
| **Cerebras** | 14,400 requests/day | Yes (free) |
| **Google Gemini** | 1,500 requests/day | Yes (free) |
| **Mistral** | ~1B tokens/month | Yes (free) |
| **OpenRouter** | 50 requests/day | Yes (free) |
| **GitHub Models** | Free with GitHub account | Yes (PAT) |
| **Cloudflare Workers AI** | 10,000 neurons/day | Yes (free) |

Full setup list: pick **Primary**, **Fallback**, and **Vision** providers from the Settings dialog (long-press the app title).

### Recommended stack

- **Primary:** Groq (fast, 14,400/day)
- **Fallback:** Cerebras (fires automatically on 429)
- **Vision:** Google Gemini (image-capable)

Total ~1.3 billion tokens per day across the three.

---

## Permissions

| Permission | Why |
|---|---|
| **INTERNET** | API calls to your chosen provider |
| **RECORD_AUDIO** | Voice input |
| **POST_NOTIFICATIONS** | Reminders |
| **Accessibility Service** | Device control — tapping, typing, scrolling, reading screen |

Ind AI never reads password fields. Android blocks accessibility from capturing secure inputs, and Ind AI does not attempt to circumvent that.

---

## Architecture

| Layer | Files |
|---|---|
| Chat | `MainActivity`, `AiClient`, `ChatStore`, `MarkdownRenderer` |
| Memory | `MemoryStore`, `VectorStore`, `EmbeddingClient`, `FactExtractor` |
| Voice | `VoiceIO` |
| Device control | `NovaAccessibilityService`, `Tools`, `ToolParser`, `ToolExecutor` |
| Agent | `Plan`, `Planner`, `Executor`, `AgentSession`, `AgentRenderer`, `AgentActivity` |
| Vision | `VisionClient` |
| Reminders | `ReminderScheduler`, `ReminderReceiver`, `RemindersDialog` |
| UI | `Theme`, `Drawables`, `BottomNav` |
| Permissions | `PermissionCenter`, `CapabilitiesActivity` |
| Setup | `FirstRunSetup`, `ProviderPicker`, `Providers` |

Plain Java, no external libraries beyond `org.json` (built into Android).

---

## Building from source

### Prerequisites

- JDK 17
- Android SDK with platform 34 and build-tools 34.0.0
- Gradle 8.6+ (or the bundled wrapper)

### On Termux / ARM64

Gradle downloads an x86_64 `aapt2` that cannot run on ARM. Install Termux's native `aapt2` and add to `~/.gradle/gradle.properties`:

    android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2

### Build

    cp gradle.properties.example gradle.properties
    # edit gradle.properties with your API key and endpoint (optional — you can also set them in-app)
    ./gradlew assembleDebug

APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Security

- Never commit `gradle.properties` — it holds your API key.
- Never share a built APK that contains a baked-in API key.
- If a key leaks, revoke it at the provider immediately.
- A pre-commit hook blocks `.bak`, `.env`, `.jks`, and `gradle.properties` from being committed.

## License

MIT — see `LICENSE`.
