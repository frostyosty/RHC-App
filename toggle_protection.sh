#!/bin/bash

# Exit on errors, unset variables, or failed commands in pipelines.
set -euo pipefail

# Navigate to the Android project folder
PROJECT_DIR="/workspaces/RHC-App/RockHardBlocker"
cd "$PROJECT_DIR"

CONFIG_FILE="app/src/main/java/com/rockhard/blocker/Config.kt"
OUTPUT_APK="app/build/outputs/apk/rhc/release/app-rhc-release.apk"
DOWNLOAD_APK="/workspaces/RHC-App/rhc.apk"
REPO="frostyosty/htc-downloads-rhc"

# ------------------------------------------------------------
# Validate protection mode
# ------------------------------------------------------------

if [ "$#" -ne 1 ]; then
    echo "⚠️ Usage: ./toggle_protection.sh ON | OFF"
    exit 1
fi

if [ "$1" == "ON" ]; then
    sed -i \
      's/const val UNINSTALL_PROTECTION_ENABLED = false/const val UNINSTALL_PROTECTION_ENABLED = true/g' \
      "$CONFIG_FILE"

    echo "🛡️  Protection is now ON (Uninstall BLOCKED)."

elif [ "$1" == "OFF" ]; then
    sed -i \
      's/const val UNINSTALL_PROTECTION_ENABLED = true/const val UNINSTALL_PROTECTION_ENABLED = false/g' \
      "$CONFIG_FILE"

    echo "🔓 Protection is now OFF (Uninstall ALLOWED)."

else
    echo "⚠️ Invalid option: $1"
    echo "Usage: ./toggle_protection.sh ON | OFF"
    exit 1
fi

# ------------------------------------------------------------
# Build APK
# ------------------------------------------------------------

echo ""
echo "⚙️ Compiling new APK..."

if ! ./gradlew assembleRelease; then
    echo "❌ APK build failed. No GitHub release was created."
    exit 1
fi

# ------------------------------------------------------------
# Verify and copy APK
# ------------------------------------------------------------

if [ ! -f "$OUTPUT_APK" ]; then
    echo "❌ Build completed, but the expected APK was not found:"
    echo "   $OUTPUT_APK"
    exit 1
fi

cp "$OUTPUT_APK" "$DOWNLOAD_APK"

if [ ! -f "$DOWNLOAD_APK" ]; then
    echo "❌ Failed to copy APK to:"
    echo "   $DOWNLOAD_APK"
    exit 1
fi

echo "✅ APK built successfully."
echo "📦 APK ready at: $DOWNLOAD_APK"

# ------------------------------------------------------------
# GitHub authentication
# ------------------------------------------------------------

cd /workspaces/RHC-App

echo ""
echo "🔐 Checking GitHub authentication..."

# If Codespaces injected GITHUB_TOKEN/GH_TOKEN, don't let those
# override the GitHub CLI's stored credentials.
unset GITHUB_TOKEN
unset GH_TOKEN

if ! gh auth status >/dev/null 2>&1; then
    echo "🔐 GitHub login is required."
    echo ""
    gh auth login
fi

# ------------------------------------------------------------
# Verify repository access
# ------------------------------------------------------------

echo ""
echo "🔎 Checking access to GitHub repository..."

if ! gh release list --repo "$REPO" --limit 1 >/dev/null 2>&1; then
    echo "❌ Cannot access GitHub repository:"
    echo "   $REPO"
    echo ""
    echo "🔐 Please log in with a GitHub account that has access to this repository."
    echo ""

    gh auth login

    if ! gh release list --repo "$REPO" --limit 1 >/dev/null 2>&1; then
        echo "❌ GitHub authentication still cannot access $REPO."
        echo "   The APK was built successfully, but no release was created."
        exit 1
    fi
fi

echo "✅ GitHub repository access confirmed."

# ------------------------------------------------------------
# Create GitHub Release
# ------------------------------------------------------------

TAG="v$(date +%Y%m%d%H%M%S)"

echo ""
echo "🚀 Creating GitHub release..."
echo "   Repository: $REPO"
echo "   Tag:        $TAG"
echo "   Protection: $1"

if gh release create "$TAG" "$DOWNLOAD_APK" \
    --repo "$REPO" \
    --title "Dev Build $TAG - Protection State: $1" \
    --notes "Compiled with UNINSTALL_PROTECTION_ENABLED = $1"; then

    echo ""
    echo "✅ Done!"
    echo "📦 APK built successfully."
    echo "🚀 Release $TAG pushed to GitHub."
    echo "🛡️  Protection state: $1"
else
    echo ""
    echo "❌ APK built successfully, but the GitHub Release upload failed."
    echo "   Tag: $TAG"
    echo "   Repository: $REPO"
    exit 1
fi

