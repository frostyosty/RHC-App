#include <windows.h>
#include <commctrl.h>
#include <thread>
#include <vector>
#include <map>

// --- Modular Headers ---
#include "Globals.h"
#include "DesktopUtils.h"
#include "Guardian.h"
#include "DashboardUI.h"
#include "TrayUI.h"
#include "ui/SmoothButton.h"
#include "include/DatabaseManager.h"

// --- Unity Build Includes ---
#include "../cloak/SafeModeCloak.h"

#include "UIAScanner.h"
#include "HostsBlocker.h"
#include "CustomTaskManager.h"
#include "SystemOverride.h"
#include "CrashReporter.h"
#include "NightfallUI.h"

// ==========================================
// GLOBAL VARIABLE DEFINITIONS
// ==========================================
HWND g_hMainWindow = NULL; 
HWND g_hDashboardWindow = NULL; 
HWND g_hMomentumText = NULL;

HWND g_hComboAppBlock = NULL;
HWND g_hEditAppTime = NULL;
HWND g_hEditWebBlock = NULL;
HWND g_hEditWebTime = NULL;

HWND g_hListBlocks = NULL;
HWND g_hListEarned = NULL; 
HWND g_hListSpent = NULL;

HWND g_hComboPhysical = NULL;
HWND g_hComboMental = NULL;
HWND g_hComboChores = NULL;
HWND g_hComboWork = NULL;

HWND g_hHoveredBtn = NULL; 
HWND g_hPressedBtn = NULL; 
HHOOK g_hKeyboardHook = NULL;

std::wstring g_RedWallReason = L"";
NOTIFYICONDATAW nid = {};
HFONT g_hFontTitle, g_hFontNormal, g_hFontEmoji, g_hFontSmall, g_hFontGiant;

std::vector<TaskItem> g_Tasks = {
    {L"🪓 Chop Wood / Yardwork (45m)", 45, "physical"}, {L"🏃 Go for a Run (30m)", 30, "physical"},
    {L"🏋️ Weightlifting / Gym (60m)", 60, "physical"}, {L"🧘 Stretching / Yoga (20m)", 20, "physical"},
    {L"📚 Read a Book (30m)", 30, "mental"}, {L"🧠 Deep Work / Focus (60m)", 60, "work"},
    {L"🗣️ Learn a Language (20m)", 20, "mental"}, {L"🧊 Cold Exposure (10m)", 10, "health"},
    {L"🙏 Meditate / Pray (15m)", 15, "mental"}, {L"🧹 Clean the House (30m)", 30, "chores"},
    {L"🍳 Meal Prep / Cook (45m)", 45, "chores"}, {L"📞 Call Family / Friend (20m)", 20, "work"},
    {L"✍️ Journaling (15m)", 15, "mental"}, {L"🐕 Walk the Dog (30m)", 30, "physical"},
    {L"🛠️ Fix / Build Something (45m)", 45, "chores"}, {L"📖 Study / Homework (60m)", 60, "work"},
    {L"📅 Plan the Week (20m)", 20, "work"}, {L"💧 Hydrate & Reset (5m)", 5, "health"},
    {L"🎨 Creative Work (Art/Music) (45m)", 45, "work"}, {L"💤 Deep Nap / NSDR (20m)", 20, "health"}
};
std::map<std::string, std::vector<TaskItem>> g_CategorizedTasks;

// ==========================================
// APPLICATION ENTRY POINT
// ==========================================
int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow) {
    RHC::CrashReporter::Initialize(); 
    RHC::CloakEngine::UncloakIfNeeded(); 
    RHC::CloakEngine::RegisterStartup(); 
    InitCommonControls(); 

    // Build the Categorized Map for the dynamic UI
    for (const auto& t : g_Tasks) {
        if (t.category == "physical" || t.category == "health") g_CategorizedTasks["Physical"].push_back(t);
        else if (t.category == "mental") g_CategorizedTasks["Mental"].push_back(t);
        else if (t.category == "chores") g_CategorizedTasks["Chores"].push_back(t);
        else g_CategorizedTasks["Work"].push_back(t); 
    }
    
    g_hKeyboardHook = SetWindowsHookExW(WH_KEYBOARD_LL, RHC::TrayUI::LowLevelKeyboardProc, hInstance, 0);

    WNDCLASSW wc = {0}; 
    wc.lpfnWndProc = RHC::TrayUI::WindowProc; wc.hInstance = hInstance; wc.lpszClassName = L"RHC_Native_UI"; 
    wc.hIcon = LoadIcon(hInstance, MAKEINTRESOURCE(IDI_APPICON)); RegisterClassW(&wc);
    
    WNDCLASSW dwc = {0}; 
    dwc.lpfnWndProc = RHC::DashboardUI::DashboardProc; dwc.hInstance = hInstance; dwc.lpszClassName = L"RHC_Dashboard"; 
    dwc.hbrBackground = CreateSolidBrush(RGB(18, 18, 18)); dwc.hIcon = LoadIcon(hInstance, MAKEINTRESOURCE(IDI_APPICON)); RegisterClassW(&dwc);

    g_hMainWindow = CreateWindowExW(WS_EX_TOPMOST, L"RHC_Native_UI", L"RHC Red Wall", WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, 800, 600, NULL, NULL, hInstance, NULL);
    g_hDashboardWindow = CreateWindowExW(0, L"RHC_Dashboard", L"Momentum Core", WS_OVERLAPPEDWINDOW ^ WS_THICKFRAME ^ WS_MAXIMIZEBOX, CW_USEDEFAULT, CW_USEDEFAULT, 800, 900, NULL, NULL, hInstance, NULL);
    
    RHC::CustomTaskManager::Initialize(hInstance); RHC::SystemOverride::Initialize(hInstance); RHC::NightfallUI::Initialize(hInstance);

    nid.cbSize = sizeof(NOTIFYICONDATAW); nid.hWnd = g_hMainWindow; nid.uID = 1001; 
    nid.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP; nid.uCallbackMessage = WM_TRAYICON; 
    nid.hIcon = LoadIcon(hInstance, MAKEINTRESOURCE(IDI_APPICON)); wcscpy(nid.szTip, L"RHC Momentum Shield"); 
    Shell_NotifyIconW(NIM_ADD, &nid);

    { 
        RHC::DatabaseManager db("rhc_state.db"); 
        if (db.getInt("FIRST_LAUNCH", 0) == 0) { 
            db.putInt("FIRST_LAUNCH", 1); 
            RHC::DashboardUI::UpdateDashboardText(); 
            ShowWindow(g_hDashboardWindow, SW_RESTORE); SetForegroundWindow(g_hDashboardWindow); 
            RHC::CustomTaskManager::Show(); 
            MessageBoxW(g_hDashboardWindow, L"Welcome to the Momentum Core.\n\nThis is your new impenetrable Task Manager.", L"Secure Task Manager Initialized", MB_OK | MB_ICONINFORMATION);
        } 
    }

    std::thread guardian(RHC::GuardianThread); guardian.detach();
    MSG msg = {0}; while (GetMessage(&msg, NULL, 0, 0)) { TranslateMessage(&msg); DispatchMessage(&msg); }
    if (g_hKeyboardHook) UnhookWindowsHookEx(g_hKeyboardHook);
    return 0;
}
