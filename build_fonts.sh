#!/usr/bin/env bash
# Fetch TTF fonts from Google Fonts (fonts.gstatic.com) using a legacy
# Android User-Agent so the CSS API returns truetype URLs instead of woff2.
# Outputs: app/src/main/assets/fonts/*.ttf
set -euo pipefail

UA='Mozilla/5.0 (Linux; Android 4.0; en-us; Nexus S Build/IRH69) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30'
ROOT="$(cd "$(dirname "$0")" && pwd)"
DEST="$ROOT/app/src/main/assets/fonts"
mkdir -p "$DEST"

fetch_css_url() {
  local family="$1"
  local query="$2"
  curl -s -A "$UA" "https://fonts.googleapis.com/css?family=${family}${query}" \
    | grep -oE 'https://fonts.gstatic.com/[^)]*\.ttf' | head -n1
}

download_font() {
  local out="$1"
  local family="$2"
  local query="$3"
  local url
  url="$(fetch_css_url "$family" "$query")"
  if [ -z "$url" ]; then
    echo "!! Failed to resolve URL for ${family}${query}" >&2
    return 1
  fi
  echo "  ${out} <- ${url}"
  curl -sL -o "$DEST/$out" "$url"
  if ! file "$DEST/$out" | grep -q TrueType; then
    echo "!! Downloaded file is not TTF: $(file "$DEST/$out")" >&2
    rm -f "$DEST/$out"
    return 1
  fi
}

echo "==> Noto Sans SC (Regular, chinese-simplified subset)"
download_font NotoSansSC-Regular.ttf "Noto+Sans+SC" "&subset=chinese-simplified"

echo "==> Noto Sans SC (Bold, chinese-simplified subset)"
download_font NotoSansSC-Bold.ttf "Noto+Sans+SC:700" "&subset=chinese-simplified"

echo "==> Noto Serif SC (Regular, chinese-simplified subset)"
download_font NotoSerifSC-Regular.ttf "Noto+Serif+SC" "&subset=chinese-simplified"

echo "==> Roboto (Regular, Latin)"
download_font Roboto-Regular.ttf "Roboto" ""

echo
echo "==> Done. Files in $DEST:"
ls -lh "$DEST"
