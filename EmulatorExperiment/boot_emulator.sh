#!/bin/bash
echo "====================================================="
echo " INIT: SOFTWARE EMULATION MODE (KVM BYPASS ENGAGED) "
echo "====================================================="
echo "Booting virtual device in background..."

# Boot the zombie phone
$ANDROID_HOME/emulator/emulator -avd NetbeastPhone -no-window -accel off -gpu swiftshader_indirect -no-audio -no-boot-anim -memory 2048 &

echo "Waiting for Android OS to wake up... (This takes 3-10 minutes on pure software math!)"
# This command halts the script until the phone's brain turns on
adb wait-for-device

echo "Device Awake! Waiting for package manager to stabilize..."
sleep 30

echo "====================================================="
echo " LOGCAT ATTACHED. WAITING FOR APP DATA... "
echo "(Press Ctrl+C to stop watching)"
echo "====================================================="

# Watch the logs, but ONLY show logs related to our app or the Game Engine!
adb logcat | grep -iE "rockhard|ActivityManager|AndroidRuntime"
