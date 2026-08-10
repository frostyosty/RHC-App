const fs = require('fs');
try {
    const path = 'app/src/main/java/com/rockhard/blocker/MainActivity.kt';
    let code = fs.readFileSync(path, 'utf8');
    
    // Fix the missing quotes
    if (code.includes('destination == CUSTOM_APP')) {
        code = code.replace(/destination == CUSTOM_APP/g, 'destination == "CUSTOM_APP"');
        fs.writeFileSync(path, code);
        console.log("✅ FIXED: Added missing quotes around CUSTOM_APP in MainActivity!");
    } else {
        console.log("⚠️ Could not find unquoted CUSTOM_APP. Check line 269 manually.");
    }
} catch (e) {
    console.log("❌ Failed to patch: " + e.message);
}
