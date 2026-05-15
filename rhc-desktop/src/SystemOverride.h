#pragma once
#include <windows.h>

namespace RHC {
    namespace SystemOverride {
        void Initialize(HINSTANCE hInstance);
        void Show();
        HWND GetWindowHandle();
    }
}
