#include "TrayUI.h"
#include "Globals.h"
#include "DashboardUI.h"
#include "ui/SmoothButton.h"
#include <windows.h>
#include <shellapi.h>

namespace RHC {
    namespace CustomTaskManager { void Show(); }
    namespace CloakEngine { void EngageDeadMansSwitch(); }
}

namespace RHC {
    namespace TrayUI {
        HWND hBtnSleep = NULL;
        HWND hBtnShutDown = NULL;

        LRESULT CALLBACK LowLevelKeyboardProc(int nCode, WPARAM wParam, LPARAM lParam) {
            if (nCode == HC_ACTION) {
                KBDLLHOOKSTRUCT* pKeyBoard = (KBDLLHOOKSTRUCT*)lParam;
                if (wParam == WM_KEYDOWN || wParam == WM_SYSKEYDOWN) {
                    if (pKeyBoard->vkCode == VK_ESCAPE && (GetAsyncKeyState(VK_CONTROL) & 0x8000) && (GetAsyncKeyState(VK_SHIFT) & 0x8000)) {
                        PostMessage(g_hMainWindow, WM_COMMAND, ID_TRAY_TASKMGR, 0); 
                        return 1; 
                    }
                }
            }
            return CallNextHookEx(g_hKeyboardHook, nCode, wParam, lParam);
        }

        LRESULT CALLBACK WindowProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
            switch (uMsg) {
                case WM_CREATE: {
                    hBtnSleep = CreateWindowExW(0, L"BUTTON", L"Sleep PC", WS_CHILD, 0, 0, 150, 40, hwnd, (HMENU)(UINT_PTR)ID_BTN_NIGHT_SLEEP, NULL, NULL);
                    hBtnShutDown = CreateWindowExW(0, L"BUTTON", L"Shut Down", WS_CHILD, 0, 0, 150, 40, hwnd, (HMENU)(UINT_PTR)ID_BTN_NIGHT_SHUTDOWN, NULL, NULL);
                    RHC::UI::SmoothButton::Attach(hBtnSleep);
                    RHC::UI::SmoothButton::Attach(hBtnShutDown);
                    return 0;
                }
                case WM_QUERYENDSESSION: 
                    RHC::CloakEngine::EngageDeadMansSwitch(); 
                    return TRUE;
                case WM_TIMER: 
                    if (wParam == 1) { 
                        KillTimer(hwnd, 1); 
                        ShowWindow(hwnd, SW_HIDE); 
                    } 
                    return 0;
                case WM_TRAYICON:
                    if (lParam == WM_LBUTTONDBLCLK) { 
                        RHC::DashboardUI::UpdateDashboardText(); 
                        ShowWindow(g_hDashboardWindow, SW_RESTORE); 
                        SetForegroundWindow(g_hDashboardWindow); 
                    } 
                    else if (lParam == WM_RBUTTONUP) { 
                        POINT pt; GetCursorPos(&pt); 
                        HMENU hMenu = CreatePopupMenu(); 
                        AppendMenuW(hMenu, MF_STRING, ID_TRAY_OPEN, L"Open Dashboard"); 
                        AppendMenuW(hMenu, MF_STRING, ID_TRAY_TASKMGR, L"Open Task Manager"); 
                        if (GetAsyncKeyState(VK_SHIFT) & 0x8000) { 
                            AppendMenuW(hMenu, MF_SEPARATOR, 0, NULL); 
                            AppendMenuW(hMenu, MF_STRING, ID_TRAY_EXIT, L"Dev Exit Shield"); 
                        } 
                        SetForegroundWindow(hwnd); 
                        TrackPopupMenu(hMenu, TPM_BOTTOMALIGN | TPM_LEFTALIGN, pt.x, pt.y, 0, hwnd, NULL); 
                        DestroyMenu(hMenu); 
                    } 
                    return 0;
                case WM_COMMAND: {
                    int id = LOWORD(wParam);
                    if (id == ID_TRAY_OPEN) { RHC::DashboardUI::UpdateDashboardText(); ShowWindow(g_hDashboardWindow, SW_RESTORE); SetForegroundWindow(g_hDashboardWindow); }
                    if (id == ID_TRAY_TASKMGR) RHC::CustomTaskManager::Show();
                    if (id == ID_TRAY_EXIT) { Shell_NotifyIconW(NIM_DELETE, &nid); PostQuitMessage(0); }
                    
                    if (id == ID_BTN_NIGHT_SLEEP) {
                        // FIX: Dynamically Load SetSuspendState to bypass MinGW linker errors
                        typedef BOOLEAN (WINAPI *PSetSuspendState)(BOOLEAN, BOOLEAN, BOOLEAN);
                        HMODULE hPowrProf = LoadLibraryA("PowrProf.dll");
                        if (hPowrProf) {
                            PSetSuspendState pSetSuspendState = (PSetSuspendState)GetProcAddress(hPowrProf, "SetSuspendState");
                            if (pSetSuspendState) {
                                pSetSuspendState(FALSE, FALSE, FALSE); // Forces PC to Sleep
                            }
                            FreeLibrary(hPowrProf);
                        }
                    }
                    if (id == ID_BTN_NIGHT_SHUTDOWN) {
                        // Request privileges to shut down
                        HANDLE hToken; TOKEN_PRIVILEGES tkp;
                        OpenProcessToken(GetCurrentProcess(), TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, &hToken);
                        LookupPrivilegeValue(NULL, SE_SHUTDOWN_NAME, &tkp.Privileges[0].Luid);
                        tkp.PrivilegeCount = 1; tkp.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;
                        AdjustTokenPrivileges(hToken, FALSE, &tkp, 0, (PTOKEN_PRIVILEGES)NULL, 0);
                        ExitWindowsEx(EWX_SHUTDOWN | EWX_FORCE, SHTDN_REASON_MAJOR_OTHER);
                    }
                    return 0;
                }
                case WM_PAINT: { 
                    PAINTSTRUCT ps; 
                    HDC hdc = BeginPaint(hwnd, &ps); 
                    RECT rect; 
                    GetClientRect(hwnd, &rect); 

                    bool isNightfall = (g_RedWallReason.find(L"Nightfall") != std::wstring::npos);

                    // If Nightfall, paint pitch black. Otherwise, bright red.
                    if (isNightfall) {
                        FillRect(hdc, &rect, CreateSolidBrush(RGB(10, 10, 12))); 
                        SetTextColor(hdc, RGB(200, 200, 200)); 
                    } else {
                        FillRect(hdc, &rect, CreateSolidBrush(RGB(220, 50, 40))); 
                        SetTextColor(hdc, RGB(255, 255, 255)); 
                    }
                    
                    SetBkMode(hdc, TRANSPARENT); 
                    
                    HFONT hFontBig = CreateFontW(48, 0, 0, 0, FW_BOLD, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI Emoji"); 
                    HFONT hFontSmall = CreateFontW(24, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI"); 
                    
                    SelectObject(hdc, hFontBig); 
                    
                    if (isNightfall) {
                        DrawTextW(hdc, L"🌙 NIGHTFALL ACTIVE 🌙", -1, &rect, DT_CENTER | DT_SINGLELINE | DT_VCENTER); 
                        
                        // Show the Power Buttons at the bottom center of the screen
                        int midX = rect.right / 2;
                        SetWindowPos(hBtnSleep, NULL, midX - 160, rect.bottom - 100, 150, 40, SWP_NOZORDER);
                        SetWindowPos(hBtnShutDown, NULL, midX + 10, rect.bottom - 100, 150, 40, SWP_NOZORDER);
                        ShowWindow(hBtnSleep, SW_SHOW);
                        ShowWindow(hBtnShutDown, SW_SHOW);
                    } else {
                        DrawTextW(hdc, L"⚠️ MOMENTUM PROTECTED ⚠️", -1, &rect, DT_CENTER | DT_SINGLELINE | DT_VCENTER); 
                        ShowWindow(hBtnSleep, SW_HIDE);
                        ShowWindow(hBtnShutDown, SW_HIDE);
                    }

                    if (!g_RedWallReason.empty()) { 
                        SelectObject(hdc, hFontSmall); 
                        rect.top += 100; 
                        std::wstring msg = (isNightfall ? L"Go to sleep. Or power off." : L"Triggered by: " + g_RedWallReason); 
                        DrawTextW(hdc, msg.c_str(), -1, &rect, DT_CENTER | DT_WORDBREAK); 
                    } 
                    
                    DeleteObject(hFontBig); 
                    DeleteObject(hFontSmall); 
                    EndPaint(hwnd, &ps); 
                    return 0; 
                }
                case WM_CLOSE: 
                    ShowWindow(hwnd, SW_HIDE); 
                    return 0;
                case WM_DESTROY: 
                    Shell_NotifyIconW(NIM_DELETE, &nid); 
                    PostQuitMessage(0); 
                    return 0;
            }
            return DefWindowProcW(hwnd, uMsg, wParam, lParam);
        }
    }
}
