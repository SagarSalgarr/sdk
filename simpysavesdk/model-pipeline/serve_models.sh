#!/usr/bin/env bash
# Start the model HTTP server from model-pipeline so /release/... is served.
# Usage: ./serve_models.sh   (run from model-pipeline or repo root)

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -d release ]]; then
  echo "No release/ folder found. Run: python scripts/prepare_release.py --output-dir release"
  exit 1
fi

# Prefer WiFi-style IP for phones on same LAN
IP=$(hostname -I 2>/dev/null | awk '{print $1}')
if [[ -z "$IP" ]]; then
  IP=$(ip -4 route get 8.8.8.8 2>/dev/null | grep -oP 'src \K[\d.]+' || echo "YOUR_IP")
fi

echo "=============================================="
echo "Model server will serve from: $(pwd)"
echo "Release folder: $(pwd)/release"
echo ""
echo "In your app (e.g. SimplySaveTestApp/App.tsx) set:"
echo "  const MODEL_BASE_URL = 'http://${IP}:8765/release';"
echo ""
echo "If the device still doesn't hit this server:"
echo "  - Phone and PC on same WiFi"
echo "  - Allow port: sudo ufw allow 8765"
echo "  - Check logcat: adb logcat | grep -E 'SimplySaveVoice|ModelDeliveryManager'"
echo "=============================================="
echo "Starting HTTP server on 0.0.0.0:8765 ..."
exec python3 -m http.server 8765
