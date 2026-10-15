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

        // How a node places itself on its parent's cross axis. Auto follows the
        // parent's align-items, as in CSS.
        enum class AlignSelf {
            Auto,
            Start,
            Center,
            End,
            Stretch
        };

        struct FlexLayout {
            FlexDirection direction = FlexDirection::Column;
            JustifyContent justifyContent = JustifyContent::Start;
            AlignItems alignItems = AlignItems::Start;   // how this node aligns its children
            AlignSelf alignSelf = AlignSelf::Auto;        // how this node aligns itself in its parent
            int flexGrow = 0;      // 0 = fixed layout, > 0 = stretches relative to sibling nodes
            int width = -1;        // -1 = auto or flex-calculated
            int height = -1;       // -1 = auto or flex-calculated
        };
    }
}
