#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT_DIR"

shopt -s nullglob
files=( *.png )
shopt -u nullglob

if [ ${#files[@]} -eq 0 ]; then
  echo "❌ No .png files found in the root directory."
  exit 1
fi

echo "=== DESKTOP ICON SELECTION ==="
echo "Please select an image to use as the DESKTOP master icon:"
i=1
for f in "${files[@]}"; do
  echo "  [$i] $f"
  ((i++))
done
read -rp "Enter number for Desktop: " choice1

echo
echo "=== MOBILE ICON SELECTION ==="
echo "Please select an image to use as the MOBILE master (Android):"
i=1
for f in "${files[@]}"; do
  echo "  [$i] $f"
  ((i++))
done
read -rp "Enter number for Mobile: " choice2

if ! [[ "$choice1" =~ ^[0-9]+$ ]] || [ "$choice1" -lt 1 ] || [ "$choice1" -gt "${#files[@]}" ] || \
   ! [[ "$choice2" =~ ^[0-9]+$ ]] || [ "$choice2" -lt 1 ] || [ "$choice2" -gt "${#files[@]}" ]; then
  echo "❌ Invalid selection."
  exit 1
fi

MASTER_DESKTOP="$(realpath "${files[$((choice1-1))]}")"
MASTER_MOBILE="$(realpath "${files[$((choice2-1))]}")"

echo
echo "🖥️  Desktop Master: $MASTER_DESKTOP"
echo "📱 Mobile Master: $MASTER_MOBILE"

echo "⬇️  Installing temporary image processing tools..."
npm install --no-save sharp png-to-ico >/dev/null 2>&1

echo "🔨 Generating Native Icons (Trimming to Square)..."

node -e '
const sharp = require("sharp");
const pngModule = require("png-to-ico");
const pngToIco = pngModule.default ?? pngModule;
const fs = require("fs");

async function generate() {
    try {
        const deskMaster = process.argv[1];
        const mobMaster = process.argv[2];
        const rhcDir = "/workspaces/RHC-App";

        console.log("  -> Creating square cropped masters...");

        await sharp(deskMaster)
            .resize(256, 256, { fit: "cover", position: "center" })
            .toFile("temp_desk.png");

        await sharp(mobMaster)
            .resize(512, 512, { fit: "cover", position: "center" })
            .toFile("temp_mob.png");

        fs.mkdirSync(rhcDir + "/rhc-desktop", { recursive: true });

        console.log("  -> Generating rhc-desktop/rhc_icon.ico...");

        const icoBuf = await pngToIco(["temp_desk.png"]);

        fs.writeFileSync(rhcDir + "/rhc-desktop/rhc_icon.ico", icoBuf);
        fs.copyFileSync(
            "temp_desk.png",
            rhcDir + "/rhc-desktop/rhc_icon.png"
        );

        const androidRes = rhcDir + "/RockHardBlocker/app/src/main/res";

        const mipmaps = [
            { name: "mdpi", size: 48 },
            { name: "hdpi", size: 72 },
            { name: "xhdpi", size: 96 },
            { name: "xxhdpi", size: 144 },
            { name: "xxxhdpi", size: 192 }
        ];

        for (const m of mipmaps) {
            const dir = androidRes + "/mipmap-" + m.name;
            fs.mkdirSync(dir, { recursive: true });

            console.log("  -> Generating Android mipmap-" + m.name + "...");

            await sharp("temp_mob.png")
                .resize(m.size, m.size)
                .toFile(dir + "/ic_launcher.png");
        }

        fs.unlinkSync("temp_desk.png");
        fs.unlinkSync("temp_mob.png");

        console.log("✅ Success! RHC icons have been generated.");
    } catch (e) {
        console.error("❌ Error generating icons:", e);
        process.exit(1);
    }
}

generate();
' "$MASTER_DESKTOP" "$MASTER_MOBILE"