# Glass EE2 Live Translation

Offline-first split-pane transcription + translation client targeting Google Glass Enterprise Edition 2 (Android 8.1 / API 27).

## Features

- Foreground service that captures 16 kHz mono audio (`VOICE_RECOGNITION`) continuously and holds a partial wake lock.
- Lightweight energy-based VAD that commits utterances after ≈600 ms of trailing silence.
- Streaming Vosk STT integration with debounced partial updates and final results piped to a translator queue.
- Stubbed offline Apertium translator with pluggable language profiles (EN↔ES, EN↔FR ready; additional pairs scaffolded).
- Split-screen UI optimised for the 640×360 Glass display with status HUD chips for Mic/VAD/STT/Translation.
- Gesture support: tap to pause/resume, swipe forward/back to cycle language presets, swipe down + double tap to exit.
- Model manager utility class for installing/removing model packs from `files/models/` (first-run copy from assets supported).
- Rolling session buffer ready for future export.

## Building

1. Install Android Studio Arctic Fox (AGP 7.0.4) with Android SDK 31 platform tools and API 27 system image.
2. Open the project and let Gradle sync. The module targets `minSdk=27`, `targetSdk=27`, `compileSdk=31`.
3. Provide Vosk models under `app/src/main/assets/models/<model-folder>` **or** push them to the device at
   `/sdcard/Android/data/com.srikanth.glass.livetranslation/files/models/<model-folder>`.
   - Recommended models: `vosk-model-small-en-us-0.15`, `vosk-model-small-es-0.42`, `vosk-model-small-fr-0.22`.
4. Build and deploy to a Glass EE2 device (Android 8.1). Grant microphone permission on first run.

## Runtime Controls

- **Tap**: toggle pause/resume capture (STT kept warm).
- **Swipe forward/back**: cycle language profiles (updates STT model + translator).
- **Swipe down + double tap**: confirm exit and stop the foreground service.

## Adding Models

Use `ModelManager` to inspect installed packs:

```kotlin
val manager = ModelManager(context)
val packs = manager.listInstalledPacks()
```

To ship a model inside the APK, drop it under `app/src/main/assets/models/<model-folder>/…`. The first time a profile is
selected the assets are copied into the app-private `files/models/` directory.

## Known Limitations

- Apertium integration is stubbed with a rule-based placeholder lexicon for the MVP language pairs.
- VAD uses a simplified energy detector until WebRTC VAD JNI bindings are added.
- Large Vosk/Apertium packages are not bundled to keep the repository lightweight—follow the instructions above to supply models.
