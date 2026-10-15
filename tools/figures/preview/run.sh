#!/bin/bash
# Renders rigged figures to PNG sheets on the plain JVM, with figure-core
# compiled by the Kotlin compiler bundled in the Gradle distribution (as
# tools/world_preview does). One sheet per move: 8 views across, 4 moments down.
# Usage: bash tools/figures/preview/run.sh [figure.glb [moves.glb]]
# With no figure it draws the blocky mannequin. After import.sh build, try:
#   bash tools/figures/preview/run.sh rhc-android/app/src/gamersFemaleHomevisits/assets/figures/{test,moves}.glb
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/tools/figures/preview/out"
CORE="$ROOT/rhc-android/figure-core/src/commonMain/kotlin/com/rockhard/blocker/figure"
LIB="$(find "$HOME/.gradle/wrapper/dists" -type d -path '*gradle-8.7/lib' | head -n 1)"
if [ -z "$LIB" ]; then echo "❌ Gradle 8.7 not found; run ./gradlew once in rhc-android first."; exit 1; fi

jar() { find "$LIB" -maxdepth 1 -name "$1" | head -n 1; }
STDLIB="$(jar 'kotlin-stdlib-1.9*.jar')"
CP="$(jar 'kotlin-compiler-embeddable-*.jar'):$STDLIB:$(jar 'kotlin-script-runtime-*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-daemon-embeddable-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar')"

# Paths given relative to where the user ran this
args=()
for f in "$@"; do args+=("$(cd "$(dirname "$f")" && pwd)/$(basename "$f")"); done

BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
mkdir -p "$OUT"
echo "🔨 Compiling figure-core + preview..."
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -no-stdlib -cp "$STDLIB" -d "$BUILD" \
    "$ROOT/tools/figures/preview/FigurePreview.kt" "$CORE"/*.kt
echo "🧍 Rendering..."
java -Djava.awt.headless=true -cp "$BUILD:$STDLIB" FigurePreviewKt "$OUT" "${args[@]}"
echo "🖼️  Sheets in $OUT"
