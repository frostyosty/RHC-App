#include "NightfallUI.h"
#include "include/DatabaseManager.h"
#include <cstdio>

namespace RHC {
    namespace NightfallUI {
        HWND hwnd = NULL; HWND hSleepEdit = NULL; HWND hWakeEdit = NULL;
        
        LRESULT CALLBACK WndProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
            switch (uMsg) {
                case WM_CREATE: {
                    HFONT hFont = CreateFontA(16, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, "Segoe UI");
                    CreateWindowExA(0, "STATIC", "NIGHTFALL PROTOCOL", WS_CHILD | WS_VISIBLE | SS_CENTER, 0, 20, 300, 30, hwnd, NULL, NULL, NULL);
                    CreateWindowExA(0, "STATIC", "Sleep Time (HH:MM 24h):", WS_CHILD | WS_VISIBLE, 30, 60, 240, 20, hwnd, NULL, NULL, NULL);
                    hSleepEdit = CreateWindowExA(WS_EX_CLIENTEDGE, "EDIT", "22:30", WS_CHILD | WS_VISIBLE, 30, 80, 220, 30, hwnd, NULL, NULL, NULL);
                    CreateWindowExA(0, "STATIC", "Wake Time (HH:MM 24h):", WS_CHILD | WS_VISIBLE, 30, 120, 240, 20, hwnd, NULL, NULL, NULL);
                    hWakeEdit = CreateWindowExA(WS_EX_CLIENTEDGE, "EDIT", "06:00", WS_CHILD | WS_VISIBLE, 30, 140, 220, 30, hwnd, NULL, NULL, NULL);
                    HWND hBtn = CreateWindowExA(0, "BUTTON", "Lock Schedule", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 30, 190, 220, 40, hwnd, (HMENU)1, NULL, NULL);
                    
                    EnumChildWindows(hwnd,[](HWND child, LPARAM font) { SendMessage(child, WM_SETFONT, font, TRUE); return TRUE; }, (LPARAM)hFont);
                    
                    RHC::DatabaseManager db("rhc_state.db");
                    int nStart = db.getInt("NIGHTFALL_START", -1); int nEnd = db.getInt("NIGHTFALL_END", -1);
                    if(nStart != -1) { char buf[10]; sprintf(buf, "%02d:%02d", nStart/60, nStart%60); SetWindowTextA(hSleepEdit, buf); }
                    if(nEnd != -1) { char buf[10]; sprintf(buf, "%02d:%02d", nEnd/60, nEnd%60); SetWindowTextA(hWakeEdit, buf); }
                    return 0;
                }
                case WM_COMMAND:
                    if (LOWORD(wParam) == 1) {
                        char sBuf[10], wBuf[10]; GetWindowTextA(hSleepEdit, sBuf, 10); GetWindowTextA(hWakeEdit, wBuf, 10);
                        int sH, sM, wH, wM;
                        if (sscanf(sBuf, "%d:%d", &sH, &sM) == 2 && sscanf(wBuf, "%d:%d", &wH, &wM) == 2) {
                            RHC::DatabaseManager db("rhc_state.db"); db.putInt("NIGHTFALL_START", sH * 60 + sM); db.putInt("NIGHTFALL_END", wH * 60 + wM);
                            MessageBoxA(hwnd, "Nightfall Schedule Locked!", "Success", MB_OK | MB_ICONINFORMATION); ShowWindow(hwnd, SW_HIDE);
                        } else MessageBoxA(hwnd, "Invalid Format! Please use HH:MM (e.g. 22:30)", "Error", MB_ICONERROR);
                    } return 0;
                case WM_CTLCOLORSTATIC: SetTextColor((HDC)wParam, RGB(255, 255, 255)); SetBkColor((HDC)wParam, RGB(18, 18, 18)); return (LRESULT)CreateSolidBrush(RGB(18, 18, 18));
                case WM_CLOSE: ShowWindow(hwnd, SW_HIDE); return 0;
            }
            return DefWindowProcA(hwnd, uMsg, wParam, lParam);
        }
        void Initialize(HINSTANCE hInstance) {
            WNDCLASSA wc = {0}; wc.lpfnWndProc = WndProc; wc.hInstance = hInstance; wc.lpszClassName = "RHC_Nightfall"; wc.hbrBackground = CreateSolidBrush(RGB(18, 18, 18)); RegisterClassA(&wc);
            hwnd = CreateWindowExA(0, "RHC_Nightfall", "Nightfall Schedule", WS_OVERLAPPEDWINDOW ^ WS_THICKFRAME ^ WS_MAXIMIZEBOX, CW_USEDEFAULT, CW_USEDEFAULT, 300, 300, NULL, NULL, hInstance, NULL);
        }
        void Show() { ShowWindow(hwnd, SW_RESTORE); SetForegroundWindow(hwnd); }
        HWND GetWindowHandle() { return hwnd; }
    }
}
