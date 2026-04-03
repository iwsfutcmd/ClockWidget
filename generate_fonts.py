#!/usr/bin/env python3
"""
Refreshes the bundled Google Fonts list before a release.
Requires a Google Fonts API key (https://console.cloud.google.com).
The key is only used here — it never goes in the app.

Usage:
    python3 generate_fonts.py YOUR_API_KEY
"""
import json, sys, urllib.request

key = sys.argv[1] if len(sys.argv) > 1 else input("API key: ").strip()
url = f"https://www.googleapis.com/webfonts/v1/webfonts?key={key}&sort=popularity"
raw = urllib.request.urlopen(url).read()
out = "app/src/main/assets/google_fonts.json"
with open(out, "wb") as f:
    f.write(raw)
count = len(json.loads(raw)["items"])
print(f"Wrote {count} fonts to {out}")
