#pragma once
#include <windows.h>
#include <string>

namespace RHC {
    namespace DashboardUI {
        void SyncHostsFileFromDB();
        void RefreshLogsUI();
        void RefreshBlockListUI();
        void PopulateInstalledAppsCombo(HWND hCombo);
        void AddBlockItem(const std::wstring& domain, const std::wstring& timeRestr);
        void UpdateDashboardText();
        void TrackOvercome(const std::string& target, const std::string& listKey, const std::string& firstTimeKey);
        void SpendTime(int taskIndex);

        LRESULT CALLBACK DashboardProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam);
    }
}
