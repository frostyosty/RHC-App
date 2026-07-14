#pragma once
#include <windows.h>
#include <string>

namespace RHC {
    namespace UI {
        class CustomModal {
        public:
            // Spawns a custom-drawn, GDI+ antialiased, responsive dialog modal 
            // that blocks input to the calling parent HWND.
            static void Show(HWND hwndParent, const std::wstring& title, const std::wstring& message);
        };
    }
}
