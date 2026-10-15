#pragma once
#include "UINode.h"

namespace RHC {
    namespace UI {
        class FlexEngine {
        public:
            // Extra window height given to combo boxes for their drop-down list
            static const int COMBO_DROP_HEIGHT = 200;

            // Recursively calculates structural bounding boxes for the node tree 
            // and translates coordinates to underlying child HWNDs.
            static void Arrange(UINode* root, const RECT& parentRect);
        };
    }
}
