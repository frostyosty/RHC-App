#pragma once
#include <windows.h>
#include <vector>
#include "UIStyle.h"
#include "FlexLayout.h"

namespace RHC {
    namespace UI {
        class UINode {
        public:
            HWND hwnd = NULL;                 // Associated Win32 control handle (if any)
            UIStyle style;
            FlexLayout layout;
            RECT calculatedBounds = {0, 0, 0, 0};
            std::vector<UINode*> children;

            UINode(HWND hw = NULL) : hwnd(hw) {}
            ~UINode() {
                for (auto child : children) {
                    delete child;
                }
            }

            void AddChild(UINode* child) {
                children.push_back(child);
            }
        };
    }
}
