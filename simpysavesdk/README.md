# Simply Save Voice SDK

Voice AI SDK for the Simply Save app by Nippon India Mutual Fund. Enables Indian retail investors to interact with the app in Hindi, Bengali, Gujarati, Marathi, Odia, Assamese, Bhojpuri, and Maithili via voice or text.

## Architecture

- **Layer 1 — Native Android AAR**: All AI models, inference, audio, and security run on-device (Kotlin).
- **Layer 2 — React Native Bridge**: Thin Kotlin module that maps JavaScript calls to the AAR and results back to JS.
- **Layer 3 — TypeScript API**: Public API consumed by the Simply Save React Native app.

### Model call chain (Registry → Lifecycle → Engine)

The AAR matches the spec: **ModelRegistry** (directory) is read from `model-registry.json`; **ModelLifecycleManager** (scheduler) does `ensureLoaded(descriptor)` and eviction; **ModelStorageManager** is the only class that knows file paths and returns decrypted bytes; **pipeline steps** call `registry.getModelFor()` → `lifecycleManager.ensureLoaded()` → `getEngineForType()` → engine inference. No step touches ONNX/TFLite or file paths directly. See `android/.../models/` and `model-registry.json`.

## Installation (APK team)

```bash
npm install @nippon/simplysave-voice-sdk
```

React Native autolinking picks up the native module from `package.json`. Add `RECORD_AUDIO` to `AndroidManifest.xml` if using `startRecording()`.

## Usage

```ts
import SimplySaveSDK, { Language } from '@nippon/simplysave-voice-sdk';

const sdk = SimplySaveSDK.getInstance();
await sdk.initialize(licenseKey, { targetLanguage: Language.HINDI });

// Voice: record then process
await sdk.startRecording(sessionId);
// ... user speaks ...
const result = await sdk.stopRecordingAndProcess(Language.HINDI);

// Or process a WAV file
const result = await sdk.processVoice(audioFilePath, sessionId, Language.HINDI);

// Text
const result = await sdk.processText('balance check karna hai', sessionId, Language.HINDI);

if (result.isComplete) {
  // Navigate using result.intent, show result.responseTextTarget, play result.audioFilePath
}
```

## Build

- **TypeScript**: `npm run build` (output in `lib/`).
- **Android**: The `android/` folder is a single library module. It is built as part of the host React Native app when they build their APK. No standalone AAR build is required for development; the app’s Gradle includes this module via autolinking.

## How to run end-to-end and see results

1. **Download models** — In `model-pipeline/`: set `HF_TOKEN`, run `python scripts/download_three_models.py --stt --translation`.
2. **Quantize** — `python scripts/quantize_three_models.py --stt --translation` (Whisper, IndicTrans2 distilled 200M → ONNX INT8).
3. **Prepare** — `python scripts/prepare_release.py`. Then either put `release/` on a path the app can read (for **lightweight**, pass `modelLocalBasePath`) or host it at **one** base URL (for **heavier**, pass `modelDownloadBaseUrl`). No CDN — one base URL for all hosted models.
4. **Build SDK** — `npm install && npm run build`.
5. **Integrate in RN app** — Install this package, add `RECORD_AUDIO` if using voice, then `sdk.initialize(licenseKey, { targetLanguage, modelLocalBasePath, modelDownloadBaseUrl })` and `sdk.processText(...)`. Light models from path; others from the single URL.
6. **Run app** — `npx react-native run-android`; trigger `processText` (or voice) and check logs/UI for `result.intent`, `result.responseTextTarget`, `result.isComplete`.

Full step-by-step commands: **[RUN_GUIDE.md](RUN_GUIDE.md)**.

## Model pipeline

See [model-pipeline/README.md](model-pipeline/README.md). Use **HF_TOKEN** for HuggingFace. Flow: **download** Whisper / IndicTrans2 (and optionally **Meta MMS-TTS** per language) → **quantize** → **prepare_release** → host at one base URL or path. **TTS** = Meta MMS-TTS, one model per language; the app passes target language (e.g. from speaker/frontend) and only that language’s TTS is downloaded on demand. **Models are pluggable**: change or add models via `model-registry.json` and `model-pipeline/config.py` without changing the rest of the architecture.

## Intents

`CREATE_GULLAK_SIP`, `INVEST_LUMPSUM`, `CHECK_BALANCE`, `NOMINEE`, `NOTIFICATION`, `ECS_MANDATE`, `BRIGHT_BHAVISHYA`, `FALLBACK`.

## License

Proprietary — Nippon India Mutual Fund.
