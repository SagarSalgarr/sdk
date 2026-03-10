#!/usr/bin/env python3
"""
Train intent classifier (MobileBERT-based) on extracted dataset.
Output: PyTorch model + config, then use export_tflite.py to get TFLite for AAR.
Uses HF_TOKEN from environment for HuggingFace downloads (no hardcoding).
"""

import argparse
import os
import json
import numpy as np
import pandas as pd
import torch
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, f1_score
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
    TrainingArguments,
    Trainer,
    EarlyStoppingCallback,
)
from datasets import Dataset

INTENT_LABELS = [
    "CREATE_GULLAK_SIP",
    "INVEST_LUMPSUM",
    "CHECK_BALANCE",
    "NOMINEE",
    "NOTIFICATION",
    "ECS_MANDATE",
    "BRIGHT_BHAVISHYA",
    "FALLBACK",
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dataset", default="models/intent_classifier/dataset/train.csv",
                    help="CSV with utterance_english, intent")
    ap.add_argument("--model-name", default="google/mobilebert-uncased",
                    help="Base HuggingFace model")
    ap.add_argument("--output-dir", default="models/intent_classifier/checkpoint",
                    help="Checkpoint output directory")
    ap.add_argument("--val-ratio", type=float, default=0.1)
    ap.add_argument("--epochs", type=int, default=10)
    ap.add_argument("--batch-size", type=int, default=32)
    ap.add_argument("--lr", type=float, default=2e-5)
    ap.add_argument("--patience", type=int, default=3)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    if not os.path.isfile(args.dataset):
        raise SystemExit(f"Dataset not found: {args.dataset}. Run extract_intent_dataset.py first.")

    label2id = {l: i for i, l in enumerate(INTENT_LABELS)}
    id2label = {i: l for l, i in label2id.items()}
    num_labels = len(INTENT_LABELS)

    df = pd.read_csv(args.dataset)
    df = df[df["intent"].isin(INTENT_LABELS)]
    df["label"] = df["intent"].map(label2id)
    texts = df["utterance_english"].astype(str).tolist()
    labels = df["label"].tolist()

    X_train, X_val, y_train, y_val = train_test_split(
        texts, labels, test_size=args.val_ratio, stratify=labels, random_state=args.seed
    )

    tokenizer = AutoTokenizer.from_pretrained(args.model_name)
    model = AutoModelForSequenceClassification.from_pretrained(
        args.model_name,
        num_labels=num_labels,
        id2label=id2label,
        label2id=label2id,
    )

    def tokenize(examples):
        return tokenizer(
            examples["text"],
            truncation=True,
            padding="max_length",
            max_length=128,
            return_tensors=None,
        )

    train_ds = Dataset.from_dict({"text": X_train, "label": y_train})
    val_ds = Dataset.from_dict({"text": X_val, "label": y_val})
    train_ds = train_ds.map(lambda x: tokenize({"text": x["text"]}), batched=True, remove_columns=["text"])
    val_ds = val_ds.map(lambda x: tokenize({"text": x["text"]}), batched=True, remove_columns=["text"])

    training_args = TrainingArguments(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        learning_rate=args.lr,
        weight_decay=0.01,
        evaluation_strategy="epoch",
        save_strategy="epoch",
        load_best_model_at_end=True,
        metric_for_best_model="f1",
        greater_is_better=True,
        logging_steps=50,
        save_total_limit=2,
        seed=args.seed,
    )

    def compute_metrics(eval_pred):
        logits, labels = eval_pred
        preds = np.argmax(logits, axis=-1)
        f1 = f1_score(labels, preds, average="weighted")
        return {"f1": f1}

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_ds,
        eval_dataset=val_ds,
        compute_metrics=compute_metrics,
        callbacks=[EarlyStoppingCallback(early_stopping_patience=args.patience)],
    )

    trainer.train()
    eval_out = trainer.evaluate()
    preds = trainer.predict(val_ds)
    report = classification_report(
        y_val,
        np.argmax(preds.predictions, axis=-1),
        target_names=INTENT_LABELS,
        output_dict=True,
    )

    os.makedirs(args.output_dir, exist_ok=True)
    with open(os.path.join(args.output_dir, "metrics.json"), "w") as f:
        json.dump({"eval_f1": eval_out.get("eval_f1"), "report": report}, f, indent=2)

    trainer.save_model(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print(f"Model saved to {args.output_dir}")
    print("F1:", eval_out.get("eval_f1"))


if __name__ == "__main__":
    main()
