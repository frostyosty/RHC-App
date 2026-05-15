#include "CustomTaskManager.h"
#include "DashboardUI.h"
#include "DesktopUtils.h"
#include "include/DatabaseManager.h"
#include "include/StringUtils.h"
#include <tlhelp32.h>
#include <psapi.h>
#include <commctrl.h>
#include <string>
#include <vector>

namespace RHC {
    namespace CustomTaskManager {
        HWND hwnd = NULL;
        HWND hListView = NULL;

        void RefreshList() {
            SendMessageW(hListView, LVM_DELETEALLITEMS, 0, 0);
            HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
            if (hSnap != INVALID_HANDLE_VALUE) {
                PROCESSENTRY32W pe32;
                pe32.dwSize = sizeof(PROCESSENTRY32W);
                int rowIndex = 0;
                if (Process32FirstW(hSnap, &pe32)) {
                    do {
                        std::wstring exeName = pe32.szExeFile;
                        std::wstring fullPath = L"";
                        std::wstring memStr = L"N/A";
                        
                        HANDLE hProc = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, pe32.th32ProcessID);
                        if (hProc) {
                            wchar_t pathBuf[MAX_PATH]; DWORD size = MAX_PATH;
                            if (QueryFullProcessImageNameW(hProc, 0, pathBuf, &size)) fullPath = pathBuf;

                            PROCESS_MEMORY_COUNTERS pmc;
                            if (GetProcessMemoryInfo(hProc, &pmc, sizeof(pmc))) memStr = std::to_wstring(pmc.WorkingSetSize / 1024) + L" K";
                            CloseHandle(hProc);
                        }

                        // FIX: Check if an app is disguised as a system process
                        std::string lowerExe = RHC::StringUtils::toLower(RHC::Utils::wstring_to_utf8(exeName));
                        std::string lowerPath = RHC::StringUtils::toLower(RHC::Utils::wstring_to_utf8(fullPath));
                        
                        bool isFakeSystemProcess = false;
                        if (lowerExe == "svchost.exe" || lowerExe == "conhost.exe" || lowerExe == "explorer.exe") {
                            if (!lowerPath.empty() && lowerPath.find("\\windows\\") == std::string::npos) {
                                isFakeSystemProcess = true; // Caught trying to hide outside C:\Windows!
                            }
                        }

                        if (!isFakeSystemProcess && (lowerExe.find("rhc_desktop") != std::string::npos || 
                            lowerExe.find("syncservices") != std::string::npos ||
                            lowerExe.find("svchost") != std::string::npos ||
                            lowerExe.find("conhost") != std::string::npos)) {
                            continue;
                        }

                        std::wstring displayName = isFakeSystemProcess ? L"⚠️ FAKE: " + exeName : exeName;

                        LVITEMW lvi = {0}; lvi.mask = LVIF_TEXT | LVIF_PARAM; lvi.iItem = rowIndex; lvi.iSubItem = 0;
                        lvi.pszText = (LPWSTR)displayName.c_str(); lvi.lParam = pe32.th32ProcessID;
                        SendMessageW(hListView, LVM_INSERTITEMW, 0, (LPARAM)&lvi);

                        std::wstring pidStr = std::to_wstring(pe32.th32ProcessID);
                        LVITEMW lviPid = {0}; lviPid.iSubItem = 1; lviPid.pszText = (LPWSTR)pidStr.c_str();
                        SendMessageW(hListView, LVM_SETITEMTEXTW, rowIndex, (LPARAM)&lviPid);

                        LVITEMW lviMem = {0}; lviMem.iSubItem = 2; lviMem.pszText = (LPWSTR)memStr.c_str();
                        SendMessageW(hListView, LVM_SETITEMTEXTW, rowIndex, (LPARAM)&lviMem);
                        rowIndex++;
                    } while (Process32NextW(hSnap, &pe32));
                }
                CloseHandle(hSnap);
            }
        }

        void ActionSelected(bool blockApp) {
            int sel = SendMessageW(hListView, LVM_GETNEXTITEM, -1, LVNI_SELECTED);
            if (sel != -1) {
                LVITEMW lvi = {0}; lvi.iItem = sel; lvi.mask = LVIF_PARAM;
                SendMessageW(hListView, LVM_GETITEMW, 0, (LPARAM)&lvi);
                DWORD pid = (DWORD)lvi.lParam;

                HANDLE hProc = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ | PROCESS_TERMINATE, FALSE, pid);
                if (hProc) {
                    if (blockApp) {
                        wchar_t exePath[MAX_PATH]; DWORD size = MAX_PATH;
                        if (QueryFullProcessImageNameW(hProc, 0, exePath, &size)) {
                            std::wstring path(exePath);
                            size_t pos = path.find_last_of(L"\\/");
                            std::wstring exeName = (pos != std::wstring::npos) ? path.substr(pos + 1) : path;
                            
                            std::string lowerExe = RHC::StringUtils::toLower(RHC::Utils::wstring_to_utf8(exeName));
                            std::string lowerPath = RHC::StringUtils::toLower(RHC::Utils::wstring_to_utf8(path));
                            
                            // FIX: Ensure fake system processes bypass protection
                            bool isFakeSystemProcess = (lowerExe == "svchost.exe" || lowerExe == "conhost.exe" || lowerExe == "explorer.exe") && (lowerPath.find("\\windows\\") == std::string::npos);

                            std::vector<std::string> protectedExes = {"explorer.exe", "cmd.exe", "powershell.exe", "taskmgr.exe", "csrss.exe", "winlogon.exe", "lsass.exe", "smss.exe", "services.exe", "spoolsv.exe"};
                            bool isProtected = false;
                            
                            if (!isFakeSystemProcess) {
                                for(auto& p : protectedExes) if (lowerExe == p) isProtected = true;
                            }
                            
                            if (isProtected) MessageBoxW(hwnd, L"Cannot block essential system apps!", L"Error", MB_ICONERROR);
                            else {
                                RHC::DatabaseManager db("rhc_state.db");
                                std::string currentList = db.getString("BLOCKLIST_EXE", "");
                                if (currentList.find(lowerExe) == std::string::npos) {
                                    if (currentList.empty()) db.putString("BLOCKLIST_EXE", lowerExe);
                                    else db.putString("BLOCKLIST_EXE", currentList + "," + lowerExe);
                                    MessageBoxW(hwnd, L"App Blocked! It will auto-close.", L"Success", MB_OK);
                                } else MessageBoxW(hwnd, L"App is already blocked.", L"Info", MB_OK);
                            }
                        }
                    }
                    TerminateProcess(hProc, 0); CloseHandle(hProc);
                }
                RefreshList(); RHC::DashboardUI::RefreshBlockListUI(); 
            }
        }

        LRESULT CALLBACK WndProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
            switch (uMsg) {
                case WM_CREATE: {
                    HFONT hFont = CreateFontW(14, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
                    hListView = CreateWindowExW(WS_EX_CLIENTEDGE, WC_LISTVIEWW, L"", WS_CHILD | WS_VISIBLE | LVS_REPORT | LVS_SINGLESEL | LVS_SHOWSELALWAYS, 20, 20, 440, 360, hwnd, NULL, NULL, NULL);
                    SendMessageW(hListView, LVM_SETEXTENDEDLISTVIEWSTYLE, 0, LVS_EX_FULLROWSELECT | LVS_EX_GRIDLINES | LVS_EX_DOUBLEBUFFER);
                    SendMessageW(hListView, WM_SETFONT, (WPARAM)hFont, TRUE);

                    LVCOLUMNW lvc = {0}; lvc.mask = LVCF_FMT | LVCF_WIDTH | LVCF_TEXT | LVCF_SUBITEM;
                    lvc.iSubItem = 0; lvc.cx = 220; lvc.fmt = LVCFMT_LEFT; lvc.pszText = (LPWSTR)L"Process Name"; SendMessageW(hListView, LVM_INSERTCOLUMNW, 0, (LPARAM)&lvc);
                    lvc.iSubItem = 1; lvc.cx = 80; lvc.fmt = LVCFMT_LEFT; lvc.pszText = (LPWSTR)L"PID"; SendMessageW(hListView, LVM_INSERTCOLUMNW, 1, (LPARAM)&lvc);
                    lvc.iSubItem = 2; lvc.cx = 110; lvc.fmt = LVCFMT_RIGHT; lvc.pszText = (LPWSTR)L"Memory"; SendMessageW(hListView, LVM_INSERTCOLUMNW, 2, (LPARAM)&lvc);

                    HWND bRefresh = CreateWindowExW(0, L"BUTTON", L"Refresh", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 20, 390, 100, 35, hwnd, (HMENU)1, NULL, NULL);
                    HWND bBlock = CreateWindowExW(0, L"BUTTON", L"Block App", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 240, 390, 100, 35, hwnd, (HMENU)3, NULL, NULL);
                    HWND bKill = CreateWindowExW(0, L"BUTTON", L"End Task", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 360, 390, 100, 35, hwnd, (HMENU)2, NULL, NULL);
                    
                    SendMessageW(bRefresh, WM_SETFONT, (WPARAM)hFont, TRUE); SendMessageW(bBlock, WM_SETFONT, (WPARAM)hFont, TRUE); SendMessageW(bKill, WM_SETFONT, (WPARAM)hFont, TRUE);
                    RefreshList(); return 0;
                }
                case WM_COMMAND:
                    if (LOWORD(wParam) == 1) RefreshList();
                    if (LOWORD(wParam) == 2) ActionSelected(false); 
                    if (LOWORD(wParam) == 3) ActionSelected(true);  
                    return 0;
                case WM_CLOSE: Hide(); return 0;
            }
            return DefWindowProcW(hwnd, uMsg, wParam, lParam);
        }

        void Initialize(HINSTANCE hInstance) {
            INITCOMMONCONTROLSEX icex; icex.dwSize = sizeof(INITCOMMONCONTROLSEX); icex.dwICC = ICC_LISTVIEW_CLASSES; InitCommonControlsEx(&icex);
            WNDCLASSW wc = {0}; wc.lpfnWndProc = WndProc; wc.hInstance = hInstance; wc.lpszClassName = L"RHC_TaskMgr"; wc.hbrBackground = CreateSolidBrush(RGB(240, 240, 240)); RegisterClassW(&wc);
            hwnd = CreateWindowExW(0, L"RHC_TaskMgr", L"Task Manager", WS_OVERLAPPEDWINDOW ^ WS_THICKFRAME ^ WS_MAXIMIZEBOX, CW_USEDEFAULT, CW_USEDEFAULT, 500, 480, NULL, NULL, hInstance, NULL);
        }
        void Show() { RefreshList(); ShowWindow(hwnd, SW_RESTORE); SetForegroundWindow(hwnd); }
        void Hide() { ShowWindow(hwnd, SW_HIDE); }
        HWND GetWindowHandle() { return hwnd; }
    }
}
