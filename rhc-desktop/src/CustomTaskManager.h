#pragma once
#include <windows.h>

namespace RHC {
    namespace CustomTaskManager {
        void Initialize(HINSTANCE hInstance);
        void Show();
        void Hide();
        HWND GetWindowHandle();
    }
}
