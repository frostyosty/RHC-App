# RHC-App

quick changes --> do change then
./gradlew assembleRelease
cp app/build/outputs/apk/rhc/release/app-rhc-release.apk ./rhc.apk
gh release create "v$(date +%Y%m%d%H%M)" ./rhc.apk --repo frostyosty/htc-downloads-rhc --title "Quick Tweak"

## The Pro Way
Let's do the "Pro Way" we talked about earlier.
On your phone: Go to Settings 
→
→
 Developer Options 
→
→
 Enable USB Debugging.
Plug your phone into your actual computer with a USB cable.
On your local computer (NOT the Codespace): Open your computer's terminal (Command Prompt, PowerShell, or Mac Terminal).
Run this command to download the newly built APK straight from your Codespace and install it onto your plugged-in phone:
code
Bash
# note this uses the GitHub CLI to pull the file and ADB to install it
gh codespace cp remote:/workspaces/RHC-App/RockHardBlocker/app/build/outputs/apk/bro/debug/app-bro-debug.apk ./app-bro-debug.apk && adb install -r app-bro-debug.apk



## Getting this on your phone

# Step 1: Start the Codespace Web Server
Run this exact command in your terminal. It will navigate into the folder where your APK lives and spin up a temporary download server:
code
Bash
cd /workspaces/RHC-App/RockHardBlocker/app/build/outputs/apk/bro/debug/ && python3 -m http.server 8080
# Step 2: Make the Port Public
Look at the bottom panel of your Codespace window, find PORTS.
You will see Port 8080 in the list. Right-click it, go to Port Visibility, and select "Public".
Hover over the "Local Address" column for that port. You should see a little "Globe" icon (Open in Browser) or a copy button. Click copy. The URL will look something like https://glowing-space-broccoli-8080.app.github.dev.
# Step 3: Download directly to your Phone
Open your browser on your Android phone.
Paste that URL into the address bar.
You will see a very basic, white webpage with a link that says app-bro-debug.apk. - Tap it.
Your browser will download it as a true application. When it finishes, tap "Open" or go into your phone's native "Files" app, navigate to Downloads, and tap the APK.
Android will recognize it instantly, give you the standard "Do you want to install this app?" prompt, and you'll be in business.
(When you are done, go back to your Codespace terminal, press Ctrl + C to kill the server, and type cd /workspaces/RHC-App/RockHardBlocker to get back to the main folder).




## real emergency kill switch

If your phone is plugged into your computer (or connected via Wireless ADB) to test this, your computer has supreme power over the phone. If you are stuck on the red screen, you just open a local terminal on your computer and type:
code
Bash
adb uninstall com.rockhard.blocker.bro