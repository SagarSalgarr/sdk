# Model Pipeline — Simply Save Voice SDK

This directory contains scripts and conventions for preparing models used by the native AAR. Models are **pluggable**: change STT/translation/TTS by editing `config.py` and `model-registry.json` (no need to change the rest of the architecture).

| Group        | Models                          | Source / Notes                    |
|-------------|----------------------------------|------------------------------------|
| **STT/ASR** | Whisper tiny                     | HuggingFace `openai/whisper-tiny`   |
| **Translation** | IndicTrans2 distilled 200M   | HuggingFace ai4bharat (EN↔Indic)   |
| **TTS**     | Meta MMS-TTS (per language)      | HuggingFace `facebook/mms-tts-{code}`; on-demand per language |

## Environment

- **Python**: 3.10+
- **Hardware**: 32GB RAM, NVIDIA GPU 16GB VRAM recommended (do not run on developer laptop or CI for full training)
- **HuggingFace**: Set `HF_TOKEN` in the environment for gated models and downloads. Never hardcode tokens.

```bash
export HF_TOKEN=your_token_here
python -m venv .venv
source .venv/bin/activate   # or .venv\Scripts\activate on Windows
pip install -r requirements.txt
```

## Directory convention (Ch 6.2)

```
model-pipeline/
  models/
    {model-name}/{version}/
      raw/         # Original downloaded weights (read-only after download)
      onnx/        # Exported ONNX before quantization
      quantized/   # Final INT8 quantized model (all three groups ≤ 450 MB)
      validation/  # Quality metrics (e.g. metrics.json)
      packaging/   # Encrypted files for Play Console upload
  scripts/
    download_three_models.py   # Download Whisper + IndicTrans2 + Meta MMS-TTS (per-language) from HuggingFace
    quantize_three_models.py   # INT8 quantize STT/Translation; copy MMS-TTS per language to quantized/
    extract_intent_dataset.py
    train_intent_classifier.py
    export_intent_tflite.py
  config.py        # BUDGET_MB=450, paths, HF repo IDs
  requirements.txt
```

## Three-model flow (450 MB total)

1. **Download** (uses `HF_TOKEN`):
   ```bash
   python scripts/download_three_models.py
   ```
   - Pulls **Whisper tiny** and **IndicTrans2 distilled 200M** (both directions) from HuggingFace into `models/*/1.0/raw/`.
   - **TTS (Meta MMS-TTS)**: One model per language (e.g. `facebook/mms-tts-hin`, `facebook/mms-tts-ben`). Config: `config.MMS_TTS_LANGS`. Use `--tts` to download all, or `--tts-lang mms_tts_hin` for specific languages.

2. **Quantize** (STT + Translation → ONNX INT8; TTS → copy MMS-TTS files per language to quantized/):
   ```bash
   python scripts/quantize_three_models.py
   ```
   - **Whisper**: encoder + decoder ONNX, INT8 dynamic.
   - **IndicTrans2 distilled 200M**: EN→Indic and Indic→EN to ONNX INT8 (or INT4 with `--quant-int4`).
   - **TTS (MMS-TTS)**: Copies `config.json`, `model.safetensors`, `vocab.json`, etc. per language from raw to quantized (no ONNX). Use `--tts-lang mms_tts_hin` to build only specific languages.

3. **Packaging**: Use your encryption script (AES-256-GCM, manifest.json) to produce Play asset packs. Intent classifier TFLite goes in AAR `assets/`; the three-model pack is the install-time base.

## Intent classifier (MobileBERT)

Separate from the 450 MB pack; output goes into AAR assets.

1. **Extract dataset from Excel** (Voice Interaction sheet; exclude Respond rows):
   ```bash
   python scripts/extract_intent_dataset.py "path/to/Simply Save - Transcript, Shared (1).xlsx" -o models/intent_classifier/dataset/train.csv
   ```

2. **Train** (768→256→9 head, inverse frequency weights, 80/10/10, validation gates):
   ```bash
   python scripts/train_intent_classifier.py --dataset models/intent_classifier/dataset/train.csv --output-dir models/intent_classifier/checkpoint
   ```

3. **Export to TFLite** (INT8 PTQ, representative data; validate ≥ 88%):
   ```bash
   python scripts/export_intent_tflite.py
   ```
   Copy the resulting `.tflite` to `android/src/main/assets/`.

## Adding new intents

- Add the intent to `INTENT_LABELS` in `scripts/train_intent_classifier.py` and to the Kotlin `Intent` enum in the AAR.
- Add Flow→Intent mapping in `scripts/extract_intent_dataset.py` if the Excel uses new flow names.
- Add slot schema and response templates in `android/src/main/assets/`.
- Retrain, re-export TFLite, replace the file in AAR assets and bump version.

## Adding or changing TTS languages (pluggable)

- **Meta MMS-TTS**: Add a row to `config.MMS_TTS_LANGS`: `(hf_code, logical_name)` e.g. `("hin", "mms_tts_hin")`. Add a matching entry in `android/.../assets/model-registry.json` with `logicalName`, `modelType: "TTS_END_TO_END"`, `fileNames` (see `MMS_TTS_FILES`), and `supportedLanguages: ["hi"]`. No code changes in delivery or pipeline; the registry and config drive which models are used.
- **Swapping TTS (e.g. another vendor)**: Add new entries to the registry and config; point `fileNames` and HF repo (or URL) to the new model. The SDK loads by logical name and type; no architecture change needed.
