#!/bin/bash
# Interactive build + release menu, replacing the manual copy-paste
# commands that used to live in the bottom half of rhc-android/README.md.
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || { cd "$(dirname "${BASH_SOURCE[0]}")" && pwd; })"
cd "$ROOT_DIR"

RELEASE_REPO="frostyosty/htc-downloads-rhc"

declare -A TARGET_NAMES=(
  [1]="Desktop (rhc_desktop.exe)"
  [2]="Android: Gamers Male - Netbeasts"
  [3]="Android: Gamers Female - Homevisits"
  [4]="Android: Timesavers Male - Momentum"
  [5]="Android: Timesavers Female - Momentum"
  [6]="Android: ALL four flavors"
)

echo "======================================"
echo "   RHC BUILD & RELEASE"
echo "======================================"
for i in 1 2 3 4 5 6; do
  echo "  [$i] ${TARGET_NAMES[$i]}"
done
echo
read -rp "Enter what you want to build, as digits (e.g. 13 for Desktop + Gamers Female Homevisits): " CHOICE

if [ -z "$CHOICE" ]; then
  echo "Nothing entered, exiting."
  exit 0
fi

# Expand the typed digits into a de-duplicated ordered list of targets.
# "6" expands to the four individual Android flavors.
SELECTED=()
add_target() {
  for t in "${SELECTED[@]-}"; do [ "$t" == "$1" ] && return; done
  SELECTED+=("$1")
}
for (( i=0; i<${#CHOICE}; i++ )); do
  d="${CHOICE:$i:1}"
  case "$d" in
    1) add_target 1 ;;
    2) add_target 2 ;;
    3) add_target 3 ;;
    4) add_target 4 ;;
    5) add_target 5 ;;
    6) add_target 2; add_target 3; add_target 4; add_target 5 ;;
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

ARTIFACTS=()

build_desktop() {
  echo "🚀 Building Desktop..."
  bash rhc-desktop/build.sh
  ARTIFACTS+=("rhc-desktop/rhc_desktop.exe")
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
    1) build_desktop ;;
    2) build_android_flavor "GamersMaleNetbeasts" "gamersMaleNetbeasts" "rhc_netbeasts.apk" ;;
    3) build_android_flavor "GamersFemaleHomevisits" "gamersFemaleHomevisits" "rhc_homevisits.apk" ;;
    4) build_android_flavor "TimesaversMaleMomentum" "timesaversMaleMomentum" "rhc_momentum_m.apk" ;;
    5) build_android_flavor "TimesaversFemaleMomentum" "timesaversFemaleMomentum" "rhc_momentum_f.apk" ;;
  esac
done

echo
echo "✅ Build complete. Artifacts:"
for a in "${ARTIFACTS[@]}"; do echo "  - $a"; done
echo

read -rp "Create a GitHub release for these on $RELEASE_REPO? [y/N] " DO_RELEASE
if [[ "$DO_RELEASE" =~ ^[Yy]$ ]]; then
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

  TAG="v$(date +%Y%m%d%H%M%S)"
  read -rp "Release title [Dev Build $TAG]: " TITLE
  read -rp "Release notes (optional): " NOTES
  TITLE="${TITLE:-Dev Build $TAG}"

  echo
  echo "Publishing release '$TAG' to $RELEASE_REPO:"
  for a in "${ARTIFACTS[@]}"; do echo "  - $a"; done

  gh release create "$TAG" "${ARTIFACTS[@]}" \
    --repo "$RELEASE_REPO" \
    --title "$TITLE" \
    --notes "$NOTES"
  echo "🚀 Released $TAG to $RELEASE_REPO."
else
  echo "Skipping release. Artifacts remain built locally."
fi
