#include "NightfallUI.h"
#include "ui/CustomModal.h"
#include "include/DatabaseManager.h"
#include <cstdio>

namespace RHC {
    namespace NightfallUI {
        HWND hwnd = NULL; HWND hSleepEdit = NULL; HWND hWakeEdit = NULL;
        HWND g_hOverlay = NULL; int g_OverlayMode = 0;

        LRESULT CALLBACK OverlayProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
            switch(uMsg) {
                case WM_ERASEBKGND: return 1;
                case WM_PAINT: {
                    PAINTSTRUCT ps; HDC hdc = BeginPaint(hwnd, &ps);
                    HBRUSH hBrush = CreateSolidBrush(RGB(0,0,0)); FillRect(hdc, &ps.rcPaint, hBrush);
                    DeleteObject(hBrush); EndPaint(hwnd, &ps); return 0;
                }
                case WM_TIMER: {
                    if (g_OverlayMode == 2) {
                        POINT pt; GetCursorPos(&pt);
                        static POINT lastPt = {-1, -1};
                        if (pt.x != lastPt.x || pt.y != lastPt.y) {
                            lastPt = pt;
                            int vx = GetSystemMetrics(SM_XVIRTUALSCREEN); int vy = GetSystemMetrics(SM_YVIRTUALSCREEN);
                            int vw = GetSystemMetrics(SM_CXVIRTUALSCREEN); int vh = GetSystemMetrics(SM_CYVIRTUALSCREEN);
                            
                            HRGN hRgn = CreateRectRgn(vx, vy, vx + vw, vy + vh);
                            // Flashlight Hole Radius = 75px
                            HRGN hHole = CreateEllipticRgn(pt.x - 75, pt.y - 75, pt.x + 75, pt.y + 75);
                            CombineRgn(hRgn, hRgn, hHole, RGN_DIFF);
                            SetWindowRgn(hwnd, hRgn, TRUE);
                            DeleteObject(hHole); // hRgn is now owned by the system
                        }
                    }
                    return 0;
                }
                case WM_NCHITTEST: return HTCLIENT; // Eat clicks outside the hole
            }
            return DefWindowProcA(hwnd, uMsg, wParam, lParam);
        }

        void UpdateOverlay(int mode, int param) {
            if (!g_hOverlay) return;
            if (g_OverlayMode != mode) {
                g_OverlayMode = mode;
                if (mode == 0) {
                    KillTimer(g_hOverlay, 1); ShowWindow(g_hOverlay, SW_HIDE);
                } else if (mode == 1) {
                    KillTimer(g_hOverlay, 1);
                    SetWindowLong(g_hOverlay, GWL_EXSTYLE, WS_EX_TOPMOST | WS_EX_TOOLWINDOW | WS_EX_LAYERED | WS_EX_TRANSPARENT);
                    SetWindowRgn(g_hOverlay, NULL, TRUE);
                    SetWindowPos(g_hOverlay, HWND_TOPMOST, GetSystemMetrics(SM_XVIRTUALSCREEN), GetSystemMetrics(SM_YVIRTUALSCREEN), GetSystemMetrics(SM_CXVIRTUALSCREEN), GetSystemMetrics(SM_CYVIRTUALSCREEN), SWP_NOACTIVATE);
                    ShowWindow(g_hOverlay, SW_SHOWNA);
                } else if (mode == 2) {
                    SetWindowLong(g_hOverlay, GWL_EXSTYLE, WS_EX_TOPMOST | WS_EX_TOOLWINDOW | WS_EX_LAYERED);
                    SetLayeredWindowAttributes(g_hOverlay, 0, 255, LWA_ALPHA);
                    SetWindowPos(g_hOverlay, HWND_TOPMOST, GetSystemMetrics(SM_XVIRTUALSCREEN), GetSystemMetrics(SM_YVIRTUALSCREEN), GetSystemMetrics(SM_CXVIRTUALSCREEN), GetSystemMetrics(SM_CYVIRTUALSCREEN), SWP_NOACTIVATE);
                    SetTimer(g_hOverlay, 1, 16, NULL); // 60 FPS Flashlight Tracking
                    ShowWindow(g_hOverlay, SW_SHOWNA);
                }
            }
            if (mode == 1) SetLayeredWindowAttributes(g_hOverlay, 0, param, LWA_ALPHA);
        }

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
                            RHC::UI::CustomModal::Show(hwnd, L"Success", L"Nightfall Schedule Locked!"); ShowWindow(hwnd, SW_HIDE);
                        } else RHC::UI::CustomModal::Show(hwnd, L"Error", L"Invalid Format! Please use HH:MM (e.g. 22:30)");
                    } return 0;
                case WM_CTLCOLORSTATIC: SetTextColor((HDC)wParam, RGB(255, 255, 255)); SetBkColor((HDC)wParam, RGB(18, 18, 18)); return (LRESULT)CreateSolidBrush(RGB(18, 18, 18));
                case WM_CLOSE: ShowWindow(hwnd, SW_HIDE); return 0;
            }
            return DefWindowProcA(hwnd, uMsg, wParam, lParam);
        }

        void Initialize(HINSTANCE hInstance) {
            WNDCLASSA wc = {0}; wc.lpfnWndProc = WndProc; wc.hInstance = hInstance; wc.lpszClassName = "RHC_Nightfall"; wc.hbrBackground = CreateSolidBrush(RGB(18, 18, 18)); RegisterClassA(&wc);
            hwnd = CreateWindowExA(0, "RHC_Nightfall", "Nightfall Schedule", WS_OVERLAPPEDWINDOW ^ WS_THICKFRAME ^ WS_MAXIMIZEBOX, CW_USEDEFAULT, CW_USEDEFAULT, 300, 300, NULL, NULL, hInstance, NULL);

            WNDCLASSA owc = {0}; owc.lpfnWndProc = OverlayProc; owc.hInstance = hInstance; owc.lpszClassName = "RHC_Overlay"; RegisterClassA(&owc);
            g_hOverlay = CreateWindowExA(WS_EX_TOPMOST | WS_EX_TOOLWINDOW | WS_EX_LAYERED, "RHC_Overlay", "", WS_POPUP, 0, 0, 10, 10, NULL, NULL, hInstance, NULL);
        }
        void Show() { ShowWindow(hwnd, SW_RESTORE); SetForegroundWindow(hwnd); }
        HWND GetWindowHandle() { return hwnd; }
    }
}
