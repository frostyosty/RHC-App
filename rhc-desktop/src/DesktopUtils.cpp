#include "DesktopUtils.h"
#include "Globals.h"
#include <windows.h>
#include <ctime>
#include <iostream>
#include "../../rhc-common/include/StringUtils.h"

bool g_DebugMode = false;
bool g_SystemIsSleeping = false;

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
            if (sscanf_s(parts[0].c_str(), "%d:%d", &sh, &sm) != 2 || sscanf_s(parts[1].c_str(), "%d:%d", &eh, &em) != 2) return false;
            
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

        bool ValidateTimeFormat(const std::wstring& timeStr, std::wstring& outErrorMsg) {
            std::wstring trimmed = timeStr;
            trimmed.erase(0, trimmed.find_first_not_of(L" \t\r\n"));
            trimmed.erase(trimmed.find_last_not_of(L" \t\r\n") + 1);

            if (trimmed.empty() || trimmed == L"None" || trimmed == L"none") {
                return true;
            }

            size_t dash = trimmed.find(L'-');
            if (dash == std::wstring::npos) {
                outErrorMsg = L"Format must be 'HH:MM-HH:MM' (e.g., 09:00-17:00).\nMissing '-' range separator.";
                return false;
            }

            std::wstring start = trimmed.substr(0, dash);
            std::wstring end = trimmed.substr(dash + 1);

            int sh = -1, sm = -1, eh = -1, em = -1;
            wchar_t extraStart = L'\0', extraEnd = L'\0';

            if (swscanf_s(start.c_str(), L"%d:%d%c", &sh, &sm, &extraStart, 1) != 2 || sh < 0 || sh > 23 || sm < 0 || sm > 59) {
                outErrorMsg = L"Invalid start time. Format must be 'HH:MM-HH:MM'.\nStart hour must be between 0-23 and minutes 0-59.";
                return false;
            }

            if (swscanf_s(end.c_str(), L"%d:%d%c", &eh, &em, &extraEnd, 1) != 2 || eh < 0 || eh > 23 || em < 0 || em > 59) {
                outErrorMsg = L"Invalid end time. Format must be 'HH:MM-HH:MM'.\nEnd hour must be between 0-23 and minutes 0-59.";
                return false;
            }

            return true;
        }

        void ShowErrorModal(HWND hwndParent, const std::wstring& title, const std::wstring& message) {
            MessageBoxW(hwndParent, message.c_str(), title.c_str(), MB_OK | MB_ICONERROR | MB_APPLMODAL | MB_SETFOREGROUND);
        }

        int GetAllowedWindowDuration(const std::string& timeWindow) {
            if (timeWindow.empty() || timeWindow == "0" || timeWindow == "None" || timeWindow.find('-') == std::string::npos) {
                return 0;
            }
            auto parts = RHC::StringUtils::split(timeWindow, '-');
            if (parts.size() != 2) return 0;
            int sh, sm, eh, em;
            if (sscanf_s(parts[0].c_str(), "%d:%d", &sh, &sm) != 2 || sscanf_s(parts[1].c_str(), "%d:%d", &eh, &em) != 2) return 0;
            
            int start = sh * 60 + sm;
            int end = eh * 60 + em;
            if (start <= end) {
                return end - start;
            } else { 
                return (1440 - start) + end;
            }
        }

        std::string FormatTimeToAmPm(const std::string& timeWindow) {
            if (timeWindow.empty() || timeWindow == "0" || timeWindow == "None" || timeWindow.find('-') == std::string::npos) {
                return "Always Blocked";
            }
            auto parts = RHC::StringUtils::split(timeWindow, '-');
            if (parts.size() != 2) return "Always Blocked";
            int sh, sm, eh, em;
            if (sscanf_s(parts[0].c_str(), "%d:%d", &sh, &sm) != 2 || sscanf_s(parts[1].c_str(), "%d:%d", &eh, &em) != 2) return "Always Blocked";
            
            auto formatHalf = [](int h, int m) -> std::string {
                std::string ampm = (h >= 12) ? "PM" : "AM";
                int displayH = h % 12;
                if (displayH == 0) displayH = 12;
                char buf[32];
                sprintf_s(buf, "%d:%02d %s", displayH, m, ampm.c_str());
                return std::string(buf);
            };
            
            return formatHalf(sh, sm) + " to " + formatHalf(eh, em);
        }

    }
}
