#pragma once
#include <windows.h>

namespace RHC {
    namespace UI {
        struct BoxModel {
            int top = 0;
            int right = 0;
            int bottom = 0;
            int left = 0;
        };

        struct Gradient {
            COLORREF colorStart = RGB(18, 18, 18);
            COLORREF colorEnd = RGB(18, 18, 18);
            float angle = 0.0f; // in degrees
            bool enabled = false;
        };

        struct UIStyle {
            COLORREF backgroundColor = RGB(18, 18, 18);
            Gradient backgroundGradient;
            COLORREF borderColor = RGB(70, 70, 70);
            int borderWidth = 0;
            int borderRadius = 0; // rounded corners mapping
            COLORREF textColor = RGB(220, 220, 220);
            BoxModel padding;
            BoxModel margin;
        };
    }
}
