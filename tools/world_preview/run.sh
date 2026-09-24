#!/bin/bash
# Compiles the 3D Wilds sim + renderer (pure Kotlin, no Android) on the plain
# JVM with the Kotlin compiler bundled in the Gradle distribution, runs a
# soak test (determinism, encounters, snapshot round-trip, timing) and writes
# preview PNGs. Usage: bash tools/world_preview/run.sh [out_dir]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:-$ROOT/tools/world_preview/out}"
WORLD="$ROOT/rhc-android/world-core/src/commonMain/kotlin/com/rockhard/blocker/world"
LIB="$(find "$HOME/.gradle/wrapper/dists" -type d -path '*gradle-8.7/lib' | head -n 1)"
if [ -z "$LIB" ]; then echo "❌ Gradle 8.7 not found; run ./gradlew once in rhc-android first."; exit 1; fi

jar() { find "$LIB" -maxdepth 1 -name "$1" | head -n 1; }
STDLIB="$(jar 'kotlin-stdlib-1.9*.jar')"
CP="$(jar 'kotlin-compiler-embeddable-*.jar'):$STDLIB:$(jar 'kotlin-script-runtime-*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-daemon-embeddable-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar')"

BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
mkdir -p "$OUT"
echo "🔨 Compiling world sim + renderer..."
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -no-stdlib -cp "$STDLIB" -d "$BUILD" \
    "$ROOT/tools/world_preview/WorldPreview.kt" "$WORLD"/sim/*.kt "$WORLD"/render/*.kt
echo "🌲 Running..."
java -cp "$BUILD:$STDLIB" WorldPreviewKt "$OUT"
echo "🖼️  Previews in $OUT"
