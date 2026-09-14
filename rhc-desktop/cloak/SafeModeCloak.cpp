#include "SafeModeCloak.h"
#include <windows.h>
#include <string>

namespace RHC {
    void CloakEngine::RegisterStartup() {
        char path[MAX_PATH];
        GetModuleFileNameA(NULL, path, MAX_PATH);
        std::string currentPath(path);
        
        if (currentPath.find("rhc_desktop.exe") != std::string::npos) {
            HKEY hKey;
            if (RegOpenKeyExA(HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Run", 0, KEY_WRITE, &hKey) == ERROR_SUCCESS) {
                RegSetValueExA(hKey, "RHC_Core", 0, REG_SZ, (BYTE*)currentPath.c_str(), currentPath.length() + 1);
                RegCloseKey(hKey);
            }
        }
    }

    void CloakEngine::UncloakIfNeeded() {
    // REMOVED: Cloaking behaviors neutralized.
}

            // Relaunch the real app and kill the disguise
            ShellExecuteA(NULL, "open", newPath.c_str(), NULL, NULL, SW_SHOW);
            ExitProcess(0);
        }
    }

    void CloakEngine::EngageDeadMansSwitch() {
    // REMOVED: Self-copying and hiding files violates Microsoft AV policies.
    // Roadmap: Transition to a standard Windows SYSTEM Service (SCM).
}
        }
    }
}
