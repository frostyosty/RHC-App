#pragma once

namespace RHC {
    namespace UI {
        enum class FlexDirection {
            Row,
            Column
        };

        enum class JustifyContent {
            Start,
            Center,
            End,
            SpaceBetween,
            SpaceAround
        };

        enum class AlignItems {
            Start,
            Center,
            End,
            Stretch
        };

        struct FlexLayout {
            FlexDirection direction = FlexDirection::Column;
            JustifyContent justifyContent = JustifyContent::Start;
            AlignItems alignItems = AlignItems::Start;
            int flexGrow = 0;      // 0 = fixed layout, > 0 = stretches relative to sibling nodes
            int width = -1;        // -1 = auto or flex-calculated
            int height = -1;       // -1 = auto or flex-calculated
        };
    }
}
