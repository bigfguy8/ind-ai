# Ind AI

A personal Android AI assistant with voice, memory, device control, and autonomous agent capabilities.

## Features

- Streaming chat with markdown rendering
- Voice input and text-to-speech, continuous hands-free mode
- Persistent semantic memory with auto-extraction
- Device control via AccessibilityService (open apps, tap, type, scroll, read screen)
- Autonomous agent — plans, executes, and reports
- Screen vision (Android 11+)
- Reminders, search, regenerate, export, model switcher

## Requirements

- Android 5.0+ (API 21)
- OpenAI-compatible chat completions endpoint
- Accessibility Service permission for device control features

## Building

### Prerequisites

- JDK 17
- Android SDK with platform 34 and build-tools 34.0.0
- Gradle 8.6+ (or the bundled wrapper)

### On Termux / ARM64

Gradle downloads an x86_64 `aapt2` that cannot run on ARM. Install Termux's native `aapt2` and add to `~/.gradle/gradle.properties`:

    android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2

### Build steps

    cp gradle.properties.example gradle.properties
    # edit gradle.properties with your API key and endpoint
    ./gradlew assembleDebug

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Configuration

Set in `gradle.properties` at build time:

- `NOVA_API_KEY` — your API key
- `NOVA_ENDPOINT` — chat completions URL
- `NOVA_MODEL` — chat model name

At runtime, the Settings dialog lets you change the vision model and system prompt.

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

## Security

- Never commit `gradle.properties` — it holds your API key.
- Never share the built APK — the API key is embedded in it.
- If your key leaks, revoke it at your provider immediately.

## License

MIT — see `LICENSE`.
