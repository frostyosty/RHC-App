const fs = require('fs');
const path = 'RockHardBlocker/app/src/main/java/com/rockhard/blocker/GameActivity.kt';
let code = fs.readFileSync(path, 'utf8');

const searchStr = 'findViewById<Button>(R.id.btnExit).setOnClickListener { finish() }';
const replaceStr = `findViewById<Button>(R.id.btnExit).setOnClickListener { 
            if (prefs.getBoolean("LAUNCH_GAME_DEFAULT", false)) {
                startActivity(Intent(this@GameActivity, MainActivity::class.java).apply {
                    putExtra("FROM_GAME", true)
                })
            } else {
                finish() 
            }
        }`;

if (code.includes(searchStr)) {
    code = code.replace(searchStr, replaceStr);
    fs.writeFileSync(path, code);
    console.log('✅ FIXED: btnExit now serves as an escape hatch to MainActivity when Netbeasts is the default launcher!');
} else {
    console.log('❌ Failed to find btnExit in GameActivity.kt! It might have already been patched.');
}
