#!/usr/bin/env python3
"""
Prepare quantized models for hosting so the SDK can download them on first use.
Uses Whisper tiny, IndicTrans2 distilled 200M, and Meta MMS-TTS (per-language) from quantized/ dirs.

Layout: release/{logicalName}/{fileName}
e.g. release/whisper_tiny_v1/whisper_encoder_int8.onnx, release/mms_tts_hin/config.json

Upload the contents of release/ to your server and set SDK config modelDownloadBaseUrl
to that base URL (one URL for all REMOTE_DOWNLOAD models). TTS models are on-demand per language.

Usage:
  python scripts/prepare_release.py [--output-dir release]
"""

import argparse
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import (
    WHISPER_TINY_QUANTIZED,
    WHISPER_TINY_RAW,
    INDICTRANS2_EN_INDIC_QUANTIZED,
    INDICTRANS2_INDIC_EN_QUANTIZED,
    INDICTRANS2_EN_INDIC_RAW,
    INDICTRANS2_INDIC_EN_RAW,
    MMS_TTS_LANGS,
    MMS_TTS_FILES,
    mms_tts_quantized_dir,
)


def _translation_file_pairs(quant_dir: str, raw_dir: str) -> list:
    """ONNX copies plus dict files from raw. Returns list of (src_path, dst_name)."""
    pairs = []
    if os.path.isfile(os.path.join(quant_dir, "model_int4.onnx")):
        pairs = [(os.path.join(quant_dir, "model_int4.onnx"), "encoder_int8.onnx"), (os.path.join(quant_dir, "model_int4.onnx"), "decoder_int8.onnx")]
    else:
        pairs = [(os.path.join(quant_dir, "model_int8.onnx"), "encoder_int8.onnx"), (os.path.join(quant_dir, "model_int8.onnx"), "decoder_int8.onnx")]
    for name in ["dict.SRC.json", "dict.TGT.json"]:
        src = os.path.join(raw_dir, name)
        if os.path.isfile(src):
            pairs.append((src, name))
    return pairs


def _whisper_file_pairs(quant_dir: str, raw_dir: str) -> list:
    """Encoder, decoder from quantized; vocab from raw. Returns list of (src_path, dst_name)."""
    pairs = [
        (os.path.join(quant_dir, "whisper_encoder_int8.onnx"), "whisper_encoder_int8.onnx"),
        (os.path.join(quant_dir, "whisper_decoder_int8.onnx"), "whisper_decoder_int8.onnx"),
    ]
    for candidate in ["vocab.json", "tokenizer.json"]:
        src = os.path.join(raw_dir, candidate)
        if os.path.isfile(src):
            pairs.append((src, "vocab.json"))
            break
    return pairs


def _release_entries():
    """Build release map: fixed STT/translation + dynamic MMS-TTS per language from config."""
    entries = [
        ("whisper_tiny_v1", WHISPER_TINY_QUANTIZED(), ("whisper", WHISPER_TINY_RAW())),
        ("indictrans2_en_indic_v1", INDICTRANS2_EN_INDIC_QUANTIZED(), ("translation", INDICTRANS2_EN_INDIC_RAW())),
        ("indictrans2_indic_en_v1", INDICTRANS2_INDIC_EN_QUANTIZED(), ("translation", INDICTRANS2_INDIC_EN_RAW())),
    ]
    for _hf_code, logical_name in MMS_TTS_LANGS:
        quant_dir = mms_tts_quantized_dir(logical_name)
        entries.append((logical_name, quant_dir, ("tts", [(f, f) for f in MMS_TTS_FILES])))
    return entries


def main():
    ap = argparse.ArgumentParser(description="Prepare release bundle for model download hosting")
    ap.add_argument("--output-dir", default="release", help="Output directory (default: release)")
    args = ap.parse_args()

    out_root = os.path.join(os.path.dirname(__file__), "..", args.output_dir)
    os.makedirs(out_root, exist_ok=True)

    for entry in _release_entries():
        logical_name = entry[0]
        quant_dir = entry[1]
        spec = entry[2]
        if spec[0] == "whisper":
            file_pairs = _whisper_file_pairs(quant_dir, spec[1])
        elif spec[0] == "translation":
            file_pairs = _translation_file_pairs(quant_dir, spec[1])
        else:
            # TTS: spec[1] is list of (src_name, dst_name) e.g. [("config.json", "config.json"), ...]
            file_pairs = [(os.path.join(quant_dir, src), dst) for src, dst in spec[1]]
        if not os.path.isdir(quant_dir):
            print(f"Skip {logical_name}: {quant_dir} not found")
            continue
        model_dir = os.path.join(out_root, logical_name)
        os.makedirs(model_dir, exist_ok=True)
        for src_path, dst_name in file_pairs:
            dst = os.path.join(model_dir, dst_name)
            if os.path.isfile(src_path):
                shutil.copy2(src_path, dst)
                print(f"  {logical_name}/{dst_name}")
            else:
                print(f"  Skip (missing): {logical_name}/{dst_name}")

    print(f"\nRelease bundle: {os.path.abspath(out_root)}")
    print("Upload this folder to your CDN and set downloadBaseUrl (or modelDownloadBaseUrl in SDK config) to the base URL.")


if __name__ == "__main__":
    main()
