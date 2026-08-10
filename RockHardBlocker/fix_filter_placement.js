const fs = require('fs');
try {
    const path = 'app/src/main/java/com/rockhard/blocker/MainActivity.kt';
    let code = fs.readFileSync(path, 'utf8');
    
    const startTag = 'val socialWeb = listOf(';
    const endTag = 'Pair("Yahoo Search", "search.yahoo.com"))';
    
    let sIdx = code.indexOf(startTag);
    let eIdx = code.indexOf(endTag, sIdx);
    
    if (sIdx !== -1 && eIdx !== -1) {
        eIdx += endTag.length;
        
        const newBlock = `val socialWeb = listOf(Pair("Instagram", "instagram.com"), Pair("Facebook", "facebook.com"), Pair("TikTok", "tiktok.com"), Pair("Twitter / X", "twitter.com"), Pair("Reddit", "reddit.com")).filter { !blockedWebDomains.contains(it.second.lowercase()) }
        val videoWeb = listOf(Pair("YouTube", "youtube.com"), Pair("Twitch", "twitch.tv"), Pair("Netflix", "netflix.com"), Pair("Hulu", "hulu.com")).filter { !blockedWebDomains.contains(it.second.lowercase()) }
        val newsWeb = listOf(Pair("CNN", "cnn.com"), Pair("Fox News", "foxnews.com"), Pair("BBC", "bbc.com"), Pair("NY Times", "nytimes.com")).filter { !blockedWebDomains.contains(it.second.lowercase()) }
        val searchWeb = listOf(Pair("Google Images", "google.com/search?tbm=isch"), Pair("Bing Video Search", "bing.com/videos"), Pair("Yahoo Search", "search.yahoo.com")).filter { !blockedWebDomains.contains(it.second.lowercase()) }`;
        
        code = code.substring(0, sIdx) + newBlock + code.substring(eIdx);
        fs.writeFileSync(path, code);
        console.log("✅ FIXED: Moved .filter() to the end of the lists!");
    } else {
        console.log("⚠️ Could not find the Web lists.");
    }
} catch (e) {
    console.log("❌ Failed to patch: " + e.message);
}
