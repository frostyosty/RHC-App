#pragma once
#include <string>

namespace RHC {
    namespace Utils {
        std::wstring utf8_to_wstring(const std::string& str);
        std::string wstring_to_utf8(const std::wstring& wstr);
        bool IsTimeAllowed(const std::string& timeWindow);
        void InjectEvadeAction();
    }
}
