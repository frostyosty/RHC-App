#!/bin/bash
cd /workspaces/RHC-App/rhc-android || { echo "Directory /workspaces/RHC-App/rhc-android not found"; exit 1; }
rm -f output.txt

FILES=(
  "app/src/main/AndroidManifest.xml"
  "app/src/main/java/com/rockhard/blocker/MainActivity.kt"
  "app/src/main/java/com/rockhard/blocker/MainMomentumUI.kt"
  "app/src/main/java/com/rockhard/blocker/MomentumActivity.kt"
  "app/src/main/java/com/rockhard/blocker/LeaderboardEngine.kt"
  "app/src/main/java/com/rockhard/blocker/guardian/GuardianService.kt"
  "app/src/main/java/com/rockhard/blocker/MomentumEngine.kt"
  "app/src/main/java/com/rockhard/blocker/engines/AetherEngine.kt"
  "world-core/build.gradle.kts"
)

for f in "${FILES[@]}"; do
  if [ -f "$f" ]; then
    echo "===== $f =====" >> output.txt
    cat "$f" >> output.txt
    echo -e "\n\n" >> output.txt
  else
    echo "===== $f (NOT FOUND) =====" >> output.txt
    echo -e "\n\n" >> output.txt
  fi
done
