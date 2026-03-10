#!/usr/bin/env python3
"""
Download model groups (STT, Translation, TTS) from HuggingFace.
Uses HF_TOKEN from environment. Puts files under models/{name}/{version}/raw/.

- STT: Whisper tiny (openai/whisper-tiny)
- Translation: IndicTrans2 distilled 200M (EN↔Indic)
- TTS: Meta MMS-TTS per language (facebook/mms-tts-{code}); on-demand per lang.

Cache: if a model group's raw/ dir already has files, that group is skipped.
Use --force to re-download. Add/remove TTS languages in config.MMS_TTS_LANGS.
"""

import argparse
import os
import sys

# Add parent so we can import config
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import (
    get_hf_token,
    WHISPER_TINY_HF_ID,
    WHISPER_TINY_RAW,
    INDICTRANS2_EN_INDIC_HF_ID,
    INDICTRANS2_INDIC_EN_HF_ID,
    INDICTRANS2_EN_INDIC_RAW,
    INDICTRANS2_INDIC_EN_RAW,
    MMS_TTS_HF_PREFIX,
    MMS_TTS_LANGS,
    MMS_TTS_FILES,
    mms_tts_raw_dir,
)


def is_cached(raw_dir: str, required_files: list[str] | None = None, require_any: list[str] | None = None) -> bool:
    """True if raw_dir exists and has content (or contains all required_files / any of require_any if given)."""
    if not os.path.isdir(raw_dir):
        return False
    names = os.listdir(raw_dir)
    if not names:
        return False
    if require_any:
        return any(os.path.isfile(os.path.join(raw_dir, f)) for f in require_any)
    if required_files:
        return all(f in names for f in required_files)
    return True


def download_whisper_tiny(token: str, force: bool = False) -> bool:
    """Returns True if downloaded (or already cached)."""
    out = WHISPER_TINY_RAW()
    if not force and is_cached(out):
        print(f"[cache] Whisper tiny already in {out}; skip (use --force to re-download).")
        return True
    try:
        from huggingface_hub import snapshot_download
    except ImportError:
        raise SystemExit("Install huggingface_hub: pip install huggingface_hub")
    print(f"Downloading {WHISPER_TINY_HF_ID} -> {out}")
    snapshot_download(
        repo_id=WHISPER_TINY_HF_ID,
        local_dir=out,
        token=token,
    )
    print("Whisper tiny done.")
    return True


def download_indictrans2(token: str, force: bool = False) -> bool:
    """Returns True if both directions downloaded (or already cached)."""
    try:
        from huggingface_hub import snapshot_download
    except ImportError:
        raise SystemExit("Install huggingface_hub: pip install huggingface_hub")
    # Require at least one weight file so partial downloads (config/tokenizer only) are re-done
    weight_any = ["model.safetensors", "pytorch_model.bin"]
    downloaded = False
    for repo_id, raw_dir in [
        (INDICTRANS2_EN_INDIC_HF_ID, INDICTRANS2_EN_INDIC_RAW()),
        (INDICTRANS2_INDIC_EN_HF_ID, INDICTRANS2_INDIC_EN_RAW()),
    ]:
        if not force and is_cached(raw_dir, require_any=weight_any):
            print(f"[cache] Already in {raw_dir}; skip (use --force to re-download).")
            continue
        print(f"Downloading {repo_id} -> {raw_dir}")
        snapshot_download(
            repo_id=repo_id,
            local_dir=raw_dir,
            token=token,
        )
        downloaded = True
    if not downloaded and not force:
        print("[cache] IndicTrans2 (both directions) already present.")
    else:
        print("IndicTrans2 (both directions) done.")
    return True


def download_mms_tts(token: str, force: bool = False, lang_filter: list[str] | None = None) -> bool:
    """Download Meta MMS-TTS per-language models (facebook/mms-tts-{code}). Only downloads languages in MMS_TTS_LANGS (or lang_filter if set)."""
    try:
        from huggingface_hub import snapshot_download
    except ImportError:
        raise SystemExit("Install huggingface_hub: pip install huggingface_hub")
    langs = [x for x in MMS_TTS_LANGS if lang_filter is None or x[1] in lang_filter]
    for hf_code, logical_name in langs:
        out = mms_tts_raw_dir(logical_name)
        if not force and is_cached(out, require_any=["model.safetensors", "pytorch_model.bin"]):
            print(f"[cache] MMS-TTS {logical_name} already in {out}; skip (use --force to re-download).")
            continue
        repo_id = f"{MMS_TTS_HF_PREFIX}{hf_code}"
        print(f"Downloading {repo_id} -> {out}")
        snapshot_download(repo_id=repo_id, local_dir=out, token=token)
        print(f"MMS-TTS {logical_name} done.")
    return True


def main():
    ap = argparse.ArgumentParser(
        description="Download Whisper, IndicTrans2, and Meta MMS-TTS (per-language) from HuggingFace. Cached raw/ dirs are skipped; use --force to re-download."
    )
    ap.add_argument("--stt", action="store_true", default=True, help="Download Whisper tiny")
    ap.add_argument("--translation", action="store_true", default=True, help="Download IndicTrans2 distilled 200M")
    ap.add_argument("--tts", action="store_true", default=True, help="Download Meta MMS-TTS (all languages in config)")
    ap.add_argument("--skip-tts-download", action="store_true", help="Do not download TTS")
    ap.add_argument("--tts-lang", action="append", dest="tts_langs", metavar="LOGICAL_NAME", help="Download only these TTS models (e.g. mms_tts_hin). Can repeat.")
    ap.add_argument("--force", action="store_true", help="Re-download even if raw/ cache exists")
    args = ap.parse_args()

    token = get_hf_token()
    force = args.force

    if args.stt:
        download_whisper_tiny(token, force=force)
    if args.translation:
        download_indictrans2(token, force=force)
    if args.tts and not args.skip_tts_download:
        download_mms_tts(token, force=force, lang_filter=args.tts_langs)

    print("Downloads done. Next: run quantize_three_models.py (INT8) and check total <= 450 MB.")


if __name__ == "__main__":
    main()
