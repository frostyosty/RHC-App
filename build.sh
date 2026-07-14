#!/bin/bash
set -e
echo "🚀 Building Native RHC Command Center..."

cd /workspaces/RHC-App
x86_64-w64-mingw32-windres rhc-desktop/app.rc -O coff -o rhc-desktop/app.res

x86_64-w64-mingw32-gcc -O2 -c rhc-common/src/sqlite3.c -o sqlite3.o
x86_64-w64-mingw32-g++ -O2 -std=c++17 -Irhc-common -c rhc-common/src/StringUtils.cpp -o StringUtils.o
x86_64-w64-mingw32-g++ -O2 -std=c++17 -Irhc-common -c rhc-common/src/DatabaseManager.cpp -o DatabaseManager.o
x86_64-w64-mingw32-g++ -O2 -std=c++17 -Irhc-common -c rhc-common/src/MomentumEngine.cpp -o MomentumEngine.o
x86_64-w64-mingw32-g++ -O2 -std=c++17 -Irhc-common -c rhc-common/src/ShieldRuleEngine.cpp -o ShieldRuleEngine.o
x86_64-w64-mingw32-g++ -O2 -std=c++17 -Irhc-common -c rhc-common/src/LeaderboardEngine.cpp -o LeaderboardEngine.o

x86_64-w64-mingw32-g++ -O2 -std=c++17 -s \
    -Irhc-common \
    rhc-desktop/src/main.cpp \
    rhc-desktop/src/DesktopUtils.cpp \
    rhc-desktop/src/Guardian.cpp \
    rhc-desktop/src/DashboardUI.cpp \
    rhc-desktop/src/TrayUI.cpp \
    rhc-desktop/src/CustomTaskManager.cpp \
    rhc-desktop/src/SystemOverride.cpp \
    rhc-desktop/src/NightfallUI.cpp \
    rhc-desktop/src/CrashReporter.cpp \
    rhc-desktop/src/UIAScanner.cpp \
    rhc-desktop/src/HostsBlocker.cpp \
    rhc-desktop/src/ui/SmoothButton.cpp \
    rhc-desktop/src/ui/FlexEngine.cpp \
    rhc-desktop/src/ui/CSSEngine.cpp \
    rhc-desktop/src/ui/Renderer.cpp \
    rhc-desktop/src/ui/CustomModal.cpp \
    rhc-desktop/cloak/SafeModeCloak.cpp \
    rhc-desktop/app.res \
    StringUtils.o DatabaseManager.o MomentumEngine.o ShieldRuleEngine.o LeaderboardEngine.o sqlite3.o \
    -o rhc-desktop/rhc_desktop.exe \
    -static -static-libgcc -static-libstdc++ \
    -mwindows -lgdi32 -luser32 -lole32 -loleaut32 -lwinhttp -lrpcrt4 -lcomctl32 -luuid -lpowrprof -lgdiplus -ldwmapi

rm *.o rhc-desktop/app.res
echo "✅ SUCCESS! rhc_desktop.exe generated."
