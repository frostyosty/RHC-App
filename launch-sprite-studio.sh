#!/bin/bash
echo "🚀 Booting RHC Sprite Studio..."
echo "👉 When VS Code asks, click 'Open in Browser' or go to the 'Ports' tab and click the Local Address."
python3 "$(dirname "$0")/sprite_studio/server.py"
