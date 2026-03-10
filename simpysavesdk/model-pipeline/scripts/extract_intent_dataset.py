#!/usr/bin/env python3
"""
Extract intent-labeled utterances from Simply Save transcript Excel.
Output: CSV with columns utterance_english, utterance_native, intent, flow
Maps Flow column to one of: CREATE_GULLAK_SIP, INVEST_LUMPSUM, CHECK_BALANCE,
NOMINEE, NOTIFICATION, ECS_MANDATE, BRIGHT_BHAVISHYA, FALLBACK.
"""

import argparse
import os
import pandas as pd

INTENTS = [
    "CREATE_GULLAK_SIP",
    "INVEST_LUMPSUM",
    "CHECK_BALANCE",
    "NOMINEE",
    "NOTIFICATION",
    "ECS_MANDATE",
    "BRIGHT_BHAVISHYA",
    "FALLBACK",
]

# Map possible Flow values from Excel to intent enum
FLOW_TO_INTENT = {
    "create gullak sip": "CREATE_GULLAK_SIP",
    "gullak sip": "CREATE_GULLAK_SIP",
    "invest lumpsum": "INVEST_LUMPSUM",
    "lumpsum": "INVEST_LUMPSUM",
    "check balance": "CHECK_BALANCE",
    "balance": "CHECK_BALANCE",
    "nominee": "NOMINEE",
    "notification": "NOTIFICATION",
    "notifications": "NOTIFICATION",
    "ecs mandate": "ECS_MANDATE",
    "ecs": "ECS_MANDATE",
    "bright bhavishya": "BRIGHT_BHAVISHYA",
    "bhavishya": "BRIGHT_BHAVISHYA",
    "fallback": "FALLBACK",
}


def normalize_flow(flow: str) -> str:
    if pd.isna(flow) or not str(flow).strip():
        return "FALLBACK"
    key = str(flow).strip().lower()
    return FLOW_TO_INTENT.get(key, "FALLBACK")


def main():
    ap = argparse.ArgumentParser(description="Extract intent dataset from Simply Save transcript Excel")
    ap.add_argument("excel_path", help="Path to Simply Save - Transcript, Shared (1).xlsx")
    ap.add_argument("-o", "--output", default="models/intent_classifier/dataset/train.csv",
                    help="Output CSV path")
    ap.add_argument("--sheet", default=0, help="Sheet index or name (default: 0)")
    ap.add_argument("--english-col", default="English", help="Column name for English utterance")
    ap.add_argument("--native-col", default="Hindi", help="Column name for native language")
    ap.add_argument("--flow-col", default="Flow", help="Column name for Flow / intent")
    args = ap.parse_args()

    if not os.path.isfile(args.excel_path):
        raise SystemExit(f"File not found: {args.excel_path}")

    df = pd.read_excel(args.excel_path, sheet_name=args.sheet)
    cols = [c for c in df.columns if isinstance(c, str)]
    english_col = args.english_col if args.english_col in cols else (cols[0] if cols else None)
    native_col = args.native_col if args.native_col in cols else None
    flow_col = args.flow_col if args.flow_col in cols else None

    if flow_col is None:
        raise SystemExit("Flow column not found. Use --flow-col to specify.")

    rows = []
    for _, row in df.iterrows():
        flow_val = row.get(flow_col)
        intent = normalize_flow(flow_val)
        eng = row.get(english_col)
        native = row.get(native_col) if native_col else ""
        if pd.isna(eng) or str(eng).strip() == "":
            continue
        eng = str(eng).strip()
        native = "" if pd.isna(native) else str(native).strip()
        rows.append({
            "utterance_english": eng,
            "utterance_native": native,
            "intent": intent,
            "flow_raw": flow_val,
        })

    out_df = pd.DataFrame(rows)
    os.makedirs(os.path.dirname(os.path.abspath(args.output)) or ".", exist_ok=True)
    out_df.to_csv(args.output, index=False)
    print(f"Wrote {len(out_df)} rows to {args.output}")
    print(out_df["intent"].value_counts())


if __name__ == "__main__":
    main()
