#pragma once
#include <windows.h>

namespace RHC {
    namespace UI {
        // The app is system-DPI aware (app.manifest), so Windows no longer stretches
        // it on scaled displays. Every pixel size in code is written for 100%
        // (96 DPI) and goes through Scale() to come out the right size.
        class Dpi {
        public:
            static int Get() {
                static int dpi = [] {
                    HDC hdc = GetDC(NULL);
                    int d = hdc ? GetDeviceCaps(hdc, LOGPIXELSY) : 96;
                    if (hdc) ReleaseDC(NULL, hdc);
                    return d > 0 ? d : 96;
                }();
                return dpi;
            }

            static int Scale(int px) { return MulDiv(px, Get(), 96); }
            static float ScaleF(float px) { return px * Get() / 96.0f; }
        };
    }
}
