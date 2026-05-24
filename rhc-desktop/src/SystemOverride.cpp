#include "SystemOverride.h"
#include "Globals.h"
#include "HostsBlocker.h"
#include "include/DatabaseManager.h"
#include "include/StringUtils.h"
#include <chrono>
#include <iomanip>
#include <sstream>
#include <wininet.h>

namespace RHC {
    namespace SystemOverride {
        HWND hwnd = NULL; HWND hCombo = NULL; HWND hStatusText = NULL; HWND hBtnPause = NULL; HWND hBtnUninstall = NULL;

        long long GetNetworkTimeMs() {
            HMODULE hWinInet = LoadLibraryA("wininet.dll");
            if (!hWinInet) return 0;
            typedef PVOID(WINAPI *IOpenA)(LPCSTR, DWORD, LPCSTR, LPCSTR, DWORD);
            typedef PVOID(WINAPI *IOpenUrlA)(PVOID, LPCSTR, LPCSTR, DWORD, DWORD, PVOID);
            typedef BOOL(WINAPI *IQueryInfoA)(PVOID, DWORD, LPVOID, LPDWORD, LPDWORD);
            typedef BOOL(WINAPI *IClose)(PVOID);

            IOpenA pInternetOpenA = (IOpenA)GetProcAddress(hWinInet, "InternetOpenA");
            IOpenUrlA pInternetOpenUrlA = (IOpenUrlA)GetProcAddress(hWinInet, "InternetOpenUrlA");
            IQueryInfoA pHttpQueryInfoA = (IQueryInfoA)GetProcAddress(hWinInet, "HttpQueryInfoA");
            IClose pInternetCloseHandle = (IClose)GetProcAddress(hWinInet, "InternetCloseHandle");

            long long netTimeMs = 0;
            if (pInternetOpenA && pInternetOpenUrlA && pHttpQueryInfoA && pInternetCloseHandle) {
                PVOID hInt = pInternetOpenA("RHC_Agent", 1, NULL, NULL, 0);
                if (hInt) {
                    PVOID hUrl = pInternetOpenUrlA(hInt, "https://google.com", NULL, 0, 0x80000000 | 0x04000000, 0);
                    if (hUrl) {
                        SYSTEMTIME st; DWORD size = sizeof(st); DWORD index = 0;
                        if (pHttpQueryInfoA(hUrl, 0x40000015, &st, &size, &index)) {
                            FILETIME ft; SystemTimeToFileTime(&st, &ft);
                            ULARGE_INTEGER ull; ull.LowPart = ft.dwLowDateTime; ull.HighPart = ft.dwHighDateTime;
                            netTimeMs = (ull.QuadPart - 116444736000000000ULL) / 10000ULL;
                        }
                        pInternetCloseHandle(hUrl);
                    }
                    pInternetCloseHandle(hInt);
                }
            }
            FreeLibrary(hWinInet);
            return netTimeMs;
        }

        long long GetTimeMs() {
            long long localTime = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
            long long netTime = GetNetworkTimeMs();
            long long currentTime = (netTime > 1600000000000LL) ? netTime : localTime;

            RHC::DatabaseManager db("rhc_state.db");
            long long lastKnown = std::stoll(db.getString("LAST_KNOWN_TIME", "0"));

            if (lastKnown == 0) {
                db.putString("LAST_KNOWN_TIME", std::to_string(currentTime));
                return currentTime;
            }

            if (currentTime < lastKnown) {
                currentTime = lastKnown + 1000; 
            } else if (netTime == 0 && (currentTime - lastKnown) > (24LL * 3600LL * 1000LL)) {
                currentTime = lastKnown + (24LL * 3600LL * 1000LL); 
            }

            db.putString("LAST_KNOWN_TIME", std::to_string(currentTime));
            return currentTime;
        }

        void UpdateUI() {
            RHC::DatabaseManager db("rhc_state.db");
            long long unlockTime = std::stoll(db.getString("OVERRIDE_UNLOCK_TIME", "0"));
            std::string type = db.getString("OVERRIDE_TYPE", "");
            long long now = GetTimeMs();

            if (unlockTime > now) {
                time_t timeT = unlockTime / 1000; std::stringstream ss; ss << std::put_time(std::localtime(&timeT), "%b %d, %I:%M %p");
                std::string status = "Status: " + type + " unlocks on " + ss.str();
                SetWindowTextA(hStatusText, status.c_str());
                EnableWindow(hBtnPause, FALSE); EnableWindow(hBtnUninstall, FALSE); EnableWindow(hCombo, FALSE);
            } else if (unlockTime > 0 && now >= unlockTime) {
                SetWindowTextA(hStatusText, ("SYSTEM UNLOCKED! Ready to execute " + type + ".").c_str());
                EnableWindow(hCombo, FALSE);
                if (type == "PAUSE") { SetWindowTextA(hBtnPause, "EXECUTE PAUSE"); EnableWindow(hBtnPause, TRUE); EnableWindow(hBtnUninstall, FALSE); }
                else { SetWindowTextA(hBtnUninstall, "EXECUTE UNINSTALL"); EnableWindow(hBtnUninstall, TRUE); EnableWindow(hBtnPause, FALSE); }
            } else {
                SetWindowTextA(hStatusText, "Select a delay. Once scheduled, it cannot be reduced.");
                EnableWindow(hBtnPause, TRUE); EnableWindow(hBtnUninstall, TRUE); EnableWindow(hCombo, TRUE);
            }
        }

        void ExitCleanly() {
            if (g_hKeyboardHook) UnhookWindowsHookEx(g_hKeyboardHook);
            Shell_NotifyIconW(NIM_DELETE, &nid);
            ExitProcess(0);
        }

        void ExecuteUninstall() {
            RHC::HostsBlocker::SyncHostsFile(std::vector<std::string>());
            HKEY hKey;
            if (RegOpenKeyExA(HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Run", 0, KEY_WRITE, &hKey) == ERROR_SUCCESS) {
                RegDeleteValueA(hKey, "RHC_Core"); RegDeleteValueA(hKey, "Sync_Service_Host"); RegCloseKey(hKey);
            }
            
            char path[MAX_PATH]; GetModuleFileNameA(NULL, path, MAX_PATH);
            std::string exePath(path);
            
            // Get directory containing the executable to resolve the absolute db path
            size_t pos = exePath.find_last_of("\\/");
            std::string dir = (pos != std::string::npos) ? exePath.substr(0, pos + 1) : "";
            std::string dbPath = dir + "rhc_state.db";
            
            // Shell command to delete both database and exe after process termination
            std::string cmd = "/c ping 127.0.0.1 -n 3 > nul & del \"" + dbPath + "\" & del \"" + exePath + "\"";
            ShellExecuteA(NULL, "open", "cmd.exe", cmd.c_str(), NULL, SW_HIDE); ExitCleanly();
        }

        void ScheduleOrExecute(const std::string& type) {
            RHC::DatabaseManager db("rhc_state.db");
            long long unlockTime = std::stoll(db.getString("OVERRIDE_UNLOCK_TIME", "0"));
            long long now = GetTimeMs();

            if (unlockTime > 0 && now >= unlockTime) {
                if (type == "UNINSTALL") ExecuteUninstall();
                if (type == "PAUSE") { db.putString("OVERRIDE_IS_PAUSED", "TRUE"); MessageBoxA(hwnd, "System is now PAUSED. Restart app to resume.", "Paused", MB_OK | MB_ICONINFORMATION); ExitCleanly(); }
            } else {
                int sel = SendMessage(hCombo, CB_GETCURSEL, 0, 0); int days = 3;
                if (sel == 1) days = 5; else if (sel == 2) days = 7; else if (sel == 3) days = 14;
                int minDays = db.getInt("OVERRIDE_MIN_DAYS", 0);
                if (days < minDays) { MessageBoxA(hwnd, "You cannot decrease a previously selected delay!", "Error", MB_ICONERROR); return; }
                long long newUnlock = now + (days * 24LL * 60LL * 60LL * 1000LL);
                db.putString("OVERRIDE_UNLOCK_TIME", std::to_string(newUnlock)); db.putString("OVERRIDE_TYPE", type); db.putInt("OVERRIDE_MIN_DAYS", days);
                UpdateUI();
            }
        }

        LRESULT CALLBACK WndProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
            switch (uMsg) {
                case WM_CREATE: {
                    HFONT hFont = CreateFontA(16, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, "Segoe UI");
                    CreateWindowExA(0, "STATIC", "SYSTEM OVERRIDE", WS_CHILD | WS_VISIBLE | SS_CENTER, 0, 20, 400, 30, hwnd, NULL, NULL, NULL);
                    hStatusText = CreateWindowExA(0, "STATIC", "", WS_CHILD | WS_VISIBLE | SS_CENTER, 20, 60, 360, 40, hwnd, NULL, NULL, NULL);
                    hCombo = CreateWindowExA(0, "COMBOBOX", "", CBS_DROPDOWNLIST | WS_CHILD | WS_VISIBLE, 100, 110, 200, 200, hwnd, NULL, NULL, NULL);
                    SendMessageA(hCombo, CB_ADDSTRING, 0, (LPARAM)"3 Days"); SendMessageA(hCombo, CB_ADDSTRING, 0, (LPARAM)"5 Days"); SendMessageA(hCombo, CB_ADDSTRING, 0, (LPARAM)"1 Week"); SendMessageA(hCombo, CB_ADDSTRING, 0, (LPARAM)"2 Weeks");
                    SendMessageA(hCombo, CB_SETCURSEL, 0, 0);
                    hBtnPause = CreateWindowExA(0, "BUTTON", "Sched Pause", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 50, 160, 140, 40, hwnd, (HMENU)1, NULL, NULL);
                    hBtnUninstall = CreateWindowExA(0, "BUTTON", "Sched Uninstall", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 210, 160, 140, 40, hwnd, (HMENU)2, NULL, NULL);
                    SendMessage(hStatusText, WM_SETFONT, (WPARAM)hFont, TRUE); SendMessage(hCombo, WM_SETFONT, (WPARAM)hFont, TRUE); SendMessage(hBtnPause, WM_SETFONT, (WPARAM)hFont, TRUE); SendMessage(hBtnUninstall, WM_SETFONT, (WPARAM)hFont, TRUE);
                    UpdateUI(); return 0;
                }
                case WM_COMMAND: if (LOWORD(wParam) == 1) ScheduleOrExecute("PAUSE"); if (LOWORD(wParam) == 2) ScheduleOrExecute("UNINSTALL"); return 0;
                case WM_CTLCOLORSTATIC: SetTextColor((HDC)wParam, RGB(255, 255, 255)); SetBkColor((HDC)wParam, RGB(18, 18, 18)); return (LRESULT)CreateSolidBrush(RGB(18, 18, 18));
                case WM_CLOSE: ShowWindow(hwnd, SW_HIDE); return 0;
            }
            return DefWindowProcA(hwnd, uMsg, wParam, lParam);
        }

        void Initialize(HINSTANCE hInstance) {
            WNDCLASSA wc = {0}; wc.lpfnWndProc = WndProc; wc.hInstance = hInstance; wc.lpszClassName = "RHC_Override"; wc.hbrBackground = CreateSolidBrush(RGB(18, 18, 18)); RegisterClassA(&wc);
            hwnd = CreateWindowExA(0, "RHC_Override", "System Override", WS_OVERLAPPEDWINDOW ^ WS_THICKFRAME ^ WS_MAXIMIZEBOX, CW_USEDEFAULT, CW_USEDEFAULT, 400, 260, NULL, NULL, hInstance, NULL);
        }
        void Show() { UpdateUI(); ShowWindow(hwnd, SW_RESTORE); SetForegroundWindow(hwnd); }
        HWND GetWindowHandle() { return hwnd; }
    }
}
