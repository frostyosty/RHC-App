const fs = require('fs');
try {
    const path = 'app/src/main/java/com/rockhard/blocker/MainActivity.kt';
    let code = fs.readFileSync(path, 'utf8');
    
    // Find the exact block with the extra brace and remove it
    const badBlock = /addBlockItem\("BLOCKLIST_WEB", webInput, webInput\)\s*\}\s*findViewById<EditText>\(R\.id\.etCustomWeb\)\.setText\(""\)\s*\}\s*\}/m;
    
    if (badBlock.test(code)) {
        code = code.replace(badBlock, 
`addBlockItem("BLOCKLIST_WEB", webInput, webInput)
            }
            findViewById<EditText>(R.id.etCustomWeb).setText("") 
        }`);
        fs.writeFileSync(path, code);
        console.log("✅ FIXED: Extra closing brace removed in MainActivity!");
    } else {
        console.log("⚠️ Could not find the extra brace block.");
    }
} catch (e) {
    console.log("❌ Failed to patch: " + e.message);
}
