#pragma once
#include <string>
#include "UIStyle.h"
#include "FlexLayout.h"

namespace RHC {
    namespace UI {
        class CSSEngine {
        public:
            // Parse a CSS style declaration string and apply properties 
            // directly to the target style and layout configurations.
            static void ParseInline(const std::string& cssStr, UIStyle& style, FlexLayout& layout);
        };
    }
}
