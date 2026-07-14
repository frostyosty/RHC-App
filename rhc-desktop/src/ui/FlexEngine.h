#pragma once
#include "UINode.h"

namespace RHC {
    namespace UI {
        class FlexEngine {
        public:
            // Recursively calculates structural bounding boxes for the node tree 
            // and translates coordinates to underlying child HWNDs.
            static void Arrange(UINode* root, const RECT& parentRect);
        };
    }
}
