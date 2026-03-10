#!/usr/bin/env python3
"""
Export trained intent classifier to TFLite for Android AAR.
Uses ONNX as intermediate: PyTorch -> ONNX -> TensorFlow -> TFLite.
"""

import argparse
import os
import numpy as np
import torch
from transformers import AutoTokenizer, AutoModelForSequenceClassification
from tensorflow import keras
import tensorflow as tf

# Optional: tf2onnx for PyTorch->ONNX if needed; here we use a simpler path:
# Save PyTorch model, then use transformers + tf2onnx or direct TFLite from SavedModel.


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--checkpoint", default="models/intent_classifier/checkpoint",
                    help="Path to trained checkpoint (saved model + tokenizer)")
    ap.add_argument("--output", default="models/intent_classifier/quantized/intent_classifier.tflite",
                    help="Output TFLite path")
    ap.add_argument("--quantize", action="store_true", help="Apply INT8 post-training quantization")
    args = ap.parse_args()

    if not os.path.isdir(args.checkpoint):
        raise SystemExit(f"Checkpoint dir not found: {args.checkpoint}")

    tokenizer = AutoTokenizer.from_pretrained(args.checkpoint)
    model = AutoModelForSequenceClassification.from_pretrained(args.checkpoint)
    model.eval()

    # Dummy input for tracing
    dummy = tokenizer(
        "How much balance do I have",
        return_tensors="pt",
        padding="max_length",
        max_length=128,
        truncation=True,
    )

    # Export to ONNX
    onnx_path = os.path.join(os.path.dirname(args.output), "intent_classifier.onnx")
    os.makedirs(os.path.dirname(os.path.abspath(args.output)) or ".", exist_ok=True)

    torch.onnx.export(
        model,
        (dummy["input_ids"], dummy["attention_mask"]),
        onnx_path,
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch"},
            "attention_mask": {0: "batch"},
            "logits": {0: "batch"},
        },
        opset_version=14,
    )

    # Convert ONNX -> TF SavedModel (simplified: use onnx-tf or tf2onnx in practice)
    # For a minimal pipeline we save the PyTorch model and document TFLite conversion.
    # Full path: onnx -> tf saved_model -> TFLite (see tensorflow docs).

    print("ONNX exported to", onnx_path)
    print("For TFLite: use tf2onnx.convert or onnx-tf then TFLiteConverter.")
    print("Place the final .tflite in android/src/main/assets/ for AAR packaging.")


if __name__ == "__main__":
    main()
