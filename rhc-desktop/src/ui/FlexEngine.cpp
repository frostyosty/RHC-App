#include "FlexEngine.h"
#include <algorithm>

namespace RHC {
    namespace UI {
        void FlexEngine::Arrange(UINode* root, const RECT& parentRect) {
            if (!root) return;

            // 1. Assign local calculated dimensions
            root->calculatedBounds = parentRect;

            if (root->hwnd) {
                // Update physical Win32 control placement on screen
                SetWindowPos(root->hwnd, NULL, 
                             root->calculatedBounds.left, root->calculatedBounds.top,
                             root->calculatedBounds.right - root->calculatedBounds.left,
                             root->calculatedBounds.bottom - root->calculatedBounds.top,
                             SWP_NOZORDER | SWP_NOACTIVATE);
            }

            if (root->children.empty()) return;

            // 2. Adjust client area boundary calculations based on padding values
            int clientLeft   = root->calculatedBounds.left + root->style.padding.left;
            int clientTop    = root->calculatedBounds.top + root->style.padding.top;
            int clientRight  = root->calculatedBounds.right - root->style.padding.right;
            int clientBottom = root->calculatedBounds.bottom - root->style.padding.bottom;
            int clientWidth  = clientRight - clientLeft;
            int clientHeight = clientBottom - clientTop;

            if (clientWidth <= 0 || clientHeight <= 0) return;

            bool isRow = root->layout.direction == FlexDirection::Row;

            // Struct to store calculations for the arranging passes
            struct ChildMeasure {
                UINode* node;
                int mainSize;
                int crossSize;
            };
            std::vector<ChildMeasure> measures;

            int totalFlexGrow = 0;
            int allocatedMainSize = 0;

            // Pass 1: Measure explicit non-flex components and gather remaining flex capacity
            for (auto child : root->children) {
                int mainSize = 0;
                int crossSize = 0;

                int mMain = isRow ? (child->style.margin.left + child->style.margin.right)
                                  : (child->style.margin.top + child->style.margin.bottom);
                int mCross = isRow ? (child->style.margin.top + child->style.margin.bottom)
                                   : (child->style.margin.left + child->style.margin.right);

                if (isRow) {
                    mainSize = (child->layout.width >= 0) ? child->layout.width : 0;
                    crossSize = (child->layout.height >= 0) ? child->layout.height : (clientHeight - mCross);
                } else {
                    mainSize = (child->layout.height >= 0) ? child->layout.height : 0;
                    crossSize = (child->layout.width >= 0) ? child->layout.width : (clientWidth - mCross);
                }

                measures.push_back({ child, mainSize, crossSize });
                
                if (child->layout.flexGrow > 0) {
                    totalFlexGrow += child->layout.flexGrow;
                } else {
                    allocatedMainSize += mainSize + mMain;
                }
            }

            // Pass 2: Distribute remaining main-axis viewport space to flex nodes
            int totalMainSize = isRow ? clientWidth : clientHeight;
            int remainingMainSize = std::max(0, totalMainSize - allocatedMainSize);

            for (auto& measure : measures) {
                if (measure.node->layout.flexGrow > 0) {
                    int mMain = isRow ? (measure.node->style.margin.left + measure.node->style.margin.right)
                                      : (measure.node->style.margin.top + measure.node->style.margin.bottom);
                    measure.mainSize = (remainingMainSize * measure.node->layout.flexGrow) / totalFlexGrow - mMain;
                    measure.mainSize = std::max(0, measure.mainSize);
                }
            }

            // Pass 3: Evaluate overall size for justification computations
            int accumulatedMainSize = 0;
            for (const auto& measure : measures) {
                int mMain = isRow ? (measure.node->style.margin.left + measure.node->style.margin.right)
                                  : (measure.node->style.margin.top + measure.node->style.margin.bottom);
                accumulatedMainSize += measure.mainSize + mMain;
            }

            int startOffset = 0;
            int spaceBetween = 0;
            int actualRemaining = totalMainSize - accumulatedMainSize;
            int childCount = static_cast<int>(root->children.size());

            if (actualRemaining > 0) {
                if (root->layout.justifyContent == JustifyContent::Center) {
                    startOffset = actualRemaining / 2;
                } else if (root->layout.justifyContent == JustifyContent::End) {
                    startOffset = actualRemaining;
                } else if (root->layout.justifyContent == JustifyContent::SpaceBetween && childCount > 1) {
                    spaceBetween = actualRemaining / (childCount - 1);
                } else if (root->layout.justifyContent == JustifyContent::SpaceAround) {
                    spaceBetween = actualRemaining / (childCount + 1);
                    startOffset = spaceBetween;
                }
            }

            int currentMainPos = (isRow ? clientLeft : clientTop) + startOffset;

            // Pass 4: Map final absolute layout coordinates
            for (const auto& measure : measures) {
                RECT childRect = {0, 0, 0, 0};
                
                int marginLeft   = measure.node->style.margin.left;
                int marginRight  = measure.node->style.margin.right;
                int marginTop    = measure.node->style.margin.top;
                int marginBottom = measure.node->style.margin.bottom;

                int mainStart = currentMainPos + (isRow ? marginLeft : marginTop);
                int mainEnd   = mainStart + measure.mainSize;

                int crossStart       = isRow ? clientTop : clientLeft;
                int crossEnd         = isRow ? clientBottom : clientRight;
                int crossLimit       = isRow ? clientHeight : clientWidth;
                int crossMarginStart = isRow ? marginTop : marginLeft;
                int crossMarginEnd   = isRow ? marginBottom : marginRight;

                int crossSize = measure.crossSize;
                if (measure.node->layout.alignItems == AlignItems::Stretch || 
                    root->layout.alignItems == AlignItems::Stretch) {
                    crossSize = crossLimit - (crossMarginStart + crossMarginEnd);
                }

                int crossPos = crossStart + crossMarginStart;
                int availableCrossSpace = crossLimit - (crossMarginStart + crossMarginEnd + crossSize);

                if (availableCrossSpace > 0) {
                    auto align = (measure.node->layout.alignItems != AlignItems::Start) 
                                 ? measure.node->layout.alignItems 
                                 : root->layout.alignItems;
                    if (align == AlignItems::Center) {
                        crossPos += availableCrossSpace / 2;
                    } else if (align == AlignItems::End) {
                        crossPos += availableCrossSpace;
                    }
                }

                if (isRow) {
                    childRect.left   = mainStart;
                    childRect.right  = mainEnd;
                    childRect.top    = crossPos;
                    childRect.bottom = crossPos + crossSize;
                } else {
                    childRect.top    = mainStart;
                    childRect.bottom = mainEnd;
                    childRect.left   = crossPos;
                    childRect.right  = crossPos + crossSize;
                }

                // Recursively layout sub-nodes using finalized absolute box bounds
                Arrange(measure.node, childRect);

                int mMain = isRow ? (marginLeft + marginRight) : (marginTop + marginBottom);
                currentMainPos += measure.mainSize + mMain + spaceBetween;
            }
        }
    }
}
