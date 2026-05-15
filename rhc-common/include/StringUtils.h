#pragma once
#include <string>
#include <vector>

namespace RHC {
    class StringUtils {
    public:
        static std::string toLower(const std::string& s);
        static std::vector<std::string> split(const std::string& s, char delimiter);
    };
}
