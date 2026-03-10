# Sharing the SDK with someone else

The full folder can be **~20 GB** (models + Python venv + release bundle). Here are practical ways to share.

---

## Option 1: Share only code (small, recommended for Git)

**Size: ~50–300 MB** (no models, no `.venv`, no `release`)

- Use the repo **with `.gitignore`** so Git does not track:
  - `model-pipeline/models/`
  - `model-pipeline/.venv/`
  - `model-pipeline/release/`
  - `node_modules/`
- Push to GitHub / GitLab / your Git server. The other person **clones** and then runs:

```bash
# 1. Install JS deps and build SDK
npm install && npm run build

# 2. Get models (they need HF_TOKEN and disk space)
cd model-pipeline
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export HF_TOKEN=their_token
python scripts/download_three_models.py --stt --translation
python scripts/quantize_three_models.py --stt --translation
python scripts/prepare_release.py --output-dir release
```

They get the same setup without you sending 20 GB.

---

## Option 2: Share code + release bundle (~1.5 GB)

**Use when:** They only need to **run the app and serve models**, not re-run the Python pipeline.

- **Exclude:** `model-pipeline/models/`, `model-pipeline/.venv/`
- **Include:** repo code + `model-pipeline/release/` (the 1.1 GB bundle the app downloads from)

Then share one archive (zip/tar.gz) or upload to Google Drive / WeTransfer / NAS.

- They **don’t** need to run `download_three_models.py` or `quantize_three_models.py`.
- They run `python -m http.server 8765` from `model-pipeline` and point the app at their laptop IP.
- If they need to re-run the pipeline later, they create a venv and run `pip install -r requirements.txt` then the scripts.

**Create the archive (from repo root):**

```bash
# From simpysavesdk root
zip -r sdk-with-release.zip . -x "model-pipeline/models/*" -x "model-pipeline/.venv/*" -x "node_modules/*" -x "*.git*"
# Or with tar (often smaller):
tar --exclude='model-pipeline/models' --exclude='model-pipeline/.venv' --exclude='node_modules' --exclude='.git' -czvf sdk-with-release.tar.gz .
```

---

## Option 3: Share the full folder (~20 GB)

**Use when:** They need everything (e.g. same models, same venv) and can accept large transfer.

- **Google Drive / OneDrive / Dropbox:** Upload the whole folder (or a zip). May take a long time; Drive has 15 GB free.
- **WeTransfer:** Free up to 2 GB; paid for larger.
- **USB / external drive:** Copy the folder and hand it over.
- **NAS or shared network path:** Copy folder to a shared location they can access.

Do **not** put 20 GB in a Git repo (GitHub/GitLab have size and performance limits).

---

## Summary

| Goal                         | What to share                    | Approx size   |
|-----------------------------|-----------------------------------|---------------|
| They develop / run pipeline | Code only (Option 1, use .gitignore) | ~50–300 MB   |
| They only run app + server  | Code + `release/` (Option 2)     | ~1.5 GB       |
| Exact copy of your machine  | Full folder (Option 3)            | ~20 GB        |

Use **Option 1** for Git and when they can run the model scripts. Use **Option 2** when you want to avoid sending models and venv but still give them a working release bundle.
