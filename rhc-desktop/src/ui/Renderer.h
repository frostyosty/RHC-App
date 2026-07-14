#pragma once
#include <windows.h>
#include "UINode.h"

namespace RHC {
    namespace UI {
        class Renderer {
        private:
            static ULONG_PTR gdiplusToken;
        public:
            // High-Performance GDI+ graphics startup and shutdown
            static void Initialize();
            static void Shutdown();

            // Renders layout styles and backgrounds recursively onto a double-buffered DC
            static void DrawNode(HDC hdc, const UINode* node);
        };
    }
}
