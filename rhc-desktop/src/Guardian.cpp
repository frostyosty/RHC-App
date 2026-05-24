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
#include <iostream>

namespace RHC {
    std::string getRedirect(const std::string& trigger) {
        RHC::DatabaseManager db("rhc_state.db");
        std::string redirects = db.getString("REDIRECTS", "");
        for (const auto& r : RHC::StringUtils::split(redirects, ',')) {
            auto parts = RHC::StringUtils::split(r, '|');
            if (parts.size() == 2 && RHC::StringUtils::toLower(trigger).find(RHC::StringUtils::toLower(parts[0])) != std::string::npos) return parts[1];
        }
        return "";
    }

    void GuardianThread() {
        CoInitializeEx(NULL, COINIT_MULTITHREADED);
        RHC::ShieldRuleEngine ruleEngine;
        { 
            RHC::DatabaseManager db("rhc_state.db"); 
            if (db.getString("OVERRIDE_IS_PAUSED", "") == "TRUE") { db.putString("OVERRIDE_IS_PAUSED", ""); CoUninitialize(); return; } 
            RHC::MomentumEngine::resetDailyIfNeeded(db); 
            RHC::DashboardUI::SyncHostsFileFromDB(); 
        }

        int syncCounter = 0;
        while (true) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1000));
            
            // --- PAUSE SCANNING WHILE WINDOWS IS SUSPENDED/SLEEPING ---
            if (g_SystemIsSleeping) {
                std::this_thread::sleep_for(std::chrono::milliseconds(4000)); 
                continue;
            }

            if (syncCounter++ >= 60) { RHC::DashboardUI::SyncHostsFileFromDB(); syncCounter = 0; }

            RHC::DatabaseManager db("rhc_state.db");
            int nfStart = db.getInt("NIGHTFALL_START", -1);
            int nfEnd = db.getInt("NIGHTFALL_END", -1);
            
            if (nfStart != -1 && nfEnd != -1 && nfStart != nfEnd) {
                time_t t = time(nullptr); tm* now = localtime(&t);
                int currentMins = now->tm_hour * 60 + now->tm_min;
                bool isNightfall = (nfStart < nfEnd) ? (currentMins >= nfStart && currentMins <= nfEnd) : (currentMins >= nfStart || currentMins <= nfEnd);
                
                int warnStart = nfStart - 5; if (warnStart < 0) warnStart += 1440;
                bool isWarn = (warnStart < nfStart) ? (currentMins >= warnStart && currentMins < nfStart) : (currentMins >= warnStart || currentMins < nfStart);
                
                if (isNightfall) {
                    RHC::NightfallUI::UpdateOverlay(2, 0);
                } else if (isWarn) {
                    int currSecs = currentMins * 60 + now->tm_sec; int wStartSecs = warnStart * 60;
                    int diff = currSecs - wStartSecs; if (diff < 0) diff += 86400;
                    int alpha = (diff * 255) / 300; if (alpha > 240) alpha = 240;
                    RHC::NightfallUI::UpdateOverlay(1, alpha);
                } else { RHC::NightfallUI::UpdateOverlay(0, 0); }
            } else { RHC::NightfallUI::UpdateOverlay(0, 0); }

            HWND hwnd = GetForegroundWindow();
            if (!hwnd || hwnd == g_hMainWindow || hwnd == g_hDashboardWindow || 
                hwnd == RHC::CustomTaskManager::GetWindowHandle() || 
                hwnd == RHC::SystemOverride::GetWindowHandle() || 
                hwnd == RHC::NightfallUI::GetWindowHandle()) continue;

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
                std::string redirectTarget = getRedirect(u8Exe);
                if (!redirectTarget.empty()) { ShellExecuteA(NULL, "open", redirectTarget.c_str(), NULL, NULL, SW_SHOW); continue; }
                g_RedWallReason = L"App Blocked: " + blockedExeName;
                ShowWindow(g_hMainWindow, SW_RESTORE); SetForegroundWindow(g_hMainWindow); BringWindowToTop(g_hMainWindow); InvalidateRect(g_hMainWindow, NULL, TRUE);
                SetTimer(g_hMainWindow, 1, 3000, NULL); continue; 
            }

            std::string scannedText = RHC::UIAScanner::ScanForeground();
            if (g_DebugMode) {
                std::cout << "\n[SCAN] Active App: " << u8Exe << std::endl;
                std::cout << "[SCAN] Window Title: " << RHC::Utils::wstring_to_utf8(wtitle) << std::endl;
                std::cout << "[SCAN] Extracted Text: " << scannedText.substr(0, 300) << "..." << std::endl;
            }

            RHC::ShieldResult result = ruleEngine.evaluateScreenText(scannedText, db);
            if (result.action == RHC::ShieldAction::BLOCK_CONTENT) {
                RHC::Utils::InjectEvadeAction();
                std::string redirectTarget = getRedirect(result.reason);
                if (!redirectTarget.empty()) { ShellExecuteA(NULL, "open", redirectTarget.c_str(), NULL, NULL, SW_SHOW); continue; }
                g_RedWallReason = RHC::Utils::utf8_to_wstring(result.reason);
                ShowWindow(g_hMainWindow, SW_RESTORE); SetForegroundWindow(g_hMainWindow); BringWindowToTop(g_hMainWindow); InvalidateRect(g_hMainWindow, NULL, TRUE);
                SetTimer(g_hMainWindow, 1, 3000, NULL);
            }
        }
        CoUninitialize();
    }
}
