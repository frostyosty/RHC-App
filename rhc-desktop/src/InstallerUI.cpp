#include "InstallerUI.h"

namespace RHC {
    bool InstallerUI::CheckAndInstall(HINSTANCE hInstance) {
        // REMOVED: this used to silently copy the running exe into Program
        // Files, taskkill any existing instance, write Add/Remove Programs
        // registry entries, and create a shortcut via a spawned PowerShell
        // command - all from a disguised borderless "Installing..." window,
        // with no user consent screen. That self-installing-dropper pattern
        // is what got rhc_desktop.exe flagged as Trojan:Win32/Bearfoos.A!ml
        // by Defender. Installation is handled exclusively by the signed,
        // consent-based NSIS installer (installer.nsi) now.
        return false;
    }
}
