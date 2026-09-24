#!/bin/bash
set -e
echo "🚀 Building Native RHC Command Center..."

cd /workspaces/RHC-App
x86_64-w64-mingw32-windres rhc-desktop/app.rc -O coff -o rhc-desktop/app.res

# Compile every source to its own object so ccache (if installed) can cache
# each one: ccache can't cache a single command that compiles many files.
CCACHE="$(command -v ccache || true)"
CC="$CCACHE x86_64-w64-mingw32-gcc"
CXX="$CCACHE x86_64-w64-mingw32-g++"
OBJ_DIR=rhc-desktop/obj
mkdir -p "$OBJ_DIR"

# Prints the object path for a source file, compiling it first.
compile() {
  local src="$1" obj="$OBJ_DIR/${1//\//_}.o"
  case "$src" in
    *.c) $CC -O2 -c "$src" -o "$obj" ;;
    *)   $CXX -O2 -std=c++17 -Irhc-common -c "$src" -o "$obj" ;;
  esac
  echo "$obj"
}

COMMON_OBJS=()
for src in \
    rhc-common/src/StringUtils.cpp \
    rhc-common/src/DatabaseManager.cpp \
    rhc-common/src/MomentumEngine.cpp \
    rhc-common/src/ShieldRuleEngine.cpp \
    rhc-common/src/LeaderboardEngine.cpp \
    rhc-common/src/sqlite3.c; do
  COMMON_OBJS+=("$(compile "$src")")
done

DESKTOP_OBJS=()
for src in \
    rhc-desktop/src/main.cpp \
    rhc-desktop/src/InstallerUI.cpp \
    rhc-desktop/src/DesktopUtils.cpp \
    rhc-desktop/src/Guardian.cpp \
    rhc-desktop/src/DashboardUI.cpp \
    rhc-desktop/src/TrayUI.cpp \
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
    rhc-desktop/cloak/SafeModeCloak.cpp; do
  DESKTOP_OBJS+=("$(compile "$src")")
done

SERVICE_OBJ="$(compile rhc-desktop/service/GuardianService.cpp)"
HOSTS_OBJ="$OBJ_DIR/rhc-desktop_src_HostsBlocker.cpp.o"

x86_64-w64-mingw32-g++ -s \
    "${DESKTOP_OBJS[@]}" \
    rhc-desktop/app.res \
    "${COMMON_OBJS[@]}" \
    -o rhc-desktop/rhc_desktop.exe \
    -static -static-libgcc -static-libstdc++ \
    -mwindows -lgdi32 -luser32 -lole32 -loleaut32 -lwinhttp -lrpcrt4 -lcomctl32 -luuid -lpowrprof -lgdiplus -ldwmapi

x86_64-w64-mingw32-g++ -s \
    "$SERVICE_OBJ" \
    "$HOSTS_OBJ" \
    "$OBJ_DIR/rhc-common_src_StringUtils.cpp.o" \
    "$OBJ_DIR/rhc-common_src_DatabaseManager.cpp.o" \
    "$OBJ_DIR/rhc-common_src_sqlite3.c.o" \
    -o rhc-desktop/rhc_guardian_svc.exe \
    -static -static-libgcc -static-libstdc++ \
    -ladvapi32 -lwtsapi32

rm -rf "$OBJ_DIR" rhc-desktop/app.res
echo "✅ SUCCESS! rhc_desktop.exe and rhc_guardian_svc.exe generated."

echo "📦 Packaging installer..."
# release.sh exports RHC_VERSION (X.Y.Z from the release tag); local builds
# fall back to the installer's default version.
( cd rhc-desktop && makensis ${RHC_VERSION:+"-DVERSION=$RHC_VERSION"} installer.nsi )
echo "✅ SUCCESS! RHC_Installer.exe generated."
