#include "InstallerUI.h"
#include "ui/UINode.h"
#include "ui/FlexEngine.h"
#include "ui/CSSEngine.h"
#include "ui/Renderer.h"
#include "ui/DoubleBuffer.h"
#include <shlobj.h>
#include <thread>
#include <string>

namespace RHC {
    static UI::UINode* g_installLayout = nullptr;

    LRESULT CALLBACK InstallProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
        switch (uMsg) {
            case WM_PAINT: {
                PAINTSTRUCT ps;
                HDC hdcPaint = BeginPaint(hwnd, &ps);
                RECT rc; GetClientRect(hwnd, &rc);
                {
                    UI::DoubleBuffer buffer(hdcPaint, &rc);
                    HDC hdc = buffer.getDC();

                    // Render custom CSS layout
                    if (g_installLayout) UI::Renderer::DrawNode(hdc, g_installLayout);

                    // Render sleek text
                    HFONT hFontTitle = CreateFontW(22, 0, 0, 0, FW_BOLD, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
                    HGDIOBJ hOldFont = SelectObject(hdc, hFontTitle);
                    SetTextColor(hdc, RGB(255, 255, 255));
                    SetBkMode(hdc, TRANSPARENT);
                    DrawTextW(hdc, L"Installing RHC Momentum Shield...", -1, &rc, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
                    
                    SelectObject(hdc, hOldFont);
                    DeleteObject(hFontTitle);
                }
                EndPaint(hwnd, &ps);
                return 0;
            }
            case WM_NCHITTEST: return HTCAPTION; // Allow dragging the borderless window
            case WM_DESTROY: PostQuitMessage(0); return 0;
        }
        return DefWindowProcW(hwnd, uMsg, wParam, lParam);
    }

    bool InstallerUI::CheckAndInstall(HINSTANCE hInstance) {
        wchar_t exePath[MAX_PATH];
        GetModuleFileNameW(NULL, exePath, MAX_PATH);
        std::wstring currentPath(exePath);

        // Check for Uninstallation Flag
        LPWSTR cmdLine = GetCommandLineW();
        if (wcsstr(cmdLine, L"--uninstall")) {
            RegDeleteKeyW(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\RHC_Shield");
            MessageBoxW(NULL, L"RHC Shield Uninstalled! You may now safely delete this folder.", L"Uninstalled", MB_OK | MB_ICONINFORMATION);
            return true;
        }

        // Determine target install directory
        wchar_t pfPath[MAX_PATH];
        SHGetFolderPathW(NULL, CSIDL_PROGRAM_FILES, NULL, 0, pfPath);
        std::wstring targetFolder = std::wstring(pfPath) + L"\\RHC Shield";
        std::wstring targetExe = targetFolder + L"\\rhc_desktop.exe";

        // If we are already running from the install directory, boot normally!
        if (currentPath == targetExe) {
            return false;
        }

        // ==========================================
        // INSTALLER MODE: Create sleek UI and copy files
        // ==========================================
        CoInitialize(NULL);
        UI::Renderer::Initialize();

        WNDCLASSW wc = {0};
        wc.lpfnWndProc = InstallProc;
        wc.hInstance = hInstance;
        wc.lpszClassName = L"RHC_InstallerUI";
        RegisterClassW(&wc);

        int width = 500;
        int height = 180;
        int x = (GetSystemMetrics(SM_CXSCREEN) - width) / 2;
        int y = (GetSystemMetrics(SM_CYSCREEN) - height) / 2;

        HWND hwndInstall = CreateWindowExW(WS_EX_TOPMOST | WS_EX_TOOLWINDOW, L"RHC_InstallerUI", L"Installing...", WS_POPUP, x, y, width, height, NULL, NULL, hInstance, NULL);

        // Build Custom Flexbox UI
        g_installLayout = new UI::UINode(hwndInstall);
        UI::CSSEngine::ParseInline("background: linear-gradient(#1e1e1e, #121212); border-radius: 12px; border: 2px solid #4CAF50; flex-direction: column;", g_installLayout->style, g_installLayout->layout);
        
        RECT rc = {0, 0, width, height};
        UI::FlexEngine::Arrange(g_installLayout, rc);

        ShowWindow(hwndInstall, SW_SHOW);
        UpdateWindow(hwndInstall);

        // Run the actual install process in a detached thread so the UI can animate/drag
        std::thread([=]() {
            Sleep(1500); // Give the user a moment to see the sleek UI
            
            // 1. Kill old versions silently
            system("taskkill /F /IM rhc_desktop.exe /T > nul 2>&1");
            
            // 2. Create directory and copy self
            CreateDirectoryW(targetFolder.c_str(), NULL);
            
            // Robust Overwrite: Try copying up to 6 times (3 seconds max) waiting for file locks to clear
            int retries = 6;
            while (retries-- > 0) {
                if (CopyFileW(currentPath.c_str(), targetExe.c_str(), FALSE)) {
                    break;
                }
                Sleep(500); // Wait 0.5s for OS to release file handle
            }

            // 3. Write Add/Remove Programs Registry
            HKEY hKey;
            if (RegCreateKeyExW(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\RHC_Shield", 0, NULL, REG_OPTION_NON_VOLATILE, KEY_WRITE, NULL, &hKey, NULL) == ERROR_SUCCESS) {
                RegSetValueExW(hKey, L"DisplayName", 0, REG_SZ, (const BYTE*)L"RHC Momentum Shield", 40);
                RegSetValueExW(hKey, L"Publisher", 0, REG_SZ, (const BYTE*)L"Rock Hard Blocker", 36);
                RegSetValueExW(hKey, L"DisplayIcon", 0, REG_SZ, (const BYTE*)targetExe.c_str(), (targetExe.length() + 1) * 2);
                std::wstring uninstallStr = targetExe + L" --uninstall";
                RegSetValueExW(hKey, L"UninstallString", 0, REG_SZ, (const BYTE*)uninstallStr.c_str(), (uninstallStr.length() + 1) * 2);
                RegCloseKey(hKey);
            }

            // 4. Create Desktop Shortcut via PowerShell (Cleanest way in C++)
            std::wstring psCommand = L"powershell \"$s=(New-Object -COM WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop')+'\\RHC Momentum Shield.lnk');$s.TargetPath='" + targetExe + L"';$s.Save()\"";
            _wsystem(psCommand.c_str());

            // 5. Launch the newly installed file and close the installer
            ShellExecuteW(NULL, L"open", targetExe.c_str(), NULL, NULL, SW_SHOW);
            PostMessageW(hwndInstall, WM_CLOSE, 0, 0);
        }).detach();

        // Local message loop just for the installer
        MSG msg = {0};
        while (GetMessage(&msg, NULL, 0, 0)) {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }

        if (g_installLayout) delete g_installLayout;
        UI::Renderer::Shutdown();
        CoUninitialize();
        return true;
    }
}
