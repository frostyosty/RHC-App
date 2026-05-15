#pragma once
#include <windows.h>

namespace RHC {
    namespace UI {
        // Drop this into any WM_PAINT block to completely eliminate rendering flicker
        class DoubleBuffer {
        private:
            HDC hdcTarget;
            HDC hdcMem;
            HBITMAP hbmMem;
            HBITMAP hbmOld;
            RECT rcPaint;

        public:
            DoubleBuffer(HDC hdc, const RECT* prcPaint) : hdcTarget(hdc), rcPaint(*prcPaint) {
                hdcMem = CreateCompatibleDC(hdcTarget);
                hbmMem = CreateCompatibleBitmap(hdcTarget, rcPaint.right - rcPaint.left, rcPaint.bottom - rcPaint.top);
                hbmOld = (HBITMAP)SelectObject(hdcMem, hbmMem);
                // Adjust origin so drawing logic doesn't need to offset
                SetWindowOrgEx(hdcMem, rcPaint.left, rcPaint.top, NULL);
            }

            ~DoubleBuffer() {
                // Blast the memory buffer to the screen in a single operation
                BitBlt(hdcTarget, rcPaint.left, rcPaint.top, 
                       rcPaint.right - rcPaint.left, rcPaint.bottom - rcPaint.top,
                       hdcMem, rcPaint.left, rcPaint.top, SRCCOPY);
                       
                SelectObject(hdcMem, hbmOld);
                DeleteObject(hbmMem);
                DeleteDC(hdcMem);
            }

            HDC getDC() { return hdcMem; }
        };
    }
}
