#include "SmoothButton.h"
#include "DoubleBuffer.h"
#include <commctrl.h>

namespace RHC {
    namespace UI {
        std::map<HWND, AnimState> SmoothButton::states;
        const UINT_PTR ANIM_TIMER_ID = 1001;

        void SmoothButton::Attach(HWND hWnd, Theme customTheme) {
            AnimState state = {0};
            state.theme = customTheme;
            state.currentBg = customTheme.bgNormal;
            state.currentBorder = customTheme.borderNormal;
            state.progress = 1.0f;
            state.isHovered = false;
            state.isPressed = false;
            state.isAnimating = false;
            
            states[hWnd] = state;
            
            // Remove BS_OWNERDRAW if set, we will hijack WM_PAINT directly for total control
            LONG_PTR style = GetWindowLongPtr(hWnd, GWL_STYLE);
            SetWindowLongPtr(hWnd, GWL_STYLE, style & ~BS_OWNERDRAW);
            
            SetWindowSubclass(hWnd, SubclassProc, 1, 0);
        }

        LRESULT CALLBACK SmoothButton::SubclassProc(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam, UINT_PTR uIdSubclass, DWORD_PTR dwRefData) {
            AnimState& state = states[hWnd];

            switch (uMsg) {
                case WM_MOUSEMOVE: {
                    if (!state.isHovered) {
                        state.isHovered = true;
                        state.progress = 0.0f; // Reset animation progress
                        
                        TRACKMOUSEEVENT tme = { sizeof(tme), TME_LEAVE, hWnd, 0 };
                        TrackMouseEvent(&tme);
                        
                        SetTimer(hWnd, ANIM_TIMER_ID, 16, NULL); // ~60 FPS
                    }
                    break;
                }
                case WM_MOUSELEAVE: {
                    state.isHovered = false;
                    state.progress = 0.0f;
                    SetTimer(hWnd, ANIM_TIMER_ID, 16, NULL);
                    break;
                }
                case WM_LBUTTONDOWN: {
                    state.isPressed = true;
                    state.progress = 0.0f;
                    SetTimer(hWnd, ANIM_TIMER_ID, 16, NULL);
                    break;
                }
                case WM_LBUTTONUP: {
                    state.isPressed = false;
                    state.progress = 0.0f;
                    SetTimer(hWnd, ANIM_TIMER_ID, 16, NULL);
                    break;
                }
                case WM_TIMER: {
                    if (wParam == ANIM_TIMER_ID) {
                        state.progress += 0.08f; // Animation Speed
                        
                        if (state.progress >= 1.0f) {
                            state.progress = 1.0f;
                            KillTimer(hWnd, ANIM_TIMER_ID);
                        }
                        
                        COLORREF targetBg = state.isPressed ? state.theme.bgPress : (state.isHovered ? state.theme.bgHover : state.theme.bgNormal);
                        COLORREF targetBorder = state.isHovered ? state.theme.borderHover : state.theme.borderNormal;
                        
                        float easedT = Easing::EaseOutExp(state.progress);
                        state.currentBg = Easing::LerpColor(state.currentBg, targetBg, easedT);
                        state.currentBorder = Easing::LerpColor(state.currentBorder, targetBorder, easedT);
                        
                        InvalidateRect(hWnd, NULL, FALSE);
                    }
                    break;
                }
                case WM_PAINT: {
                    PAINTSTRUCT ps;
                    HDC hdcPaint = BeginPaint(hWnd, &ps);
                    
                    RECT rc;
                    GetClientRect(hWnd, &rc);

                    // DOUBLE BUFFERING - All drawing happens in memory to prevent flicker
                    {
                        DoubleBuffer buffer(hdcPaint, &rc);
                        HDC hdc = buffer.getDC();

                        // 1. Draw Background
                        HBRUSH hBrush = CreateSolidBrush(state.currentBg);
                        FillRect(hdc, &rc, hBrush);
                        DeleteObject(hBrush);

                        // 2. Draw Border
                        HPEN hPen = CreatePen(PS_SOLID, 1, state.currentBorder);
                        HGDIOBJ hOldPen = SelectObject(hdc, hPen);
                        HBRUSH hOldBrush = (HBRUSH)SelectObject(hdc, GetStockObject(NULL_BRUSH));
                        Rectangle(hdc, rc.left, rc.top, rc.right, rc.bottom);
                        SelectObject(hdc, hOldPen);
                        SelectObject(hdc, hOldBrush);
                        DeleteObject(hPen);

                        // 3. Draw Text
                        wchar_t text[256];
                        GetWindowTextW(hWnd, text, 256);
                        
                        HFONT hFont = (HFONT)SendMessage(hWnd, WM_GETFONT, 0, 0);
                        HGDIOBJ hOldFont = SelectObject(hdc, hFont);
                        
                        SetTextColor(hdc, state.isHovered ? state.theme.textHover : state.theme.textNormal);
                        SetBkMode(hdc, TRANSPARENT);
                        
                        // Slightly shift text down if pressed for tactile feel
                        RECT textRect = rc;
                        if (state.isPressed) { textRect.top += 2; textRect.left += 2; }
                        
                        DrawTextW(hdc, text, -1, &textRect, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
                        SelectObject(hdc, hOldFont);
                    } // Buffer is blasted to screen here

                    EndPaint(hWnd, &ps);
                    return 0; // We handled painting, don't pass to DefSubclassProc
                }
                case WM_DESTROY: {
                    states.erase(hWnd);
                    RemoveWindowSubclass(hWnd, SubclassProc, 1);
                    break;
                }
            }
            return DefSubclassProc(hWnd, uMsg, wParam, lParam);
        }
    }
}
