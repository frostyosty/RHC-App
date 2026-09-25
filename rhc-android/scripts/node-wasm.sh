#!/bin/bash
# Runs the system node for Kotlin/Wasm (see build.gradle.kts), minus the
# --experimental-wasm-* flags that Node 22+ rejects: wasm GC is built in there.
set -euo pipefail
args=()
for a in "$@"; do
  case "$a" in --experimental-wasm-*) ;; *) args+=("$a") ;; esac
done
exec node "${args[@]}"
