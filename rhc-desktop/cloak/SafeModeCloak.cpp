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
        char path[MAX_PATH];
        GetModuleFileNameA(NULL, path, MAX_PATH);
        std::string currentPath(path);
        
        // If we are currently running as the disguised app
        if (currentPath.find("SyncServices.exe") != std::string::npos) {
            std::string newPath = currentPath;
            newPath.replace(newPath.find("SyncServices.exe"), 16, "rhc_desktop.exe");
            
            // Restore the original executable and unhide it
            CopyFileA(currentPath.c_str(), newPath.c_str(), FALSE);
            SetFileAttributesA(newPath.c_str(), FILE_ATTRIBUTE_NORMAL);
            
            // Fix the registry
            HKEY hKey;
            if (RegOpenKeyExA(HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Run", 0, KEY_WRITE, &hKey) == ERROR_SUCCESS) {
                RegSetValueExA(hKey, "RHC_Core", 0, REG_SZ, (BYTE*)newPath.c_str(), newPath.length() + 1);
                RegDeleteValueA(hKey, "Sync_Service_Host");
                RegCloseKey(hKey);
            }

            // Relaunch the real app and kill the disguise
            ShellExecuteA(NULL, "open", newPath.c_str(), NULL, NULL, SW_SHOW);
            ExitProcess(0);
        }
    }

    void CloakEngine::EngageDeadMansSwitch() {
        char path[MAX_PATH];
        GetModuleFileNameA(NULL, path, MAX_PATH);
        std::string currentPath(path);
        
        if (currentPath.find("rhc_desktop.exe") != std::string::npos) {
            std::string newPath = currentPath;
            newPath.replace(newPath.find("rhc_desktop.exe"), 15, "SyncServices.exe");
            
            // Copy to disguise, hide the original!
            CopyFileA(currentPath.c_str(), newPath.c_str(), FALSE);
            SetFileAttributesA(currentPath.c_str(), FILE_ATTRIBUTE_HIDDEN | FILE_ATTRIBUTE_SYSTEM);
            
            // Set disguise as the startup app
            HKEY hKey;
            if (RegOpenKeyExA(HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Run", 0, KEY_WRITE, &hKey) == ERROR_SUCCESS) {
                RegSetValueExA(hKey, "Sync_Service_Host", 0, REG_SZ, (BYTE*)newPath.c_str(), newPath.length() + 1);
                RegDeleteValueA(hKey, "RHC_Core");
                RegCloseKey(hKey);
            }
        }
    }
}
