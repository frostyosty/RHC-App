#!/bin/bash
# Brings rigged 3D people into the Homevisits APK. Download the packs by hand
# (they're CC0, but itch.io wants a click): Quaternius' "Universal Animation
# Library" and "Universal Base Characters", Standard (free) versions.
#   bash tools/figures/import.sh unzip <pack.zip>...   unpack into tools/figures/packs/
#   bash tools/figures/import.sh list                  every file's meshes, rig and moves
#   bash tools/figures/import.sh build                 write the assets named in figures.json
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PACKS="$HERE/packs"
cmd="${1:-}"
shift || true

if [ ! -d "$HERE/node_modules" ]; then
    echo "📦 Installing gltf-transform..."
    (cd "$HERE" && npm install --no-audit --no-fund --silent)
fi

case "$cmd" in
    unzip)
        mkdir -p "$PACKS"
        for z in "$@"; do
            echo "📂 $(basename "$z")"
            unzip -oq "$z" -d "$PACKS/$(basename "$z" .zip)"
        done
        echo "✅ Unpacked into $PACKS. Next: bash tools/figures/import.sh list"
        ;;
    list)
        mapfile -t files < <(find "$PACKS" -type f \( -name '*.glb' -o -name '*.gltf' \) | sort)
        if [ ${#files[@]} -eq 0 ]; then echo "❌ No .glb or .gltf files in $PACKS. Unzip the packs first."; exit 1; fi
        node "$HERE/slim.mjs" list "${files[@]}"
        ;;
    build)
        node "$HERE/slim.mjs" build "$HERE/figures.json"
        echo "🖼️  Check them without a phone: bash tools/figures/preview/run.sh"
        ;;
    *)
        echo "usage: bash tools/figures/import.sh unzip <pack.zip>... | list | build"
        exit 1
        ;;
esac
