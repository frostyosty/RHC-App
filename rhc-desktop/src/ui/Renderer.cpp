#include "Renderer.h"
#include <gdiplus.h>

namespace RHC {
    namespace UI {
        ULONG_PTR Renderer::gdiplusToken = 0;

        void Renderer::Initialize() {
            Gdiplus::GdiplusStartupInput gdiplusStartupInput;
            Gdiplus::GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, NULL);
        }

        void Renderer::Shutdown() {
            if (gdiplusToken) {
                Gdiplus::GdiplusShutdown(gdiplusToken);
            }
        }

        static Gdiplus::Color ToGdiColor(COLORREF cr) {
            return Gdiplus::Color(255, GetRValue(cr), GetGValue(cr), GetBValue(cr));
        }

        // Anti-aliased rounded box path. `inset` pulls the edges in, so a pen of
        // width 2*inset drawn on the path stays inside the box instead of being
        // half clipped and smeared across two pixels.
        static Gdiplus::GraphicsPath* CreateRoundedRectPath(const RECT& rect, int radius, Gdiplus::REAL inset) {
            Gdiplus::GraphicsPath* path = new Gdiplus::GraphicsPath();
            Gdiplus::REAL x = rect.left + inset;
            Gdiplus::REAL y = rect.top + inset;
            Gdiplus::REAL width = (rect.right - rect.left) - inset * 2;
            Gdiplus::REAL height = (rect.bottom - rect.top) - inset * 2;
            Gdiplus::REAL diameter = (radius - inset) * 2;

            if (diameter > width) diameter = width;
            if (diameter > height) diameter = height;

            if (diameter <= 0) {
                path->AddRectangle(Gdiplus::RectF(x, y, width, height));
                return path;
            }

            Gdiplus::RectF corner(x, y, diameter, diameter);

            // Top-Left Arc
            path->AddArc(corner, 180, 90);

            // Top-Right Arc
            corner.X = x + width - diameter;
            path->AddArc(corner, 270, 90);

            // Bottom-Right Arc
            corner.Y = y + height - diameter;
            path->AddArc(corner, 0, 90);

            // Bottom-Left Arc
            corner.X = x;
            path->AddArc(corner, 90, 90);

            path->CloseFigure();
            return path;
        }

        void Renderer::DrawNode(HDC hdc, const UINode* node) {
            if (!node) return;

            Gdiplus::Graphics graphics(hdc);
            graphics.SetSmoothingMode(Gdiplus::SmoothingModeAntiAlias);
            // Pixel centres sit at +0.5, so integer edges land on pixel boundaries
            // and straight edges stay sharp instead of blurring over two pixels.
            graphics.SetPixelOffsetMode(Gdiplus::PixelOffsetModeHalf);

            RECT rect = node->calculatedBounds;
            Gdiplus::GraphicsPath* path = CreateRoundedRectPath(rect, node->style.borderRadius, 0.0f);

            // 1. Fill container background (supports solid or multi-color gradients)
            if (node->style.backgroundGradient.enabled) {
                // One pixel larger than the box: GDI+ wraps the gradient at the
                // brush edge, which otherwise shows as a seam of the end colour.
                Gdiplus::LinearGradientBrush brush(
                    Gdiplus::Rect(rect.left - 1, rect.top - 1, rect.right - rect.left + 2, rect.bottom - rect.top + 2),
                    ToGdiColor(node->style.backgroundGradient.colorStart),
                    ToGdiColor(node->style.backgroundGradient.colorEnd),
                    Gdiplus::LinearGradientModeVertical
                );
                graphics.FillPath(&brush, path);
            } else if (node->style.hasBackground) {
                Gdiplus::SolidBrush brush(ToGdiColor(node->style.backgroundColor));
                graphics.FillPath(&brush, path);
            }
            delete path;

            // 2. Render antialiased border, drawn just inside the box
            if (node->style.borderWidth > 0) {
                Gdiplus::REAL bw = static_cast<Gdiplus::REAL>(node->style.borderWidth);
                Gdiplus::GraphicsPath* borderPath = CreateRoundedRectPath(rect, node->style.borderRadius, bw / 2);
                Gdiplus::Pen pen(ToGdiColor(node->style.borderColor), bw);
                graphics.DrawPath(&pen, borderPath);
                delete borderPath;
            }

            // 3. Render all descendant child node nodes recursively
            for (const auto child : node->children) {
                DrawNode(hdc, child);
            }
        }
    }
}
