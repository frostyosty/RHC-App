const fs = require('fs');
const path = 'RockHardBlocker/app/src/main/java/com/rockhard/blocker/GameActivity.kt';
let code = fs.readFileSync(path, 'utf8');

// 1. Fix the rogue brace that closed GameActivity early
const brokenBraces = `        }
        }

    override fun onCreate(savedInstanceState: Bundle?) {`;
    
const fixedBraces = `        }

    override fun onCreate(savedInstanceState: Bundle?) {`;

if (code.includes('}\n        }\n        }\n\n    override fun onCreate')) {
    code = code.replace('}\n        }\n        }\n\n    override fun onCreate', '}\n        }\n\n    override fun onCreate');
    fs.writeFileSync(path, code);
    console.log('✅ FIXED: Rogue closing brace removed! GameActivity is whole again.');
} else if (code.includes(brokenBraces)) {
    code = code.replace(brokenBraces, fixedBraces);
    fs.writeFileSync(path, code);
    console.log('✅ FIXED: Rogue closing brace removed! GameActivity is whole again.');
} else {
    console.log('⚠️ Could not find the rogue brace. It might already be fixed!');
}

