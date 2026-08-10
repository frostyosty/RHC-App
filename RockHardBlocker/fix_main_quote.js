const fs = require('fs');
try {
    const path = 'app/src/main/java/com/rockhard/blocker/MainActivity.kt';
    let code = fs.readFileSync(path, 'utf8');
    
    // Using a regex to catch == CUSTOM_APP with any spacing
    if (code.match(/==\s*CUSTOM_APP/)) {
        code = code.replace(/==\s*CUSTOM_APP/g, '== "CUSTOM_APP"');
        fs.writeFileSync(path, code);
        console.log("✅ FIXED: Quoted CUSTOM_APP successfully!");
    } else {
        console.log("⚠️ Could not find unquoted CUSTOM_APP.");
    }
} catch (e) {
    console.log("❌ Failed to patch: " + e.message);
}
