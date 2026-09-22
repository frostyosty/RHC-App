#!/bin/bash
# One-time setup for a fresh Codespace. Safe to re-run: every step skips
# itself when its target is already in place.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

echo "📦 Installing apt packages (desktop toolchain + dev tools)..."
sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  mingw-w64 nsis unzip \
  shellcheck ccache fd-find clang-tidy bear
sudo ln -sf "$(command -v fdfind)" /usr/local/bin/fd

echo "☕ Making sure JDK 17 is the default..."
# sdkman's scripts reference unset variables, so relax -u around them.
set +u
# shellcheck disable=SC1091
source /usr/local/sdkman/bin/sdkman-init.sh
JDK17="$(find "$SDKMAN_CANDIDATES_DIR/java" -mindepth 1 -maxdepth 1 -name '17.*' -printf '%f\n' | sort -V | tail -n 1)"
if [ -z "$JDK17" ]; then
  sdk install java 17.0.10-tem </dev/null
  JDK17="17.0.10-tem"
fi
sdk default java "$JDK17" </dev/null
set -u

echo "🤖 Installing Android SDK into $ANDROID_HOME..."
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" -o "$tmp/tools.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null

# local.properties is gitignored, so Gradle needs it recreated.
echo "sdk.dir=$ANDROID_HOME" > "$ROOT_DIR/rhc-android/local.properties"

# The release keystore is gitignored. To have it restored automatically, add
# a Codespaces secret RHC_KEYSTORE_B64 containing `base64 -w0 rockhard-keystore.jks`.
KEYSTORE="$ROOT_DIR/rhc-android/app/rockhard-keystore.jks"
if [ ! -f "$KEYSTORE" ]; then
  if [ -n "${RHC_KEYSTORE_B64:-}" ]; then
    echo "🔑 Restoring release keystore from RHC_KEYSTORE_B64..."
    echo "$RHC_KEYSTORE_B64" | base64 -d > "$KEYSTORE"
  else
    echo "⚠️  No release keystore: Android release builds will fail to sign."
    echo "    Copy rockhard-keystore.jks into rhc-android/app/ or set the RHC_KEYSTORE_B64 secret."
  fi
fi

echo "🎨 Installing tool dependencies..."
pip3 install --quiet Pillow
( cd "$ROOT_DIR/tools/icons" && npm install --silent )

echo "✅ Codespace ready. Try ./release.sh"
