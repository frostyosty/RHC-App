#pragma once
#include <windows.h>
#include <cmath>

namespace RHC {
    namespace UI {
        class Easing {
        public:
            // Standard Linear Interpolation
            static float Lerp(float a, float b, float t) {
                return a + (b - a) * t;
            }

            // Exponential Ease Out (Starts fast, smoothly slows down)
            static float EaseOutExp(float t) {
                return t == 1.0f ? 1.0f : 1.0f - std::pow(2.0f, -10.0f * t);
            }

            // Smoothly blend two RGB colors
            static COLORREF LerpColor(COLORREF c1, COLORREF c2, float t) {
                int r = Lerp(GetRValue(c1), GetRValue(c2), t);
                int g = Lerp(GetGValue(c1), GetGValue(c2), t);
                int b = Lerp(GetBValue(c1), GetBValue(c2), t);
                return RGB(r, g, b);
            }
        };
    }
}
