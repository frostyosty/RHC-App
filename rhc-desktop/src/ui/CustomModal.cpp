#include "CustomModal.h"
#include "UINode.h"
#include "FlexEngine.h"
#include "CSSEngine.h"
#include "Renderer.h"
#include "SmoothButton.h"
#include "DoubleBuffer.h"

namespace RHC {
    namespace UI {
        static HWND g_hwndModalParent = NULL;
        static std::wstring g_modalTitle = L"";
        static std::wstring g_modalMessage = L"";
        static UINode* g_modalLayout = nullptr;
        static HWND g_hModalBtnOk = NULL;

        static LRESULT CALLBACK ModalProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
            switch (uMsg) {
                case WM_CREATE: {
                    // 1. Instantiate the action confirm child button
                    g_hModalBtnOk = CreateWindowExW(0, L"BUTTON", L"OK", WS_CHILD | WS_VISIBLE, 0, 0, 100, 35, hwnd, (HMENU)1, NULL, NULL);
                    SmoothButton::Attach(g_hModalBtnOk);
                    
                    // 2. Build the flex layouts tree for the modal window bounds
                    g_modalLayout = new UINode(hwnd);
                    CSSEngine::ParseInline("background: linear-gradient(#202124, #171717); border-radius: 12px; border: 1px solid #5f6368; padding: 20px; flex-direction: column; justify-content: space-between; align-items: center;", g_modalLayout->style, g_modalLayout->layout);

                    UINode* headerNode = new UINode();
                    CSSEngine::ParseInline("height: 40px; flex-direction: row; justify-content: center; align-items: center;", headerNode->style, headerNode->layout);
                    g_modalLayout->AddChild(headerNode);

                    UINode* bodyNode = new UINode();
                    CSSEngine::ParseInline("flex-grow: 1; flex-direction: column; justify-content: center; align-items: center; margin: 15px 0;", bodyNode->style, bodyNode->layout);
                    g_modalLayout->AddChild(bodyNode);

                    UINode* footerNode = new UINode(g_hModalBtnOk);
                    CSSEngine::ParseInline("width: 100px; height: 35px; flex-direction: row; justify-content: center; align-items: center;", footerNode->style, footerNode->layout);
                    g_modalLayout->AddChild(footerNode);

                    return 0;
                }
                case WM_SIZE: {
                    RECT rc; GetClientRect(hwnd, &rc);
                    if (g_modalLayout) {
                        FlexEngine::Arrange(g_modalLayout, rc);
                    }
                    return 0;
                }
                case WM_PAINT: {
                    PAINTSTRUCT ps;
                    HDC hdcPaint = BeginPaint(hwnd, &ps);
                    RECT rc; GetClientRect(hwnd, &rc);
                    {
                        DoubleBuffer buffer(hdcPaint, &rc);
                        HDC hdc = buffer.getDC();

                        // Render rounded backplane and child panels recursively
                        Renderer::DrawNode(hdc, g_modalLayout);

                        // Draw Modal Title using standard Segoe UI text bounds
                        HFONT hFontTitle = CreateFontW(20, 0, 0, 0, FW_BOLD, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
                        HGDIOBJ hOldFont = SelectObject(hdc, hFontTitle);
                        SetTextColor(hdc, RGB(255, 255, 255));
                        SetBkMode(hdc, TRANSPARENT);
                        
                        RECT titleRect = g_modalLayout->children[0]->calculatedBounds;
                        DrawTextW(hdc, g_modalTitle.c_str(), -1, &titleRect, DT_CENTER | DT_VCENTER | DT_SINGLELINE);

                        // Draw Modal Message Body (supports multiple lines)
                        HFONT hFontBody = CreateFontW(14, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
                        SelectObject(hdc, hFontBody);
                        SetTextColor(hdc, RGB(200, 200, 202));
                        
                        RECT bodyRect = g_modalLayout->children[1]->calculatedBounds;
                        DrawTextW(hdc, g_modalMessage.c_str(), -1, &bodyRect, DT_CENTER | DT_WORDBREAK);

                        SelectObject(hdc, hOldFont);
                        DeleteObject(hFontTitle);
                        DeleteObject(hFontBody);
                    }
                    EndPaint(hwnd, &ps);
                    return 0;
                }
                case WM_COMMAND: {
                    if (LOWORD(wParam) == 1) {
                        DestroyWindow(hwnd);
                    }
                    return 0;
                }
                case WM_CLOSE: {
                    DestroyWindow(hwnd);
                    return 0;
                }
                case WM_DESTROY: {
                    if (g_modalLayout) {
                        delete g_modalLayout;
                        g_modalLayout = nullptr;
                    }
                    if (g_hwndModalParent) {
                        EnableWindow(g_hwndModalParent, TRUE);
                        SetForegroundWindow(g_hwndModalParent);
                    }
                    return 0;
                }
            }
            return DefWindowProcW(hwnd, uMsg, wParam, lParam);
        }

        void CustomModal::Show(HWND hwndParent, const std::wstring& title, const std::wstring& message) {
            g_hwndModalParent = hwndParent;
            g_modalTitle = title;
            g_modalMessage = message;

            HINSTANCE hInst = (HINSTANCE)GetWindowLongPtrW(hwndParent, GWLP_HINSTANCE);

            static bool classRegistered = false;
            if (!classRegistered) {
                WNDCLASSW wc = {0};
                wc.lpfnWndProc = ModalProc;
                wc.hInstance = hInst;
                wc.lpszClassName = L"RHC_Modal";
                wc.hbrBackground = CreateSolidBrush(RGB(32, 33, 36));
                RegisterClassW(&wc);
                classRegistered = true;
            }

            RECT parentRect;
            GetWindowRect(hwndParent, &parentRect);
            int parentWidth = parentRect.right - parentRect.left;
            int parentHeight = parentRect.bottom - parentRect.top;
            int modalWidth = 440;
            int modalHeight = 240;
            int x = parentRect.left + (parentWidth - modalWidth) / 2;
            int y = parentRect.top + (parentHeight - modalHeight) / 2;

            HWND hwndModal = CreateWindowExW(WS_EX_TOPMOST | WS_EX_TOOLWINDOW, L"RHC_Modal", title.c_str(), WS_POPUP, x, y, modalWidth, modalHeight, hwndParent, NULL, hInst, NULL);
            
            if (hwndParent) {
                EnableWindow(hwndParent, FALSE);
            }

            ShowWindow(hwndModal, SW_SHOW);
            UpdateWindow(hwndModal);

            // Local synchronous Win32 block loop to simulate a native modal state
            MSG msg;
            while (GetMessageW(&msg, NULL, 0, 0)) {
                if (!IsDialogMessageW(hwndModal, &msg)) {
                    TranslateMessage(&msg);
                    DispatchMessageW(&msg);
                }
                if (!IsWindow(hwndModal)) break;
            }
        }
    }
}
