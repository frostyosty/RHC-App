#include "DashboardUI.h"
#include "Globals.h"
#include "DesktopUtils.h"
#include "ui/SmoothButton.h"
#include "include/DatabaseManager.h"
#include "include/MomentumEngine.h"
#include "include/LeaderboardEngine.h"
#include "include/StringUtils.h"

#include <windows.h>
#include <commctrl.h>
#include <chrono>
#include <iomanip>
#include <sstream>

#define ID_BTN_FAV1 3001
#define ID_BTN_FAV2 3002
#define ID_BTN_FAV3 3003

namespace RHC {
    namespace CustomTaskManager { void Show(); }
    namespace SystemOverride { void Show(); }
    namespace NightfallUI { void Show(); }
    namespace HostsBlocker { void SyncHostsFile(const std::vector<std::string>& blockedDomains); }
}

namespace RHC {
    namespace DashboardUI {
        
        HWND g_hBtnFav1 = NULL;
        HWND g_hBtnFav2 = NULL;
        HWND g_hBtnFav3 = NULL;
        std::vector<TaskItem> recentFavorites;

        void SyncHostsFileFromDB() {
            RHC::DatabaseManager db("rhc_state.db");
            std::string webStr = db.getString("BLOCKLIST_WEB", "");
            std::vector<std::string> domains;
            auto entries = RHC::StringUtils::split(webStr, ',');
            for (const auto& entry : entries) {
                auto parts = RHC::StringUtils::split(entry, '|');
                if (!parts.empty() && !parts[0].empty()) {
                    if (parts.size() >= 4 && parts[3] != "None" && RHC::Utils::IsTimeAllowed(parts[3])) continue;
                    domains.push_back(parts[0]);
                }
            }
            RHC::HostsBlocker::SyncHostsFile(domains);
        }

        void UpdateFavoritesUI() {
            RHC::DatabaseManager db("rhc_state.db");
            std::string rt = db.getString("RECENT_TASKS", "");
            recentFavorites.clear();
            
            auto parts = RHC::StringUtils::split(rt, ',');
            for (const auto& p : parts) {
                if(p.empty()) continue;
                auto details = RHC::StringUtils::split(p, '|');
                if(details.size() == 2) {
                    recentFavorites.push_back({ RHC::Utils::utf8_to_wstring(details[0]), std::stoi(details[1]), "" });
                }
            }
            
            ShowWindow(g_hBtnFav1, recentFavorites.size() > 0 ? SW_SHOW : SW_HIDE);
            ShowWindow(g_hBtnFav2, recentFavorites.size() > 1 ? SW_SHOW : SW_HIDE);
            ShowWindow(g_hBtnFav3, recentFavorites.size() > 2 ? SW_SHOW : SW_HIDE);

            if (recentFavorites.size() > 0) SetWindowTextW(g_hBtnFav1, (L"⭐ " + recentFavorites[0].name).c_str());
            if (recentFavorites.size() > 1) SetWindowTextW(g_hBtnFav2, (L"⭐ " + recentFavorites[1].name).c_str());
            if (recentFavorites.size() > 2) SetWindowTextW(g_hBtnFav3, (L"⭐ " + recentFavorites[2].name).c_str());
        }

        void RefreshLogsUI() {
            SendMessageW(g_hListEarned, LB_RESETCONTENT, 0, 0);
            SendMessageW(g_hListSpent, LB_RESETCONTENT, 0, 0);
            RHC::DatabaseManager db("rhc_state.db");
            
            for (const auto& entry : RHC::StringUtils::split(db.getString("MOMENTUM_EARNED_TODAY", ""), ',')) {
                auto parts = RHC::StringUtils::split(entry, '|');
                if (parts.size() >= 2) {
                    std::wstring txt = RHC::Utils::utf8_to_wstring("🟢 +" + parts[1] + "m : " + parts[0]);
                    SendMessageW(g_hListEarned, LB_ADDSTRING, 0, (LPARAM)txt.c_str());
                }
            }
            
            for (const auto& entry : RHC::StringUtils::split(db.getString("MOMENTUM_SPENT_TODAY", ""), ',')) {
                auto parts = RHC::StringUtils::split(entry, '|');
                if (parts.size() >= 2) {
                    std::wstring txt = RHC::Utils::utf8_to_wstring("🔴 -" + parts[1] + "m : " + parts[0]);
                    SendMessageW(g_hListSpent, LB_ADDSTRING, 0, (LPARAM)txt.c_str());
                }
            }
            UpdateFavoritesUI();
        }

        void RefreshBlockListUI() {
            SendMessageW(g_hListBlocks, LB_RESETCONTENT, 0, 0);
            RHC::DatabaseManager db("rhc_state.db");
            
            auto parseAndAdd =[](const std::string& str, const std::string& icon) {
                for (const auto& entry : RHC::StringUtils::split(str, ',')) {
                    if (!entry.empty()) {
                        auto parts = RHC::StringUtils::split(entry, '|');
                        std::string disp = icon + " " + parts[0];
                        if (parts.size() >= 3) disp += " | Defeats: " + parts[2];
                        if (parts.size() >= 4 && !parts[3].empty() && parts[3] != "None") disp += " [" + parts[3] + "]";
                        SendMessageW(g_hListBlocks, LB_ADDSTRING, 0, (LPARAM)RHC::Utils::utf8_to_wstring(disp).c_str());
                    }
                }
            };
            parseAndAdd(db.getString("BLOCKLIST_EXE", ""), "🖥️");
            parseAndAdd(db.getString("BLOCKLIST_WEB", ""), "🌐");
            RefreshLogsUI();
        }

        void PopulateInstalledAppsCombo(HWND hCombo) {
            std::vector<std::string> apps = {"discord.exe", "steam.exe", "epicgameslauncher.exe", "telegram.exe", "whatsapp.exe", "spotify.exe"};
            const HKEY roots[] = { HKEY_LOCAL_MACHINE, HKEY_CURRENT_USER };
            const char* subkeys[] = { "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall", "SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall" };
            for (int i=0; i<2; i++) {
                for (int j=0; j<2; j++) {
                    HKEY hKey;
                    if (RegOpenKeyExA(roots[i], subkeys[j], 0, KEY_READ, &hKey) == ERROR_SUCCESS) {
                        char subKeyName[256]; DWORD subKeyNameSize = 256, index = 0;
                        while (RegEnumKeyExA(hKey, index++, subKeyName, &subKeyNameSize, NULL, NULL, NULL, NULL) == ERROR_SUCCESS) {
                            subKeyNameSize = 256; HKEY hSubKey;
                            if (RegOpenKeyExA(hKey, subKeyName, 0, KEY_READ, &hSubKey) == ERROR_SUCCESS) {
                                char displayIcon[512]; DWORD iconSize = 512;
                                if (RegQueryValueExA(hSubKey, "DisplayIcon", NULL, NULL, (LPBYTE)displayIcon, &iconSize) == ERROR_SUCCESS) {
                                    std::string iconStr(displayIcon); size_t exePos = iconStr.find(".exe");
                                    if (exePos != std::string::npos) {
                                        size_t slashPos = iconStr.find_last_of("\\/", exePos);
                                        if (slashPos != std::string::npos) {
                                            std::string exeName = RHC::StringUtils::toLower(iconStr.substr(slashPos + 1, exePos - slashPos + 3));
                                            if (!exeName.empty() && exeName[0] == '"') exeName = exeName.substr(1);
                                            if (exeName.find("unin") == std::string::npos && exeName.find("setup") == std::string::npos) {
                                                bool exists = false; for (auto& a : apps) if (a == exeName) exists = true;
                                                if (!exists) apps.push_back(exeName);
                                            }
                                        }
                                    }
                                }
                                RegCloseKey(hSubKey);
                            }
                        }
                        RegCloseKey(hKey);
                    }
                }
            }
            for (const auto& a : apps) SendMessageW(hCombo, CB_ADDSTRING, 0, (LPARAM)RHC::Utils::utf8_to_wstring(a).c_str());
        }

        void AddBlockItem(bool isExe, const std::wstring& target, const std::wstring& timeRestr) {
            std::string utf8Target = RHC::Utils::wstring_to_utf8(target);
            std::string utf8Time = RHC::Utils::wstring_to_utf8(timeRestr);
            if (utf8Target.empty() || utf8Target == "None") return;
            if (utf8Time.empty()) utf8Time = "None"; // Ensure explicit None string

            RHC::DatabaseManager db("rhc_state.db");
            auto now = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
            std::stringstream ss; ss << std::put_time(std::localtime(&now), "%b %d");
            
            std::string newEntry = utf8Target + "|" + ss.str() + "|0|" + utf8Time;
            std::string key = isExe ? "BLOCKLIST_EXE" : "BLOCKLIST_WEB";
            
            std::string currentList = db.getString(key, "");
            db.putString(key, currentList.empty() ? newEntry : currentList + "," + newEntry);
            if(!isExe) SyncHostsFileFromDB();
            RefreshBlockListUI();
        }

        void UpdateDashboardText() {
            RHC::DatabaseManager db("rhc_state.db");
            std::wstring text = std::to_wstring(RHC::MomentumEngine::calculateCurrentMomentum(db)) + L" MINS AVAILABLE";
            SetWindowTextW(g_hMomentumText, text.c_str());
        }

        void TrackOvercome(const std::string& target, const std::string& listKey, const std::string& firstTimeKey) {
            RHC::DatabaseManager db("rhc_state.db");
            std::string listStr = db.getString(listKey, "");
            auto entries = RHC::StringUtils::split(listStr, ',');
            std::string newList = ""; bool found = false;
            
            for (auto& entry : entries) {
                if (entry.empty()) continue;
                auto parts = RHC::StringUtils::split(entry, '|');
                if (RHC::StringUtils::toLower(parts[0]) == RHC::StringUtils::toLower(target)) {
                    found = true;
                    entry = parts[0] + "|" + (parts.size() > 1 ? parts[1] : "Today") + "|" + std::to_string((parts.size() > 2 ? std::stoi(parts[2]) : 0) + 1) + "|" + (parts.size() > 3 ? parts[3] : "None");
                }
                if (!newList.empty()) newList += ",";
                newList += entry;
            }
            
            if (found) {
                db.putString(listKey, newList);
                db.putInt("TOTAL_DEFEATS", db.getInt("TOTAL_DEFEATS", 0) + 1);
                RefreshBlockListUI();
                if (db.getInt(firstTimeKey, 0) == 0) {
                    db.putInt(firstTimeKey, 1);
                    RHC::MomentumEngine::addEarnedMomentum(db, target, false);
                    UpdateDashboardText();
                }
                RHC::LeaderboardEngine::submitScoreAsync(db, 0); 
            }
        }

        void ExecuteSpendTime(const TaskItem& task) {
            RHC::DatabaseManager db("rhc_state.db");
            if (RHC::MomentumEngine::spendMomentum(db, RHC::Utils::wstring_to_utf8(task.name), task.cost)) {
                if (task.category == "reading") db.putInt("TOTAL_READING", db.getInt("TOTAL_READING", 0) + task.cost);
                if (task.category == "physical") db.putInt("TOTAL_PHYSICAL", db.getInt("TOTAL_PHYSICAL", 0) + task.cost);
                
                // Update Recent Favorites String
                std::string currentRt = db.getString("RECENT_TASKS", "");
                std::string newTaskStr = RHC::Utils::wstring_to_utf8(task.name) + "|" + std::to_string(task.cost);
                
                std::vector<std::string> uniques;
                uniques.push_back(newTaskStr);
                for (const auto& p : RHC::StringUtils::split(currentRt, ',')) {
                    if (p != newTaskStr && !p.empty() && uniques.size() < 3) uniques.push_back(p);
                }
                
                std::string newRt = "";
                for(auto& u : uniques) { if(!newRt.empty()) newRt += ","; newRt += u; }
                db.putString("RECENT_TASKS", newRt);

                RHC::LeaderboardEngine::submitScoreAsync(db, task.cost);
                MessageBoxW(g_hDashboardWindow, L"Momentum Spent! Logged to Ledgers.", L"Success", MB_OK | MB_ICONINFORMATION);
                UpdateDashboardText();
                RefreshLogsUI();
            } else {
                MessageBoxW(g_hDashboardWindow, L"Not enough Momentum. Overcome urges first!", L"Error", MB_OK | MB_ICONERROR);
            }
        }

        void ProcessCategoryExecution(HWND hCombo) {
            wchar_t buf[256];
            GetWindowTextW(hCombo, buf, 256);
            std::wstring taskName = buf;
            for (const auto& t : g_Tasks) {
                if (t.name == taskName) { ExecuteSpendTime(t); return; }
            }
        }

        LRESULT CALLBACK DashboardProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
            switch (uMsg) {
                case WM_CREATE: {
                    g_hFontGiant = CreateFontW(40, 0, 0, 0, FW_BOLD, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
                    g_hFontTitle = CreateFontW(22, 0, 0, 0, FW_BOLD, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
                    g_hFontNormal = CreateFontW(16, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
                    g_hFontEmoji = CreateFontW(16, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI Emoji");
                    g_hFontSmall = CreateFontW(14, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");

                    auto addText = [hwnd](int x, int y, int w, int h, const wchar_t* txt, HFONT font) { HWND t = CreateWindowExW(0, L"STATIC", txt, WS_CHILD | WS_VISIBLE | SS_CENTER, x, y, w, h, hwnd, NULL, NULL, NULL); SendMessage(t, WM_SETFONT, (WPARAM)font, TRUE); return t; };
                    
                    // --- 1. NEW OVERCOME SECTION ---
                    addText(0, 10, 780, 30, L"1. NEW OVERCOME", g_hFontTitle);
                    addText(20, 40, 360, 20, L"--- APPLICATION ---", g_hFontNormal);
                    addText(400, 40, 360, 20, L"--- WEBSITE ---", g_hFontNormal);
                    
                    // App Row
                    g_hComboAppBlock = CreateWindowExW(WS_EX_CLIENTEDGE, L"COMBOBOX", L"", WS_CHILD | WS_VISIBLE | CBS_DROPDOWN | WS_VSCROLL, 20, 60, 180, 200, hwnd, NULL, NULL, NULL);
                    g_hEditAppTime = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"None", WS_CHILD | WS_VISIBLE, 210, 60, 80, 25, hwnd, NULL, NULL, NULL);
                    HWND bAddApp = CreateWindowExW(0, L"BUTTON", L"Block App", WS_CHILD | WS_VISIBLE, 300, 60, 80, 25, hwnd, (HMENU)ID_BTN_ADD_APP, NULL, NULL);
                    PopulateInstalledAppsCombo(g_hComboAppBlock);
                    
                    // Web Row
                    g_hEditWebBlock = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"reddit.com", WS_CHILD | WS_VISIBLE, 400, 60, 180, 25, hwnd, NULL, NULL, NULL);
                    g_hEditWebTime = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"None", WS_CHILD | WS_VISIBLE, 590, 60, 80, 25, hwnd, NULL, NULL, NULL);
                    HWND bAddWeb = CreateWindowExW(0, L"BUTTON", L"Block Web", WS_CHILD | WS_VISIBLE, 680, 60, 80, 25, hwnd, (HMENU)ID_BTN_ADD_WEB, NULL, NULL);

                    RHC::UI::SmoothButton::Attach(bAddApp); RHC::UI::SmoothButton::Attach(bAddWeb);
                    
                    // --- 2. ACTIVELY OVERCOMING ---
                    addText(0, 110, 780, 30, L"2. ACTIVELY OVERCOMING", g_hFontTitle);
                    g_hListBlocks = CreateWindowExW(WS_EX_CLIENTEDGE, L"LISTBOX", NULL, WS_CHILD | WS_VISIBLE | WS_VSCROLL | LBS_NOINTEGRALHEIGHT, 20, 140, 740, 120, hwnd, (HMENU)ID_LIST_BLOCKS, NULL, NULL);
                    
                    // --- 3. MOMENTUM CORE ---
                    g_hMomentumText = addText(0, 270, 780, 50, L"0 MINS AVAILABLE", g_hFontGiant);
                    
                    // --- 4. LEDGERS ---
                    addText(20, 330, 360, 20, L"EARNED", g_hFontTitle);
                    addText(400, 330, 360, 20, L"SPENT LEDGER", g_hFontTitle);
                    g_hListEarned = CreateWindowExW(WS_EX_CLIENTEDGE, L"LISTBOX", NULL, WS_CHILD | WS_VISIBLE | WS_VSCROLL | LBS_NOINTEGRALHEIGHT, 20, 360, 360, 120, hwnd, (HMENU)ID_LIST_EARNED, NULL, NULL);
                    g_hListSpent = CreateWindowExW(WS_EX_CLIENTEDGE, L"LISTBOX", NULL, WS_CHILD | WS_VISIBLE | WS_VSCROLL | LBS_NOINTEGRALHEIGHT, 400, 360, 360, 120, hwnd, (HMENU)ID_LIST_SPENT, NULL, NULL);

                    // --- 5. SPEND MOMENTUM (CATEGORIES) ---
                    addText(0, 490, 780, 30, L"6. SPEND MOMENTUM", g_hFontTitle);
                    
                    auto buildCat = [hwnd](int x, int y, int cID, int bID, const std::string& cat) {
                        HWND hCombo = CreateWindowExW(WS_EX_CLIENTEDGE, L"COMBOBOX", L"", WS_CHILD | WS_VISIBLE | CBS_DROPDOWNLIST | WS_VSCROLL, x, y, 250, 200, hwnd, (HMENU)(UINT_PTR)cID, NULL, NULL);
                        HWND hBtn = CreateWindowExW(0, L"BUTTON", L"EXECUTE", WS_CHILD | WS_VISIBLE, x+260, y, 100, 25, hwnd, (HMENU)(UINT_PTR)bID, NULL, NULL);
                        for (auto& t : g_CategorizedTasks[cat]) SendMessageW(hCombo, CB_ADDSTRING, 0, (LPARAM)t.name.c_str());
                        SendMessageW(hCombo, CB_SETCURSEL, 0, 0); SendMessageW(hCombo, WM_SETFONT, (WPARAM)g_hFontEmoji, TRUE);
                        RHC::UI::SmoothButton::Attach(hBtn); return hCombo;
                    };
                    
                    g_hComboPhysical = buildCat(20, 530, ID_COMBO_PHYSICAL, ID_BTN_EXEC_PHYSICAL, "Physical");
                    g_hComboMental   = buildCat(400, 530, ID_COMBO_MENTAL, ID_BTN_EXEC_MENTAL, "Mental");
                    g_hComboWork     = buildCat(20, 570, ID_COMBO_WORK, ID_BTN_EXEC_WORK, "Work");
                    g_hComboChores   = buildCat(400, 570, ID_COMBO_CHORES, ID_BTN_EXEC_CHORES, "Chores");

                    // --- FAVORITES QUICK ACTIONS ---
                    addText(0, 620, 780, 20, L"RECENT QUICK ACTIONS", g_hFontNormal);
                    g_hBtnFav1 = CreateWindowExW(0, L"BUTTON", L"Fav 1", WS_CHILD, 20, 650, 230, 40, hwnd, (HMENU)ID_BTN_FAV1, NULL, NULL);
                    g_hBtnFav2 = CreateWindowExW(0, L"BUTTON", L"Fav 2", WS_CHILD, 275, 650, 230, 40, hwnd, (HMENU)ID_BTN_FAV2, NULL, NULL);
                    g_hBtnFav3 = CreateWindowExW(0, L"BUTTON", L"Fav 3", WS_CHILD, 530, 650, 230, 40, hwnd, (HMENU)ID_BTN_FAV3, NULL, NULL);
                    RHC::UI::SmoothButton::Attach(g_hBtnFav1); RHC::UI::SmoothButton::Attach(g_hBtnFav2); RHC::UI::SmoothButton::Attach(g_hBtnFav3);

                    // --- COMMAND STRIP ---
                    HWND bTaskMgr = CreateWindowExW(0, L"BUTTON", L"🛡️ Secure Task Mgr", WS_CHILD | WS_VISIBLE, 20, 750, 230, 40, hwnd, (HMENU)ID_BTN_OPEN_TASKMGR, NULL, NULL);
                    HWND bOverride = CreateWindowExW(0, L"BUTTON", L"⚙️ System Override", WS_CHILD | WS_VISIBLE, 275, 750, 230, 40, hwnd, (HMENU)ID_TRAY_OVERRIDE, NULL, NULL);
                    HWND bNightfall = CreateWindowExW(0, L"BUTTON", L"🌙 Nightfall Schedule", WS_CHILD | WS_VISIBLE, 530, 750, 230, 40, hwnd, (HMENU)ID_TRAY_NIGHTFALL, NULL, NULL);
                    RHC::UI::SmoothButton::Attach(bTaskMgr); RHC::UI::SmoothButton::Attach(bOverride); RHC::UI::SmoothButton::Attach(bNightfall);

                    // Font assignments
                    SendMessage(g_hComboAppBlock, WM_SETFONT, (WPARAM)g_hFontNormal, TRUE); SendMessage(g_hEditAppTime, WM_SETFONT, (WPARAM)g_hFontNormal, TRUE);
                    SendMessage(g_hEditWebBlock, WM_SETFONT, (WPARAM)g_hFontNormal, TRUE); SendMessage(g_hEditWebTime, WM_SETFONT, (WPARAM)g_hFontNormal, TRUE);
                    SendMessage(g_hListBlocks, WM_SETFONT, (WPARAM)g_hFontEmoji, TRUE);
                    SendMessage(g_hListEarned, WM_SETFONT, (WPARAM)g_hFontEmoji, TRUE); SendMessage(g_hListSpent, WM_SETFONT, (WPARAM)g_hFontEmoji, TRUE);
                    
                    RefreshBlockListUI();
                    return 0;
                }
                case WM_CTLCOLORSTATIC: { HDC hdcStatic = (HDC)wParam; SetTextColor(hdcStatic, RGB(255, 255, 255)); SetBkColor(hdcStatic, RGB(18, 18, 18)); return (LRESULT)CreateSolidBrush(RGB(18, 18, 18)); }
                case WM_CTLCOLORLISTBOX: { HDC hdcList = (HDC)wParam; SetTextColor(hdcList, RGB(220, 220, 220)); SetBkColor(hdcList, RGB(30, 30, 32)); return (LRESULT)CreateSolidBrush(RGB(30, 30, 32)); }
                case WM_CTLCOLOREDIT: { HDC hdcEdit = (HDC)wParam; SetTextColor(hdcEdit, RGB(255, 255, 255)); SetBkColor(hdcEdit, RGB(40, 40, 42)); return (LRESULT)CreateSolidBrush(RGB(40, 40, 42)); }
                case WM_COMMAND: {
                    int id = LOWORD(wParam);
                    if (id == ID_BTN_OPEN_TASKMGR) RHC::CustomTaskManager::Show();
                    if (id == ID_TRAY_OVERRIDE) RHC::SystemOverride::Show();
                    if (id == ID_TRAY_NIGHTFALL) RHC::NightfallUI::Show();
                    
                    if (id == ID_BTN_ADD_APP) {
                        wchar_t target[256], timeBuf[256]; 
                        GetWindowTextW(g_hComboAppBlock, target, 256); GetWindowTextW(g_hEditAppTime, timeBuf, 256);
                        AddBlockItem(true, target, timeBuf); SetWindowTextW(g_hComboAppBlock, L""); SetWindowTextW(g_hEditAppTime, L"None");
                    }
                    if (id == ID_BTN_ADD_WEB) {
                        wchar_t target[256], timeBuf[256]; 
                        GetWindowTextW(g_hEditWebBlock, target, 256); GetWindowTextW(g_hEditWebTime, timeBuf, 256);
                        AddBlockItem(false, target, timeBuf); SetWindowTextW(g_hEditWebBlock, L""); SetWindowTextW(g_hEditWebTime, L"None");
                    }

                    if (id == ID_BTN_EXEC_PHYSICAL) ProcessCategoryExecution(g_hComboPhysical);
                    if (id == ID_BTN_EXEC_MENTAL) ProcessCategoryExecution(g_hComboMental);
                    if (id == ID_BTN_EXEC_WORK) ProcessCategoryExecution(g_hComboWork);
                    if (id == ID_BTN_EXEC_CHORES) ProcessCategoryExecution(g_hComboChores);

                    if (id == ID_BTN_FAV1 && recentFavorites.size() > 0) ExecuteSpendTime(recentFavorites[0]);
                    if (id == ID_BTN_FAV2 && recentFavorites.size() > 1) ExecuteSpendTime(recentFavorites[1]);
                    if (id == ID_BTN_FAV3 && recentFavorites.size() > 2) ExecuteSpendTime(recentFavorites[2]);

                    return 0;
                }
                case WM_CLOSE: ShowWindow(hwnd, SW_HIDE); return 0;
            }
            return DefWindowProcW(hwnd, uMsg, wParam, lParam);
        }
    }
}
