#pragma once
#include <windows.h>

namespace RHC {
    namespace NightfallUI {
        void Initialize(HINSTANCE hInstance);
        void Show();
        HWND GetWindowHandle();
        void UpdateOverlay(int mode, int param);
    }
}
