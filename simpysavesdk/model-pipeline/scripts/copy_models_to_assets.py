#!/usr/bin/env python3
"""
Copy model pipeline output into AAR assets for the Simply Save Voice SDK.
Run from repo root after training/quantizing the intent classifier.

Usage:
  python model-pipeline/scripts/copy_models_to_assets.py

Expects:
  - models/intent_classifier/quantized/intent_classifier.tflite
Copies to:
  - android/src/main/assets/intent_classifier_int8.tflite

Optional: set INTENT_TFLITE_PATH to a different source path.
"""

import os
import shutil
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_INTENT = os.path.join(REPO_ROOT, "model-pipeline", "models", "intent_classifier", "quantized", "intent_classifier.tflite")
ASSETS_DIR = os.path.join(REPO_ROOT, "android", "src", "main", "assets")
TARGET_INTENT = "intent_classifier_int8.tflite"

def main():
    intent_src = os.environ.get("INTENT_TFLITE_PATH", DEFAULT_INTENT)
    if not os.path.isfile(intent_src):
        print("Intent classifier TFLite not found:", intent_src, file=sys.stderr)
        print("Run: python model-pipeline/scripts/train_intent_classifier.py then export_intent_tflite.py", file=sys.stderr)
        sys.exit(1)
    os.makedirs(ASSETS_DIR, exist_ok=True)
    dst = os.path.join(ASSETS_DIR, TARGET_INTENT)
    shutil.copy2(intent_src, dst)
    print("Copied", intent_src, "->", dst)

if __name__ == "__main__":
    main()
