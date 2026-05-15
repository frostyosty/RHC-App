#pragma once
#include <windows.h>
#include <string>
#include <vector>
#include <map>

// --- SHARED WINDOW HANDLES ---
extern HWND g_hMainWindow;
extern HWND g_hDashboardWindow;
extern HWND g_hMomentumText;

extern HWND g_hComboAppBlock;
extern HWND g_hEditAppTime;
extern HWND g_hEditWebBlock;
extern HWND g_hEditWebTime;

extern HWND g_hListBlocks;
extern HWND g_hListEarned;
extern HWND g_hListSpent;

// Category Comboboxes
extern HWND g_hComboPhysical;
extern HWND g_hComboMental;
extern HWND g_hComboChores;
extern HWND g_hComboWork;

// --- SHARED SYSTEM HANDLES ---
extern HHOOK g_hKeyboardHook;
extern NOTIFYICONDATAW nid;

// --- SHARED STATE ---
extern std::wstring g_RedWallReason;
extern HFONT g_hFontTitle, g_hFontNormal, g_hFontEmoji, g_hFontSmall, g_hFontGiant;

struct TaskItem { 
    std::wstring name; 
    int cost; 
    std::string category; 
};
extern std::vector<TaskItem> g_Tasks;
extern std::map<std::string, std::vector<TaskItem>> g_CategorizedTasks;

// --- UI IDENTIFIERS ---
#define IDI_APPICON 101
#define WM_TRAYICON (WM_USER + 1)
#define ID_TRAY_EXIT 1001
#define ID_TRAY_OPEN 1002
#define ID_TRAY_TASKMGR 1003
#define ID_TRAY_OVERRIDE 1004
#define ID_TRAY_NIGHTFALL 1005

#define ID_BTN_ADD_APP 2004
#define ID_BTN_ADD_WEB 2005
#define ID_LIST_BLOCKS 2006

#define ID_COMBO_PHYSICAL 2008
#define ID_COMBO_MENTAL 2009
#define ID_COMBO_CHORES 2010
#define ID_COMBO_WORK 2011

#define ID_BTN_EXEC_PHYSICAL 2012
#define ID_BTN_EXEC_MENTAL 2013
#define ID_BTN_EXEC_CHORES 2014
#define ID_BTN_EXEC_WORK 2015

#define ID_LIST_EARNED 2020
#define ID_LIST_SPENT 2021
#define ID_BTN_OPEN_TASKMGR 2022

// --- NIGHTFALL BUTTONS ---
#define ID_BTN_NIGHT_SLEEP 4001
#define ID_BTN_NIGHT_SHUTDOWN 4002
