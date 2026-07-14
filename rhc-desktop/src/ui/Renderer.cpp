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

        // Custom GDI+ GraphicsPath generator for perfect anti-aliased rounded boxes
        static Gdiplus::GraphicsPath* CreateRoundedRectPath(const RECT& rect, int radius) {
            Gdiplus::GraphicsPath* path = new Gdiplus::GraphicsPath();
            int x = rect.left;
            int y = rect.top;
            int width = rect.right - rect.left;
            int height = rect.bottom - rect.top;
            int diameter = radius * 2;

            if (diameter > width) diameter = width;
            if (diameter > height) diameter = height;

            if (diameter <= 0) {
                path->AddRectangle(Gdiplus::Rect(x, y, width, height));
                return path;
            }

            Gdiplus::Rect corner(x, y, diameter, diameter);

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

            RECT rect = node->calculatedBounds;
            Gdiplus::GraphicsPath* path = CreateRoundedRectPath(rect, node->style.borderRadius);

            // 1. Fill container background (supports solid or multi-color gradients)
            if (node->style.backgroundGradient.enabled) {
                Gdiplus::LinearGradientBrush brush(
                    Gdiplus::Rect(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top),
                    ToGdiColor(node->style.backgroundGradient.colorStart),
                    ToGdiColor(node->style.backgroundGradient.colorEnd),
                    Gdiplus::LinearGradientModeVertical
                );
                graphics.FillPath(&brush, path);
            } else {
                Gdiplus::SolidBrush brush(ToGdiColor(node->style.backgroundColor));
                graphics.FillPath(&brush, path);
            }

            // 2. Render antialiased border boundary line
            if (node->style.borderWidth > 0) {
                Gdiplus::Pen pen(ToGdiColor(node->style.borderColor), static_cast<Gdiplus::REAL>(node->style.borderWidth));
                graphics.DrawPath(&pen, path);
            }

            delete path;

            // 3. Render all descendant child node nodes recursively
            for (const auto child : node->children) {
                DrawNode(hdc, child);
            }
        }
    }
}
