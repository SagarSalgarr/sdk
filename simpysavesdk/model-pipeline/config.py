"""
Model pipeline config: pluggable model groups (change models via this config + model-registry.json).
- STT: Whisper tiny
- Translation: IndicTrans2 distilled 200M
- TTS: Meta MMS-TTS per language (on-demand; one model per language)
"""

import os

# Root under model-pipeline/
MODELS_ROOT = os.path.join(os.path.dirname(__file__), "models")

# Total quantized size budget (MB) for base pack (STT + translation; TTS is on-demand per language)
BUDGET_MB = 450

# Directory convention: models/{model_name}/{version}/{raw|onnx|quantized|validation|packaging}
def model_dir(name: str, version: str, stage: str) -> str:
    path = os.path.join(MODELS_ROOT, name, version, stage)
    os.makedirs(path, exist_ok=True)
    return path

# --- STT: Whisper tiny ---
WHISPER_TINY_VERSION = "1.0"
WHISPER_TINY_HF_ID = "openai/whisper-tiny"
WHISPER_TINY_RAW = lambda: model_dir("whisper_tiny", WHISPER_TINY_VERSION, "raw")
WHISPER_TINY_ONNX = lambda: model_dir("whisper_tiny", WHISPER_TINY_VERSION, "onnx")
WHISPER_TINY_QUANTIZED = lambda: model_dir("whisper_tiny", WHISPER_TINY_VERSION, "quantized")

# --- Translation: IndicTrans2 distilled 200M ---
INDICTRANS2_VERSION = "1.0"
INDICTRANS2_EN_INDIC_HF_ID = "ai4bharat/indictrans2-en-indic-dist-200M"
INDICTRANS2_INDIC_EN_HF_ID = "ai4bharat/indictrans2-indic-en-dist-200M"
INDICTRANS2_EN_INDIC_RAW = lambda: model_dir("indictrans2_en_indic", INDICTRANS2_VERSION, "raw")
INDICTRANS2_INDIC_EN_RAW = lambda: model_dir("indictrans2_indic_en", INDICTRANS2_VERSION, "raw")
INDICTRANS2_EN_INDIC_ONNX = lambda: model_dir("indictrans2_en_indic", INDICTRANS2_VERSION, "onnx")
INDICTRANS2_INDIC_EN_ONNX = lambda: model_dir("indictrans2_indic_en", INDICTRANS2_VERSION, "onnx")
INDICTRANS2_EN_INDIC_QUANTIZED = lambda: model_dir("indictrans2_en_indic", INDICTRANS2_VERSION, "quantized")
INDICTRANS2_INDIC_EN_QUANTIZED = lambda: model_dir("indictrans2_indic_en", INDICTRANS2_VERSION, "quantized")

# --- TTS: Meta MMS-TTS (per-language; on-demand). HF repo = facebook/mms-tts-{code} ---
TTS_VERSION = "1.0"
MMS_TTS_HF_PREFIX = "facebook/mms-tts-"
# (HF language code, logical name in registry) — add/remove languages here to change TTS set
MMS_TTS_LANGS = [
    ("hin", "mms_tts_hin"),
    ("ben", "mms_tts_ben"),
    ("guj", "mms_tts_guj"),
    ("mar", "mms_tts_mar"),
    ("ori", "mms_tts_ori"),
    ("asm", "mms_tts_asm"),
    ("eng", "mms_tts_eng"),
]
MMS_TTS_FILES = ["config.json", "model.safetensors", "vocab.json", "tokenizer_config.json", "special_tokens_map.json"]


def mms_tts_raw_dir(logical_name: str) -> str:
    return model_dir(logical_name, TTS_VERSION, "raw")


def mms_tts_quantized_dir(logical_name: str) -> str:
    return model_dir(logical_name, TTS_VERSION, "quantized")


def get_hf_token() -> str:
    token = os.environ.get("HF_TOKEN", "").strip()
    if not token:
        raise RuntimeError("HF_TOKEN environment variable is required for HuggingFace downloads. Do not hardcode.")
    return token
