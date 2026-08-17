const fs = require('fs');
const path = 'RockHardBlocker/app/src/main/java/com/rockhard/blocker/guardian/GuardianService.kt';
let code = fs.readFileSync(path, 'utf8');

if (!code.includes('private var toastyOverlayView')) {
    code = code.replace(
        'private var overlayView: View? = null',
        'private var overlayView: View? = null\n    private var toastyOverlayView: View? = null'
    );
}

const tbiStart = code.indexOf('private fun triggerBossInvasion(debugReason: String, canDefend: Boolean) {');
const destroyStart = code.indexOf('override fun onDestroy() {');

if (tbiStart !== -1 && destroyStart !== -1) {
    const newCode = fs.readFileSync('replacement.kt', 'utf8');
    
    // Safely inject the raw replacement code
    code = code.substring(0, tbiStart) + newCode + '\n    ' + code.substring(destroyStart);
    fs.writeFileSync(path, code);
    console.log('✅ FIXED: Replaced full-screen Red Wall with an instant redirect + Bottom Toasty Slider!');
} else {
    console.log('❌ Failed to find replacement bounds!');
}
