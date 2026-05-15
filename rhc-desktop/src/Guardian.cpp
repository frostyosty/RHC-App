#include "Guardian.h"
#include "Globals.h"
#include "DesktopUtils.h"
#include "DashboardUI.h"
#include "UIAScanner.h"
#include "CustomTaskManager.h"
#include "SystemOverride.h"
#include "NightfallUI.h"
#include "include/ShieldRuleEngine.h"
#include "include/DatabaseManager.h"
#include "include/MomentumEngine.h"
#include "include/StringUtils.h"
#include <windows.h>
#include <thread>
#include <chrono>

namespace RHC {
    void GuardianThread() {
        CoInitializeEx(NULL, COINIT_MULTITHREADED);
        RHC::ShieldRuleEngine ruleEngine;
        { 
            RHC::DatabaseManager db("rhc_state.db"); 
            if (db.getString("OVERRIDE_IS_PAUSED", "") == "TRUE") { db.putString("OVERRIDE_IS_PAUSED", ""); return; } 
            RHC::MomentumEngine::resetDailyIfNeeded(db); 
            RHC::DashboardUI::SyncHostsFileFromDB(); 
        }

        int syncCounter = 0;
        while (true) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1000));
            if (syncCounter++ >= 60) { RHC::DashboardUI::SyncHostsFileFromDB(); syncCounter = 0; }

            HWND hwnd = GetForegroundWindow();
            if (!hwnd || hwnd == g_hMainWindow || hwnd == g_hDashboardWindow || 
                hwnd == RHC::CustomTaskManager::GetWindowHandle() || 
                hwnd == RHC::SystemOverride::GetWindowHandle() || 
                hwnd == RHC::NightfallUI::GetWindowHandle()) continue;

            // FIX: Use Wide String to correctly read Unicode/Chinese Task Manager names
            wchar_t title[512]; GetWindowTextW(hwnd, title, sizeof(title)/sizeof(wchar_t));
            std::wstring wtitle(title);
            if (wtitle.find(L"Task Manager") != std::wstring::npos && wtitle.find(L"RHC_TaskMgr") == std::wstring::npos) { PostMessage(hwnd, WM_CLOSE, 0, 0); continue; }

            DWORD pid; GetWindowThreadProcessId(hwnd, &pid); HANDLE hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ | PROCESS_TERMINATE, FALSE, pid);
            bool isAppBlocked = false; std::wstring blockedExeName = L""; std::string u8Exe = "";

            if (hProcess) {
                wchar_t exePath[MAX_PATH]; DWORD size = MAX_PATH;
                if (QueryFullProcessImageNameW(hProcess, 0, exePath, &size)) {
                    std::wstring path(exePath); std::wstring exeName = (path.find_last_of(L"\\/") != std::wstring::npos) ? path.substr(path.find_last_of(L"\\/") + 1) : path;
                    u8Exe = RHC::StringUtils::toLower(RHC::Utils::wstring_to_utf8(exeName));
                    RHC::DatabaseManager db("rhc_state.db");
                    for(auto& blocked : RHC::StringUtils::split(db.getString("BLOCKLIST_EXE", ""), ',')) {
                        auto parts = RHC::StringUtils::split(blocked, '|');
                        if (!parts.empty() && u8Exe == RHC::StringUtils::toLower(parts[0])) {
                            isAppBlocked = true; 
                            if (parts.size() >= 4 && parts[3] != "None" && RHC::Utils::IsTimeAllowed(parts[3])) isAppBlocked = false;
                            if (isAppBlocked) { blockedExeName = exeName; TerminateProcess(hProcess, 0); break; }
                        }
                    }
                }
                CloseHandle(hProcess);
            }

            if (isAppBlocked) {
                RHC::DashboardUI::TrackOvercome(u8Exe, "BLOCKLIST_EXE", "FIRST_OVERCOME_EXE_" + u8Exe);
                g_RedWallReason = L"App Blocked: " + blockedExeName;
                ShowWindow(g_hMainWindow, SW_RESTORE); SetForegroundWindow(g_hMainWindow); BringWindowToTop(g_hMainWindow); InvalidateRect(g_hMainWindow, NULL, TRUE);
                SetTimer(g_hMainWindow, 1, 3000, NULL); continue; 
            }

            RHC::DatabaseManager db("rhc_state.db");
            RHC::ShieldResult result = ruleEngine.evaluateScreenText(RHC::UIAScanner::ScanForeground(), db);
            if (result.action == RHC::ShieldAction::BLOCK_CONTENT) {
                RHC::Utils::InjectEvadeAction();
                g_RedWallReason = RHC::Utils::utf8_to_wstring(result.reason);
                ShowWindow(g_hMainWindow, SW_RESTORE); SetForegroundWindow(g_hMainWindow); BringWindowToTop(g_hMainWindow); InvalidateRect(g_hMainWindow, NULL, TRUE);
                SetTimer(g_hMainWindow, 1, 3000, NULL);
            }
        }
        CoUninitialize();
    }
}
