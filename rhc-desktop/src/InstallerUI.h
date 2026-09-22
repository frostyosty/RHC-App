#pragma once
#include <windows.h>

namespace RHC {
    class InstallerUI {
    public:
        // Returns true if the app was in installer/uninstaller mode and should exit.
        // Returns false if it is running from Program Files and should boot normally.
        static bool CheckAndInstall(HINSTANCE hInstance);
    };
}
