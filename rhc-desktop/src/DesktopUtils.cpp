#include "DesktopUtils.h"
#include <windows.h>
#include <ctime>
#include "../../rhc-common/include/StringUtils.h"

namespace RHC {
    namespace Utils {

        std::wstring utf8_to_wstring(const std::string& str) {
            if (str.empty()) return std::wstring();
            int size = MultiByteToWideChar(CP_UTF8, 0, &str[0], (int)str.size(), NULL, 0);
            std::wstring wstr(size, 0); 
            MultiByteToWideChar(CP_UTF8, 0, &str[0], (int)str.size(), &wstr[0], size);
            return wstr;
        }

        std::string wstring_to_utf8(const std::wstring& wstr) {
            if (wstr.empty()) return std::string();
            int size = WideCharToMultiByte(CP_UTF8, 0, &wstr[0], (int)wstr.size(), NULL, 0, NULL, NULL);
            std::string str(size, 0); 
            WideCharToMultiByte(CP_UTF8, 0, &wstr[0], (int)wstr.size(), &str[0], size, NULL, NULL);
            return str;
        }

        bool IsTimeAllowed(const std::string& timeWindow) {
            if (timeWindow.empty() || timeWindow == "0" || timeWindow == "None" || timeWindow.find('-') == std::string::npos) return false;
            auto parts = RHC::StringUtils::split(timeWindow, '-');
            if (parts.size() != 2) return false;
            int sh, sm, eh, em;
            if (sscanf(parts[0].c_str(), "%d:%d", &sh, &sm) != 2 || sscanf(parts[1].c_str(), "%d:%d", &eh, &em) != 2) return false;
            
            time_t t = time(nullptr); tm* now = localtime(&t);
            int cur = now->tm_hour * 60 + now->tm_min, start = sh * 60 + sm, end = eh * 60 + em;
            return (start <= end) ? (cur >= start && cur <= end) : (cur >= start || cur <= end);
        }

        void InjectEvadeAction() {
            INPUT inputs[4] = {}; 
            inputs[0].type = INPUT_KEYBOARD; inputs[0].ki.wVk = VK_MENU; // ALT
            inputs[1].type = INPUT_KEYBOARD; inputs[1].ki.wVk = VK_LEFT; // LEFT ARROW
            inputs[2].type = INPUT_KEYBOARD; inputs[2].ki.wVk = VK_LEFT; inputs[2].ki.dwFlags = KEYEVENTF_KEYUP;
            inputs[3].type = INPUT_KEYBOARD; inputs[3].ki.wVk = VK_MENU; inputs[3].ki.dwFlags = KEYEVENTF_KEYUP;
            SendInput(4, inputs, sizeof(INPUT));
        }

    }
}
