#!/bin/bash

# Navigate to the Android project folder
cd /workspaces/RHC-App/RockHardBlocker

CONFIG_FILE="app/src/main/java/com/rockhard/blocker/Config.kt"

if [ "$1" == "ON" ]; then
    sed -i 's/const val UNINSTALL_PROTECTION_ENABLED = false/const val UNINSTALL_PROTECTION_ENABLED = true/g' $CONFIG_FILE
    echo "🛡️  Protection is now ON (Uninstall BLOCKED)."
elif [ "$1" == "OFF" ]; then
    sed -i 's/const val UNINSTALL_PROTECTION_ENABLED = true/const val UNINSTALL_PROTECTION_ENABLED = false/g' $CONFIG_FILE
    echo "🔓 Protection is now OFF (Uninstall ALLOWED)."
else
    echo "⚠️ Usage: ./toggle_protection.sh ON   |   ./toggle_protection.sh OFF"
    exit 1
fi

echo "⚙️ Compiling new APK..."
./gradlew assembleRelease
cp app/build/outputs/apk/rhc/release/app-rhc-release.apk ../rhc.apk

cd /workspaces/RHC-App
TAG="v$(date +%Y%m%d%H%M%S)"
GITHUB_TOKEN="" GH_TOKEN="" gh release create "$TAG" ./rhc.apk \
  --repo frostyosty/htc-downloads-rhc --title "Dev Build $TAG - Protection State: $1" \
  --notes "Compiled with UNINSTALL_PROTECTION_ENABLED = $1"

echo "✅ Done! Release $TAG pushed to GitHub."
