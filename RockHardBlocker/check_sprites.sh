#!/bin/bash
BEASTS=("cacheon" "cardiol" "skirmalot" "cartini" "noobit" "atomit" "roamlet" "pornosaur" "goreilla" "fleshwire" "lustrot" "darkone")
ACTIONS=("idle" "walk" "defend" "attack")
echo "======================================"
echo "   NETBEAST SPRITE AUDIT REPORT"
echo "======================================"
MISSING=0
for b in "${BEASTS[@]}"; do
    for a in "${ACTIONS[@]}"; do
        FILE="app/src/main/res/drawable/${b}_${a}.gif"
        if[ ! -f "$FILE" ]; then
            echo "[ ] MISSING: ${b}_${a}.gif"
            MISSING=$((MISSING+1))
        fi
    done
done
echo "======================================"
if[ $MISSING -eq 0 ]; then echo "All sprites present!"; else echo "You need to create $MISSING GIFs."; fi
