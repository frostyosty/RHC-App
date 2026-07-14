#pragma once
#include <string>
#include <windows.h>

namespace RHC {
    namespace Utils {
        std::wstring utf8_to_wstring(const std::string& str);
        std::string wstring_to_utf8(const std::wstring& wstr);
        bool IsTimeAllowed(const std::string& timeWindow);
        void InjectEvadeAction();
        
        // Modal & Validation Subsystem
        bool ValidateTimeFormat(const std::wstring& timeStr, std::wstring& outErrorMsg);
        void ShowErrorModal(HWND hwndParent, const std::wstring& title, const std::wstring& message);
        
        // Lock-in and AM/PM Time Formatting Helpers
        int GetAllowedWindowDuration(const std::string& timeWindow);
        std::string FormatTimeToAmPm(const std::string& timeWindow);
        bool IsStrictSubset(const std::string& oldTime, const std::string& newTime);
        void ApplyDarkTitleBar(HWND hwnd);
    }
}
