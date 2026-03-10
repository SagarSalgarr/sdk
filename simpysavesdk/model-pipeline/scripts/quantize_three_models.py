#!/usr/bin/env python3
"""
Quantize model groups: STT and Translation to ONNX INT8; TTS (Meta MMS-TTS per-language) copied as-is.

- STT: Whisper tiny -> whisper_encoder_int8.onnx, whisper_decoder_int8.onnx (via Optimum + INT8)
- Translation: IndicTrans2 distilled 200M -> ONNX INT8 (optionally INT4)
- TTS: Meta MMS-TTS -> copy config.json, model.safetensors, etc. per language (no ONNX)

Reads from models/{name}/{version}/raw/, writes to quantized/. TTS is on-demand per language; add/remove languages in config.MMS_TTS_LANGS.
"""

import argparse
import os
import shutil
import subprocess
import sys
import warnings

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import (
    BUDGET_MB,
    WHISPER_TINY_RAW,
    WHISPER_TINY_ONNX,
    WHISPER_TINY_QUANTIZED,
    INDICTRANS2_EN_INDIC_RAW,
    INDICTRANS2_INDIC_EN_RAW,
    INDICTRANS2_EN_INDIC_ONNX,
    INDICTRANS2_INDIC_EN_ONNX,
    INDICTRANS2_EN_INDIC_QUANTIZED,
    INDICTRANS2_INDIC_EN_QUANTIZED,
    MMS_TTS_LANGS,
    MMS_TTS_FILES,
    mms_tts_raw_dir,
    mms_tts_quantized_dir,
)


def dir_size_mb(path: str) -> float:
    if not os.path.isdir(path):
        return 0.0
    total = 0
    for dirpath, _, filenames in os.walk(path):
        for f in filenames:
            total += os.path.getsize(os.path.join(dirpath, f))
    return total / (1024 * 1024)


def _export_whisper_via_optimum(raw: str, out_onnx: str) -> bool:
    """Export Whisper to ONNX using Optimum (avoids torch.onnx tracing issues). Returns True on success."""
    os.makedirs(out_onnx, exist_ok=True)
    try:
        from optimum.exporters.onnx import main_export
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            main_export(
                raw,
                output=out_onnx,
                task="automatic-speech-recognition",
                no_post_process=True,
                do_validation=False,
            )
    except Exception as e:
        print(f"Optimum export failed: {e}")
        # Fallback: try CLI (optimum-cli export onnx --model <raw> <out> --no-post-process)
        try:
            r = subprocess.run(
                [sys.executable, "-m", "optimum.exporters.onnx", "--model", raw, out_onnx, "--task", "automatic-speech-recognition", "--no-post-process"],
                capture_output=True,
                text=True,
                timeout=600,
            )
            if r.returncode != 0:
                print(r.stderr or r.stdout or "optimum-cli failed")
                return False
        except Exception as e2:
            print(f"Optimum CLI fallback failed: {e2}")
            return False
        return True
    return True


def quantize_whisper_tiny() -> float:
    """Export Whisper tiny to ONNX via Optimum, then INT8 dynamic quantization. Returns size in MB."""
    try:
        raw = WHISPER_TINY_RAW()
        if not os.path.isdir(raw) or not os.listdir(raw):
            print("Whisper tiny raw/ missing or empty; run download_three_models.py --stt first.")
            return 0.0

        out_onnx = WHISPER_TINY_ONNX()
        out_quant = WHISPER_TINY_QUANTIZED()
        os.makedirs(out_quant, exist_ok=True)

        # 1) Export to ONNX using Optimum (robust with current transformers/PyTorch)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", DeprecationWarning)
            warnings.simplefilter("ignore", UserWarning)
            if not _export_whisper_via_optimum(raw, out_onnx):
                return 0.0

        # Optimum may output encoder_model.onnx/decoder_model.onnx or encoder.onnx/decoder.onnx
        if not os.path.isdir(out_onnx):
            return 0.0
        onnx_files = [f for f in os.listdir(out_onnx) if f.endswith(".onnx")]
        encoder_src = None
        decoder_src = None
        for f in onnx_files:
            if "encoder" in f.lower() and "decoder" not in f.lower():
                encoder_src = os.path.join(out_onnx, f)
                break
        for f in onnx_files:
            if "decoder" in f.lower():
                decoder_src = os.path.join(out_onnx, f)
                break
        if not encoder_src or not decoder_src or not os.path.isfile(encoder_src) or not os.path.isfile(decoder_src):
            print("Whisper ONNX export did not produce encoder/decoder files. Files in", out_onnx, ":", onnx_files)
            return 0.0

        # 2) INT8 dynamic quantization
        try:
            from onnxruntime.quantization import quantize_dynamic, QuantType
        except ImportError:
            print("onnxruntime quantization not available; copying ONNX as-is.")
            for name, src in [("encoder", encoder_src), ("decoder", decoder_src)]:
                if os.path.isfile(src):
                    shutil.copy(src, os.path.join(out_quant, f"whisper_{name}_int8.onnx"))
            return dir_size_mb(out_quant)

        for name, src in [("encoder", encoder_src), ("decoder", decoder_src)]:
            dst = os.path.join(out_quant, f"whisper_{name}_int8.onnx")
            quantize_dynamic(src, dst, weight_type=QuantType.QInt8, per_channel=True)

        return dir_size_mb(out_quant)
    except Exception as e:
        print(f"Whisper quantize error: {e}")
        import traceback
        traceback.print_exc()
        return 0.0


def _quantize_translation_onnx(onnx_path: str, q_path: str, use_int4: bool) -> bool:
    """Quantize translation ONNX to INT4 (smaller) or INT8. Returns True if successful."""
    if use_int4:
        try:
            from pathlib import Path
            from onnxruntime.quantization import matmul_4bits_quantizer, quant_utils
            model = quant_utils.load_model_with_shape_infer(Path(onnx_path))
            quant_config = matmul_4bits_quantizer.DefaultWeightOnlyQuantConfig(
                block_size=128,
                is_symmetric=True,
                accuracy_level=4,
                quant_format=quant_utils.QuantFormat.QOperator,
                op_types_to_quantize=("MatMul",),
                quant_axes=(("MatMul", 0),),
            )
            quant = matmul_4bits_quantizer.MatMul4BitsQuantizer(model, algo_config=quant_config)
            quant.process()
            quant.model.save_model_to_file(q_path, use_external_data=True)
            return True
        except Exception as e:
            print(f"INT4 quantization failed ({e}), falling back to INT8")
            q_path = q_path.replace("model_int4.onnx", "model_int8.onnx")
    from onnxruntime.quantization import quantize_dynamic, QuantType
    quantize_dynamic(onnx_path, q_path, weight_type=QuantType.QInt8, per_channel=True)
    return True


def quantize_indictrans2(direction_filter: str | None = None, use_int4: bool = False) -> float:
    """IndicTrans2 distilled 200M: export to ONNX (in onnx_dir) then INT8 quantize into quant_dir only.
    direction_filter: "en_indic", "indic_en", or None/"both" for both. Use one direction to save ~245 MB."""
    total = 0.0
    directions = [
        ("en_indic", INDICTRANS2_EN_INDIC_RAW(), INDICTRANS2_EN_INDIC_ONNX(), INDICTRANS2_EN_INDIC_QUANTIZED()),
        ("indic_en", INDICTRANS2_INDIC_EN_RAW(), INDICTRANS2_INDIC_EN_ONNX(), INDICTRANS2_INDIC_EN_QUANTIZED()),
    ]
    if direction_filter and direction_filter != "both":
        directions = [(d, r, o, q) for d, r, o, q in directions if d == direction_filter]
    for direction, raw_dir, onnx_dir, quant_dir in directions:
        if not os.path.isdir(raw_dir) or not os.listdir(raw_dir):
            print(f"IndicTrans2 {direction} raw/ missing; run download_three_models.py --translation.")
            continue
        # Ensure we use 200M: raw dir should have ~400MB weight file, not ~4GB (1B)
        raw_config = os.path.join(raw_dir, "config.json")
        if os.path.isfile(raw_config):
            import json
            with open(raw_config) as f:
                cfg = json.load(f)
            name = cfg.get("_name_or_path") or cfg.get("name_or_path") or ""
            if "1B" in name or "1.1B" in name:
                print(f"IndicTrans2 {direction}: raw/ has 1B model ({name}). Run: python scripts/download_three_models.py --translation --force")
                continue
        try:
            from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
            import torch
        except ImportError as e:
            print("IndicTrans2 quantize skip:", e)
            continue
        model = AutoModelForSeq2SeqLM.from_pretrained(raw_dir, trust_remote_code=True)
        model.eval()
        tokenizer = AutoTokenizer.from_pretrained(raw_dir, trust_remote_code=True)
        os.makedirs(onnx_dir, exist_ok=True)
        # Export to onnx_dir so FP32 external data stays there; only INT8 goes to quant_dir
        onnx_path = os.path.join(onnx_dir, "model.onnx")
        # IndicTrans2 tokenizer expects "src_lang tgt_lang text" with LANGUAGE_TAGS (e.g. eng_Latn, hin_Deva)
        dummy_text = "eng_Latn hin_Deva hello" if direction == "en_indic" else "hin_Deva eng_Latn hello"
        dummy = tokenizer(dummy_text, return_tensors="pt")
        dec_start = getattr(model.config, "decoder_start_token_id", None) or model.config.pad_token_id
        if dec_start is None:
            dec_start = 0
        decoder_input_ids = torch.full(
            (dummy["input_ids"].shape[0], 1), dec_start, dtype=torch.long
        )
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", DeprecationWarning)
            warnings.simplefilter("ignore", UserWarning)
            try:
                warnings.simplefilter("ignore", torch.jit.TracerWarning)
            except AttributeError:
                pass
            torch.onnx.export(
                model,
                (dummy["input_ids"], dummy["attention_mask"], decoder_input_ids),
                onnx_path,
                input_names=["input_ids", "attention_mask", "decoder_input_ids"],
                output_names=["logits"],
                dynamic_axes={
                    "input_ids": {0: "b", 1: "s"},
                    "attention_mask": {0: "b", 1: "s"},
                    "decoder_input_ids": {0: "b", 1: "d"},
                    "logits": {0: "b"},
                },
                opset_version=17,
                dynamo=False,
            )
        os.makedirs(quant_dir, exist_ok=True)
        # Remove any leftover files from a previous run (e.g. old export external data)
        for f in os.listdir(quant_dir):
            try:
                os.remove(os.path.join(quant_dir, f))
            except Exception:
                pass
        out_name = "model_int4.onnx" if use_int4 else "model_int8.onnx"
        q_path = os.path.join(quant_dir, out_name)
        try:
            _quantize_translation_onnx(onnx_path, q_path, use_int4=use_int4)
        except Exception as e:
            print(f"IndicTrans2 quantization skip: {e}")
        # Remove FP32 ONNX and any external data from onnx_dir to save disk
        for f in os.listdir(onnx_dir):
            try:
                os.remove(os.path.join(onnx_dir, f))
            except Exception:
                pass
        total += dir_size_mb(quant_dir)
    return total


def quantize_mms_tts(lang_filter: list[str] | None = None) -> float:
    """Meta MMS-TTS: copy raw files to quantized per language (no ONNX). Returns total size in MB."""
    total_mb = 0.0
    langs = [x for x in MMS_TTS_LANGS if lang_filter is None or x[1] in lang_filter]
    for _hf_code, logical_name in langs:
        raw = mms_tts_raw_dir(logical_name)
        quant = mms_tts_quantized_dir(logical_name)
        if not os.path.isdir(raw):
            print(f"  Skip {logical_name}: raw/ missing; run download_three_models.py --tts [--tts-lang {logical_name}]")
            continue
        os.makedirs(quant, exist_ok=True)
        for f in MMS_TTS_FILES:
            src = os.path.join(raw, f)
            if os.path.isfile(src):
                shutil.copy2(src, os.path.join(quant, f))
        total_mb += dir_size_mb(quant)
    return total_mb


def main():
    ap = argparse.ArgumentParser(
        description="Quantize Whisper + IndicTrans2 distilled 200M + TTS. Use --no-tts and/or --translation-direction to reduce size."
    )
    ap.add_argument("--stt", action="store_true", default=True)
    ap.add_argument("--translation", action="store_true", default=True)
    ap.add_argument("--translation-direction", choices=("both", "en_indic", "indic_en"), default="both",
                    help="Which translation model(s) to quantize. One direction saves ~245 MB.")
    ap.add_argument("--quant-int4", action="store_true",
                    help="Use INT4 (4-bit) for translation when supported (~half size of INT8). Falls back to INT8 if INT4 fails.")
    ap.add_argument("--tts", action="store_true", default=True, help="Quantize TTS (Meta MMS-TTS per-language)")
    ap.add_argument("--no-tts", action="store_true", dest="skip_tts", help="Skip TTS; use for smaller pack.")
    ap.add_argument("--tts-lang", action="append", dest="tts_langs", metavar="LOGICAL_NAME", help="Only these TTS models (e.g. mms_tts_hin). Can repeat.")
    ap.add_argument("--no-budget-check", action="store_true", help="Do not fail if total > 450 MB")
    args = ap.parse_args()

    total_mb = 0.0
    if args.stt:
        mb = quantize_whisper_tiny()
        total_mb += mb
        print(f"Whisper tiny quantized: {mb:.1f} MB")
    if args.translation:
        mb = quantize_indictrans2(direction_filter=args.translation_direction, use_int4=getattr(args, "quant_int4", False))
        total_mb += mb
        print(f"IndicTrans2 (distilled 200M) quantized: {mb:.1f} MB")
    if args.tts and not args.skip_tts:
        mb = quantize_mms_tts(lang_filter=getattr(args, "tts_langs", None))
        total_mb += mb
        print(f"TTS (MMS-TTS) quantized: {mb:.1f} MB total")

    print(f"Total quantized size: {total_mb:.1f} MB (budget {BUDGET_MB} MB)")
    if not args.no_budget_check and total_mb > BUDGET_MB:
        raise SystemExit(f"Total {total_mb:.1f} MB exceeds budget {BUDGET_MB} MB. Reduce model size or quantization aggressiveness.")
    print("Done. Use packaging script to encrypt and build Play asset packs.")


if __name__ == "__main__":
    main()
