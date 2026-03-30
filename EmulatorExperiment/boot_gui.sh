#!/bin/bash

# 1. Kill old processes
killall emulator Xvfb fluxbox x11vnc websockify 2>/dev/null
sleep 1

# 2. Start the lightweight screen
echo "> Starting Invisible Monitor & Web Server..."
Xvfb :0 -screen 0 480x800x24 &
sleep 1
DISPLAY=:0 fluxbox &
x11vnc -display :0 -bg -nopw -listen localhost -xkb 2>/dev/null
websockify --web /usr/share/novnc/ 6080 localhost:5900 &

# 3. Boot the Emulator with EXTREME constraints (-memory 1024 -cores 1 -no-snapshot)
echo "> Booting Ultra-Lite Emulator..."
DISPLAY=:0 $ANDROID_HOME/emulator/emulator -avd LitePhone -skin 480x800 -gpu swiftshader_indirect -no-audio -no-boot-anim -memory 1024 -cores 1 -no-snapshot &

echo "====================================================="
echo " DONE! Open Port 6080 to see your Burner Phone."
echo " You can literally watch it boot up in the browser."
echo "====================================================="

# 4. Put the boot-wait loop in the background!
(
    adb wait-for-device
    echo "> Device detected. Waiting for OS to initialize..."
    while[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
        sleep 5
    done
    echo ""
    echo ">>> ANDROID IS FULLY AWAKE! STRIPPING ANIMATIONS NOW... <<<"
    adb shell settings put global window_animation_scale 0
    adb shell settings put global transition_animation_scale 0
    adb shell settings put global animator_duration_scale 0
) &
