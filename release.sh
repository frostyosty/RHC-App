#!/bin/bash
# Interactive build + release menu, replacing the manual copy-paste
# commands that used to live in the bottom half of rhc-android/README.md.
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || { cd "$(dirname "${BASH_SOURCE[0]}")" && pwd; })"
cd "$ROOT_DIR"

RELEASE_REPO="frostyosty/htc-downloads-rhc"

declare -A TARGET_NAMES=(
  [1]="Desktop + ALL four Android flavors (default)"
  [2]="Desktop (rhc_desktop.exe)"
  [3]="Android: Gamers Male - Netbeasts"
  [4]="Android: Gamers Female - Homevisits"
  [5]="Android: Timesavers Male - Momentum"
  [6]="Android: Timesavers Female - Momentum"
  [7]="Android: ALL four flavors"
)

echo "======================================"
echo "   RHC BUILD & RELEASE"
echo "======================================"
for i in 1 2 3 4 5 6 7; do
  echo "  [$i] ${TARGET_NAMES[$i]}"
done
echo
read -rp "Enter what you want to build, as digits (e.g. 24 for Desktop + Gamers Female Homevisits) [1]: " CHOICE
CHOICE="${CHOICE:-1}"

# Expand the typed digits into a de-duplicated ordered list of targets.
# "1" expands to Desktop + the four Android flavors; "7" to just the four flavors.
SELECTED=()
add_target() {
  for t in "${SELECTED[@]-}"; do [ "$t" == "$1" ] && return; done
  SELECTED+=("$1")
}
for (( i=0; i<${#CHOICE}; i++ )); do
  d="${CHOICE:$i:1}"
  case "$d" in
    1) add_target 2; add_target 3; add_target 4; add_target 5; add_target 6 ;;
    2) add_target 2 ;;
    3) add_target 3 ;;
    4) add_target 4 ;;
    5) add_target 5 ;;
    6) add_target 6 ;;
    7) add_target 3; add_target 4; add_target 5; add_target 6 ;;
    *) echo "⚠️  Ignoring unrecognized option: $d" ;;
  esac
done

if [ ${#SELECTED[@]} -eq 0 ]; then
  echo "No valid targets selected, exiting."
  exit 1
fi

echo
echo "Building:"
for t in "${SELECTED[@]}"; do echo "  - ${TARGET_NAMES[$t]}"; done
echo

# Quicksave first (same steps as zz_quicksave.txt): commit everything, rebase
# onto origin/main and push, so the release is built from pushed code and the
# commit named in the release notes exists on GitHub. A rebase conflict stops
# the script here, before anything is built or published.
echo "💾 Quicksaving to origin/main..."
git add -A
git diff --cached --quiet || git commit -m "[quicksave note here] $(date '+%Y-%m-%d %H:%M:%S')"
git pull --rebase origin main
git push origin main
echo

ARTIFACTS=()

build_desktop() {
  echo "🚀 Building Desktop..."
  bash rhc-desktop/build.sh
  ARTIFACTS+=("rhc-desktop/RHC_Installer.exe")
}

build_android_flavor() {
  local gradleTask="$1" flavorDir="$2" outName="$3"
  echo "🚀 Building Android flavor: $flavorDir..."
  ( cd rhc-android && ./gradlew "assemble${gradleTask}Release" )
  local apkPath="rhc-android/app/build/outputs/apk/${flavorDir}/release/app-${flavorDir}-release.apk"
  cp "$apkPath" "$outName"
  ARTIFACTS+=("$outName")
}

for t in "${SELECTED[@]}"; do
  case "$t" in
    2) build_desktop ;;
    3) build_android_flavor "GamersMaleNetbeasts" "gamersMaleNetbeasts" "rhc_netbeasts.apk" ;;
    4) build_android_flavor "GamersFemaleHomevisits" "gamersFemaleHomevisits" "rhc_homevisits.apk" ;;
    5) build_android_flavor "TimesaversMaleMomentum" "timesaversMaleMomentum" "rhc_momentum_m.apk" ;;
    6) build_android_flavor "TimesaversFemaleMomentum" "timesaversFemaleMomentum" "rhc_momentum_f.apk" ;;
  esac
done

echo
echo "✅ Build complete. Artifacts:"
for a in "${ARTIFACTS[@]}"; do echo "  - $a"; done
echo

# Always publish: no prompts for confirmation, title or notes. The only
# interactive step left is `gh auth login` if the CLI isn't logged in.
# If Codespaces injected GITHUB_TOKEN/GH_TOKEN, don't let those
# override the GitHub CLI's stored credentials.
unset GITHUB_TOKEN GH_TOKEN

if ! gh auth status >/dev/null 2>&1; then
  echo "🔐 GitHub login is required."
  gh auth login
fi
if ! gh release list --repo "$RELEASE_REPO" --limit 1 >/dev/null 2>&1; then
  echo "🔐 Cannot access $RELEASE_REPO yet. Please log in with an account that has access."
  gh auth login
fi

# Version: take the highest vMAJOR.MINOR.PATCH release on the repo and bump
# PATCH. The old timestamp tags (v20260922124541) don't match and are ignored.
# With no semver release yet, start at v1.0.0.
LAST_TAG=$(gh release list --repo "$RELEASE_REPO" --limit 1000 --json tagName -q '.[].tagName' \
  | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)
if [ -n "$LAST_TAG" ]; then
  IFS=. read -r MAJOR MINOR PATCH <<< "${LAST_TAG#v}"
  TAG="v$MAJOR.$MINOR.$((PATCH + 1))"
else
  TAG="v1.0.0"
fi
TITLE="Dev Build $TAG"
NOTES="Built from $(git rev-parse --short HEAD) on $(date '+%Y-%m-%d %H:%M')."$'\n\n'"Includes:"
for t in "${SELECTED[@]}"; do NOTES+=$'\n'"- ${TARGET_NAMES[$t]}"; done

echo "Publishing release '$TAG' to $RELEASE_REPO..."
RELEASE_URL=$(gh release create "$TAG" "${ARTIFACTS[@]}" \
  --repo "$RELEASE_REPO" \
  --title "$TITLE" \
  --notes "$NOTES")

echo
echo "🚀 Released $TAG to $RELEASE_REPO."
echo "🔗 $RELEASE_URL"
