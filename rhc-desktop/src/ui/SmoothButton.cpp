#include "SmoothButton.h"
#include "DoubleBuffer.h"
#include "Dpi.h"
#include <commctrl.h>
#include <uxtheme.h>
#include <gdiplus.h>

namespace RHC {
    namespace UI {
        std::map<HWND, AnimState> SmoothButton::states;
        const UINT_PTR ANIM_TIMER_ID = 1001;
        const float BUTTON_RADIUS = 6.0f;

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
            
            // Owner-draw stops the stock button drawing itself directly on press and
            // focus changes (a one-frame flash of the light native button); WM_PAINT
            // below draws everything, and clicks still send BN_CLICKED.
            LONG_PTR style = GetWindowLongPtr(hWnd, GWL_STYLE);
            SetWindowLongPtr(hWnd, GWL_STYLE, (style & ~BS_TYPEMASK) | BS_OWNERDRAW);
            
            SetWindowSubclass(hWnd, SubclassProc, 1, 0);
        }

        LRESULT CALLBACK SmoothButton::SubclassProc(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam, UINT_PTR uIdSubclass, DWORD_PTR dwRefData) {
            AnimState& state = states[hWnd];

            switch (uMsg) {
                case WM_MOUSEMOVE: {
                    if (!state.isHovered) {
                        state.isHovered = true;
                        state.progress = 0.0f;
                        
                        TRACKMOUSEEVENT tme = { sizeof(tme), TME_LEAVE, hWnd, 0 };
                        TrackMouseEvent(&tme);
                        
                        SetTimer(hWnd, ANIM_TIMER_ID, 16, NULL);
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
                case WM_SETFOCUS:
                case WM_KILLFOCUS:
                case WM_UPDATEUISTATE: {
                    InvalidateRect(hWnd, NULL, FALSE); // focus ring on or off
                    break;
                }
                case WM_TIMER: {
                    if (wParam == ANIM_TIMER_ID) {
                        state.progress += 0.08f;
                        
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

                    {
                        DoubleBuffer buffer(hdcPaint, &rc);
                        HDC hdc = buffer.getDC();

                        // 1. Let the parent paint behind us, so the rounded corners
                        // show its background rather than a square of button colour
                        DrawThemeParentBackground(hWnd, hdc, &rc);

                        // 2. Rounded, anti-aliased body and 1px border drawn just inside the edge
                        {
                            Gdiplus::Graphics g(hdc);
                            g.SetSmoothingMode(Gdiplus::SmoothingModeAntiAlias);
                            g.SetPixelOffsetMode(Gdiplus::PixelOffsetModeHalf);

                            Gdiplus::REAL w = static_cast<Gdiplus::REAL>(rc.right - rc.left) - 1.0f;
                            Gdiplus::REAL h = static_cast<Gdiplus::REAL>(rc.bottom - rc.top) - 1.0f;
                            Gdiplus::REAL d = Dpi::ScaleF(BUTTON_RADIUS) * 2.0f;
                            if (d > w) d = w;
                            if (d > h) d = h;

                            Gdiplus::GraphicsPath path;
                            path.AddArc(0.5f, 0.5f, d, d, 180, 90);
                            path.AddArc(0.5f + w - d, 0.5f, d, d, 270, 90);
                            path.AddArc(0.5f + w - d, 0.5f + h - d, d, d, 0, 90);
                            path.AddArc(0.5f, 0.5f + h - d, d, d, 90, 90);
                            path.CloseFigure();

                            Gdiplus::SolidBrush body(Gdiplus::Color(255, GetRValue(state.currentBg), GetGValue(state.currentBg), GetBValue(state.currentBg)));
                            g.FillPath(&body, &path);
                            // Keyboard focus shows as the hover border, only once the keyboard has
                            // been used (UISF_HIDEFOCUS), so a clicked button doesn't stay lit
                            bool showFocus = GetFocus() == hWnd && !(SendMessage(hWnd, WM_QUERYUISTATE, 0, 0) & UISF_HIDEFOCUS);
                            COLORREF borderCol = showFocus ? state.theme.borderHover : state.currentBorder;
                            Gdiplus::Pen border(Gdiplus::Color(255, GetRValue(borderCol), GetGValue(borderCol), GetBValue(borderCol)), 1.0f);
                            g.DrawPath(&border, &path);
                        }

                        // 4. Draw button text
                        wchar_t text[256];
                        GetWindowTextW(hWnd, text, 256);
                        
                        // No WM_SETFONT means the bitmap "System" font, so fall back to Segoe UI
                        static HFONT hDefaultFont = CreateFontW(Dpi::Scale(16), 0, 0, 0, FW_SEMIBOLD, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
                        HFONT hFont = (HFONT)SendMessage(hWnd, WM_GETFONT, 0, 0);
                        if (!hFont) hFont = hDefaultFont;
                        HGDIOBJ hOldFont = SelectObject(hdc, hFont);
                        
                        SetTextColor(hdc, state.isHovered ? state.theme.textHover : state.theme.textNormal);
                        SetBkMode(hdc, TRANSPARENT);
                        
                        RECT textRect = rc;
                        if (state.isPressed) { textRect.top += 2; textRect.left += 2; }
                        
                        DrawTextW(hdc, text, -1, &textRect, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
                        SelectObject(hdc, hOldFont);
                    }

                    EndPaint(hWnd, &ps);
                    return 0;
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
