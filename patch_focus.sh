#!/bin/bash
cd /workspaces/RHC-App/rhc-desktop

node -e "
const fs = require('fs');

try {
    let path = 'src/main.cpp';
    let code = fs.readFileSync(path, 'utf8');

    // Regex to match the aggressive AttachThreadInput block
    const targetRegex = /HWND hFore = GetForegroundWindow\(\);[\s\S]*?SetForegroundWindow\(g_hDashboardWindow\);\s*\}/;

    if (code.match(targetRegex)) {
        const replacement = \`SetForegroundWindow(g_hDashboardWindow);
            
            // ✅ COMPLIANCE FIX: Use standard FlashWindowEx instead of Thread Hijacking
            FLASHWINFO fwi = {0};
            fwi.cbSize = sizeof(FLASHWINFO);
            fwi.hwnd = g_hDashboardWindow;
            fwi.dwFlags = FLASHW_ALL | FLASHW_TIMERNOFG;
            fwi.uCount = 5;
            fwi.dwTimeout = 0;
            FlashWindowEx(&fwi);\`;

        code = code.replace(targetRegex, replacement);
        fs.writeFileSync(path, code);
        console.log('✅ FIXED: Removed AttachThreadInput focus-stealing and replaced with compliant FlashWindowEx!');
    } else {
        console.log('❌ Could not find the AttachThreadInput block in main.cpp.');
    }
} catch(e) {
    console.log('❌ Failed C++ patch: ' + e.message);
}
"

# Trigger IntelliSense update
touch src/main.cpp

# Rebuild the desktop application
echo "Rebuilding rhc-desktop..."
./build.sh

# Jump back to root and the Android App dir as per standard workflow
cd /workspaces/RHC-App/RockHardBlocker

# 1. Compile all four flavors (optional if you are only testing desktop right now)
# ./gradlew assembleGamersMaleNetbeastsRelease 
# ./gradlew assembleGamersFemaleHomevisitsRelease 
# ./gradlew assembleTimesaversMaleMomentumRelease 
# ./gradlew assembleTimesaversFemaleMomentumRelease

