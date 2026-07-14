#include "CSSEngine.h"
#include "include/StringUtils.h"
#include <sstream>
#include <algorithm>
#include <cctype>

namespace RHC {
    namespace UI {
        
        // Helper to trim trailing/leading spaces and trailing semicolons
        static std::string Trim(const std::string& str) {
            size_t first = str.find_first_not_of(" \t\r\n;");
            if (first == std::string::npos) return "";
            size_t last = str.find_last_not_of(" \t\r\n;");
            return str.substr(first, (last - first + 1));
        }

        // Helper to translate hexadecimal strings to win32 COLORREF formats
        static COLORREF ParseHexColor(std::string hex) {
            hex = Trim(hex);
            if (hex.empty()) return RGB(18, 18, 18);
            if (hex[0] == '#') hex = hex.substr(1);
            
            if (hex.length() == 3) {
                char r = hex[0], g = hex[1], b = hex[2];
                hex = std::string({r, r, g, g, b, b});
            }
            if (hex.length() == 6) {
                unsigned int r, g, b;
                std::stringstream ss;
                ss << std::hex << hex.substr(0, 2) << " " << hex.substr(2, 2) << " " << hex.substr(4, 2);
                if (ss >> r >> g >> b) {
                    return RGB(r, g, b);
                }
            }
            return RGB(18, 18, 18);
        }

        // Helper to convert shorthand dimensions (e.g. margin/padding bounds)
        static BoxModel ParseBoxModel(const std::string& val) {
            auto parts = RHC::StringUtils::split(val, ' ');
            std::vector<std::string> cleanParts;
            for (auto& p : parts) {
                std::string trimmed = Trim(p);
                if (!trimmed.empty()) cleanParts.push_back(trimmed);
            }

            auto toInt = [](const std::string& s) -> int {
                int num = 0;
                // Standard dimension parser extraction
                std::sscanf(s.c_str(), "%d", &num);
                return num;
            };

            BoxModel model = {0, 0, 0, 0};
            if (cleanParts.size() == 1) {
                int uniform = toInt(cleanParts[0]);
                model = {uniform, uniform, uniform, uniform};
            } else if (cleanParts.size() == 2) {
                int tb = toInt(cleanParts[0]);
                int lr = toInt(cleanParts[1]);
                model = {tb, lr, tb, lr};
            } else if (cleanParts.size() == 3) {
                int t  = toInt(cleanParts[0]);
                int lr = toInt(cleanParts[1]);
                int b  = toInt(cleanParts[2]);
                model = {t, lr, b, lr};
            } else if (cleanParts.size() >= 4) {
                int t = toInt(cleanParts[0]);
                int r = toInt(cleanParts[1]);
                int b = toInt(cleanParts[2]);
                int l = toInt(cleanParts[3]);
                model = {t, r, b, l};
            }
            return model;
        }

        void CSSEngine::ParseInline(const std::string& cssStr, UIStyle& style, FlexLayout& layout) {
            auto declarations = RHC::StringUtils::split(cssStr, ';');
            for (const auto& decl : declarations) {
                auto parts = RHC::StringUtils::split(decl, ':');
                if (parts.size() != 2) continue;

                std::string key = RHC::StringUtils::toLower(Trim(parts[0]));
                std::string val = Trim(parts[1]);

                if (key == "background" || key == "background-color") {
                    size_t gradientPos = val.find("linear-gradient(");
                    if (gradientPos != std::string::npos) {
                        size_t endPos = val.find(")", gradientPos);
                        if (endPos != std::string::npos) {
                            std::string inner = val.substr(gradientPos + 16, endPos - (gradientPos + 16));
                            auto colors = RHC::StringUtils::split(inner, ',');
                            if (colors.size() >= 2) {
                                style.backgroundGradient.enabled = true;
                                style.backgroundGradient.colorStart = ParseHexColor(colors[0]);
                                style.backgroundGradient.colorEnd = ParseHexColor(colors[1]);
                                style.backgroundGradient.angle = 90.0f; // vertical default
                            }
                        }
                    } else {
                        style.backgroundColor = ParseHexColor(val);
                        style.backgroundGradient.enabled = false;
                    }
                }
                else if (key == "color") {
                    style.textColor = ParseHexColor(val);
                }
                else if (key == "border-radius") {
                    int r = 0;
                    std::sscanf(val.c_str(), "%d", &r);
                    style.borderRadius = r;
                }
                else if (key == "margin") {
                    style.margin = ParseBoxModel(val);
                }
                else if (key == "padding") {
                    style.padding = ParseBoxModel(val);
                }
                else if (key == "flex-direction") {
                    std::string dir = RHC::StringUtils::toLower(val);
                    if (dir == "row") layout.direction = FlexDirection::Row;
                    else if (dir == "column") layout.direction = FlexDirection::Column;
                }
                else if (key == "justify-content") {
                    std::string j = RHC::StringUtils::toLower(val);
                    if (j == "flex-start" || j == "start") layout.justifyContent = JustifyContent::Start;
                    else if (j == "center") layout.justifyContent = JustifyContent::Center;
                    else if (j == "flex-end" || j == "end") layout.justifyContent = JustifyContent::End;
                    else if (j == "space-between") layout.justifyContent = JustifyContent::SpaceBetween;
                    else if (j == "space-around") layout.justifyContent = JustifyContent::SpaceAround;
                }
                else if (key == "align-items") {
                    std::string a = RHC::StringUtils::toLower(val);
                    if (a == "flex-start" || a == "start") layout.alignItems = AlignItems::Start;
                    else if (a == "center") layout.alignItems = AlignItems::Center;
                    else if (a == "flex-end" || a == "end") layout.alignItems = AlignItems::End;
                    else if (a == "stretch") layout.alignItems = AlignItems::Stretch;
                }
                else if (key == "flex-grow") {
                    int grow = 0;
                    std::sscanf(val.c_str(), "%d", &grow);
                    layout.flexGrow = grow;
                }
                else if (key == "width") {
                    int w = -1;
                    if (val.find('%') == std::string::npos) {
                        std::sscanf(val.c_str(), "%d", &w);
                    }
                    layout.width = w; // -1 represents flexible percentage bounds
                }
                else if (key == "height") {
                    int h = -1;
                    if (val.find('%') == std::string::npos) {
                        std::sscanf(val.c_str(), "%d", &h);
                    }
                    layout.height = h;
                }
                else if (key == "border" || key == "border-right" || key == "border-left" || key == "border-top" || key == "border-bottom") {
                    auto borderParts = RHC::StringUtils::split(val, ' ');
                    for (auto& bp : borderParts) {
                        std::string cleanBp = Trim(bp);
                        if (cleanBp.find('#') != std::string::npos) {
                            style.borderColor = ParseHexColor(cleanBp);
                        } else if (cleanBp.find("px") != std::string::npos || std::isdigit(cleanBp[0])) {
                            int w = 0;
                            std::sscanf(cleanBp.c_str(), "%d", &w);
                            style.borderWidth = w;
                        }
                    }
                }
            }
        }
    }
}
