#pragma once
#include <windows.h>
#include <map>
#include "Easing.h"

namespace RHC {
    namespace UI {
        struct Theme {
            COLORREF bgNormal = RGB(40, 40, 42);
            COLORREF bgHover  = RGB(60, 60, 65);
            COLORREF bgPress  = RGB(20, 20, 22);
            COLORREF textNormal = RGB(220, 220, 220);
            COLORREF textHover  = RGB(255, 255, 255);
            COLORREF borderNormal = RGB(70, 70, 70);
            COLORREF borderHover  = RGB(90, 180, 90);
        };

        struct AnimState {
            Theme theme;
            COLORREF currentBg;
            COLORREF currentBorder;
            float progress; // 0.0f to 1.0f
            
            bool isHovered;
            bool isPressed;
            bool isAnimating;
        };

        class SmoothButton {
        private:
            static std::map<HWND, AnimState> states;
            static LRESULT CALLBACK SubclassProc(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam, UINT_PTR uIdSubclass, DWORD_PTR dwRefData);

        public:
            // Call this on your standard Win32 Buttons to transform them into smooth animated engine components
            static void Attach(HWND hWnd, Theme customTheme = Theme());
        };
    }
}
