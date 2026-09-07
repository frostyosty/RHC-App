#!/bin/bash
cd /workspaces/RHC-App

node -e "
const fs = require('fs');

// 1. PATCH ANDROID
try {
    let path = 'RockHardBlocker/app/src/main/java/com/rockhard/blocker/guardian/ShieldRuleEngine.kt';
    let code = fs.readFileSync(path, 'utf8');

    // Replace the hardcoded .com/.org extraction
    const oldBlock = /val baseWord = domainWord\s*\.replace\(\"www\.\", \"\"\)\s*\.replace\(\"\.com\", \"\"\)\s*\.replace\(\"\.org\", \"\"\)\s*\.replace\(\"\.net\", \"\"\)/;
    const newBlock = \`val baseWord = domainWord
            .replace(\"www.\", \"\")
            .substringBeforeLast(\".\") // Dynamically strip any TLD like .io, .co.uk, etc.\`;
    
    if (code.match(oldBlock)) {
        code = code.replace(oldBlock, newBlock);

        // Replace the specific list check
        const oldListCheck = /listOf\(\s*baseWord \+ \"\.com\",\s*\"m\.\" \+ baseWord \+ \"\.com\",\s*baseWord \+ \"\.org\",\s*\"www\.\" \+ baseWord \+ \"\.com\",\s*\"youtu\.be\",\s*baseWord \+ \"\.net\"\s*\)/;
        const newListCheck = \`listOf(
                        domainWord, // Exact match for things like itch.io
                        \"m.\$domainWord\",
                        \"www.\$domainWord\",
                        baseWord + \".com\",
                        baseWord + \".org\",
                        baseWord + \".net\",
                        \"youtu.be\"
                    )\`;

        code = code.replace(oldListCheck, newListCheck);
        fs.writeFileSync(path, code);
        console.log('✅ FIXED: Android ShieldRuleEngine now properly evaluates .io and alternative TLDs!');
    }
} catch(e) {
    console.log('❌ Failed Android patch: ' + e.message);
}

// 2. PATCH DESKTOP
try {
    let path = 'rhc-desktop/src/Guardian.cpp';
    let code = fs.readFileSync(path, 'utf8');

    const targetPoint = /RHC::ShieldResult result = ruleEngine\.evaluateScreenText\(scannedText, db\);/;

    if (code.match(targetPoint) && !code.includes('isWebBlocked')) {
        const injection = \`// ✅ NEW: Evaluate Web Blocklist directly against UIA Text to catch .io and subdomains!
            std::string lowerScannedText = RHC::StringUtils::toLower(scannedText);
            bool isWebBlocked = false;
            std::string blockedWebDomain = \"\";
            for (auto& blocked : RHC::StringUtils::split(db.getString(\"BLOCKLIST_WEB\", \"\"), ',')) {
                auto parts = RHC::StringUtils::split(blocked, '|');
                if (!parts.empty()) {
                    std::string domain = RHC::StringUtils::toLower(parts[0]);
                    if (domain.find(\"www.\") == 0) domain = domain.substr(4);
                    if (lowerScannedText.find(domain) != std::string::npos) {
                        isWebBlocked = true;
                        if (parts.size() >= 4 && parts[3] != \"None\" && RHC::Utils::IsTimeAllowed(parts[3])) isWebBlocked = false;
                        if (isWebBlocked) { blockedWebDomain = domain; break; }
                    }
                }
            }

            if (isWebBlocked) {
                RHC::DashboardUI::TrackOvercome(blockedWebDomain, \"BLOCKLIST_WEB\", \"FIRST_OVERCOME_WEB_\" + blockedWebDomain);
                RHC::Utils::InjectEvadeAction();
                std::string redirectTarget = getRedirect(blockedWebDomain);
                if (!redirectTarget.empty()) { ShellExecuteA(NULL, \"open\", redirectTarget.c_str(), NULL, NULL, SW_SHOW); continue; }
                g_RedWallReason = L\"Web Blocked: \" + RHC::Utils::utf8_to_wstring(blockedWebDomain);
                ShowWindow(g_hMainWindow, SW_RESTORE); SetForegroundWindow(g_hMainWindow); BringWindowToTop(g_hMainWindow); InvalidateRect(g_hMainWindow, NULL, TRUE);
                SetTimer(g_hMainWindow, 1, 3000, NULL); continue; 
            }

            RHC::ShieldResult result = ruleEngine.evaluateScreenText(scannedText, db);\`;

        code = code.replace(targetPoint, injection);
        fs.writeFileSync(path, code);
        console.log('✅ FIXED: Desktop Guardian now evaluates UIA text for subdomains and alternate TLDs!');
    }
} catch(e) {
    console.log('❌ Failed Desktop patch: ' + e.message);
}
"

# Trigger IntelliSense update
touch RockHardBlocker/app/src/main/java/com/rockhard/blocker/guardian/ShieldRuleEngine.kt
touch rhc-desktop/src/Guardian.cpp

# Rebuild Desktop App
cd rhc-desktop
./build.sh

# Jump back to Android directory
cd ../RockHardBlocker
# ./gradlew assembleGamersMaleNetbeastsRelease

