# End-to-end setup: run the app and model server

Use this if someone shared the **simpysavesdk** folder (and optionally the **SimplySaveTestApp** or a pre-built APK) with you. Follow the steps in order.

---

## Prerequisites

- **Node.js** (v18+), **npm**
- **Python 3.10+**
- **Android Studio** / Android SDK, **adb**, device or emulator
- **HuggingFace token** ([huggingface.co](https://huggingface.co) → Settings → Access Tokens) — only if you need to download models yourself
- Phone and laptop on the **same WiFi** (or laptop connected to phone’s hotspot)

---

## Step 1: SDK – install and build

From the **simpysavesdk** folder (repo root):

```bash
cd /path/to/simpysavesdk
npm install
npm run build
```

---

## Step 2: Models – get the release bundle

You need the `model-pipeline/release/` folder. Either you received it in the share, or you generate it.

### If you already have `model-pipeline/release/` (e.g. shared with you)

Skip to **Step 3**.

### If you don’t have `release/` – run the pipeline

```bash
cd model-pipeline
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
export HF_TOKEN=your_huggingface_token_here
python scripts/download_three_models.py --stt --translation
python scripts/quantize_three_models.py --stt --translation
python scripts/prepare_release.py --output-dir release
```

Then `release/` will be under `model-pipeline/`.

---

## Step 3: Start the model server (your laptop)

The app downloads models from an HTTP server. That server must run on **your laptop** and serve the `release/` folder.

From the **simpysavesdk** folder:

```bash
cd model-pipeline
./serve_models.sh
```

Or manually:

```bash
cd model-pipeline
python3 -m http.server 8765
```

- Leave this terminal open. You should see: `Serving HTTP on 0.0.0.0 port 8765`.
- If you used `serve_models.sh`, it prints a line like:  
  `const MODEL_BASE_URL = 'http://10.133.160.170:8765/release';`  
  That **IP is your laptop’s IP** — you’ll use it in the app (see Step 5).

**Get your laptop IP if you didn’t use the script:**

```bash
hostname -I | awk '{print $1}'
```

Example: `10.133.160.170` → base URL is `http://10.133.160.170:8765/release`.

**Firewall:** If the phone can’t reach the server, allow port 8765:

```bash
sudo ufw allow 8765
```

---

## Step 4: Test app – APK vs building yourself

The test app (SimplySaveTestApp) must use **your laptop’s IP** in `MODEL_BASE_URL`. A pre-built APK has the **builder’s** IP, so it only works when the phone can reach that person’s laptop.

### Option A: You have a pre-built APK only

- The APK will only work if the phone can reach the **original builder’s** laptop (same network as them).
- To use **your** laptop as the model server, you need to build the app yourself (Option B) and set your IP.

### Option B: Build the test app yourself (recommended)

1. Get the **SimplySaveTestApp** source (whoever shared the SDK with you should provide this folder or repo).
2. Install SDK in the app (if not already):
   ```bash
   cd SimplySaveTestApp
   npm install
   # If the SDK is local:
   npm install /path/to/simpysavesdk
   ```
3. **Set your laptop IP** in the app. Open `App.tsx` and set:
   ```javascript
   const MODEL_BASE_URL = 'http://YOUR_LAPTOP_IP:8765/release';
   ```
   Replace `YOUR_LAPTOP_IP` with the IP from Step 3 (e.g. `10.133.160.170`).
4. Build and run on device:
   ```bash
   npx react-native run-android
   ```
5. If the device doesn’t load the JS bundle, run once (device connected via USB):
   ```bash
   adb reverse tcp:8081 tcp:8081
   ```
   Then start Metro (if not already): `npx react-native start`, and open the app again.

---

## Step 5: Phone and network

- Connect the **phone** to the **same WiFi as the laptop** (or connect the laptop to the phone’s hotspot).
- Ensure the model server is still running (Step 3) and the app uses the correct `MODEL_BASE_URL` (your laptop IP).

---

## Step 6: Use the app

1. Open the SimplySave test app on the phone.
2. Tap **Initialize** — the app will download models from your laptop (first time may take a few minutes).
3. Use **Process Text** or **Record → Stop & process** to test.

---

## Quick reference

| What | Where / command |
|------|------------------|
| Your laptop IP | `hostname -I \| awk '{print $1}'` |
| Model server | `cd model-pipeline && ./serve_models.sh` (or `python3 -m http.server 8765`) |
| URL in app | `http://YOUR_LAPTOP_IP:8765/release` in `App.tsx` → `MODEL_BASE_URL` |
| Same network | Phone and laptop on same WiFi (or laptop on phone hotspot) |
| Firewall | `sudo ufw allow 8765` if phone can’t reach server |
| Metro (if building app) | `adb reverse tcp:8081 tcp:8081` then `npx react-native start` |

---

## If models don’t download (“server not getting hit”)

1. Confirm model server is running from **model-pipeline** (so `release/` is served at `/release/...`).
2. In the app, `MODEL_BASE_URL` must be `http://YOUR_LAPTOP_IP:8765/release` (your current laptop IP).
3. Phone and laptop on same network; try opening `http://YOUR_LAPTOP_IP:8765/release/` in the phone’s browser to test.
4. Allow port 8765 in the laptop firewall.

More detail: see **Troubleshooting: Model server not getting hit** in `RUN_GUIDE.md`.
