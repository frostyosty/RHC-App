#pragma once
#include <windows.h>

namespace RHC {
    namespace TrayUI {
        LRESULT CALLBACK WindowProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam);
    }
}
