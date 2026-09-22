// RHC Guardian Service
//
// Runs as a Windows service (LocalSystem, auto-start) so the core blocklist
// enforcement (hosts-file domain blocking + blocked-exe termination) keeps
// running independent of the desktop GUI process. A standard, non-admin
// user's Task Manager cannot stop a LocalSystem service - only an elevated
// admin can, via `sc stop`/Services.msc or the real NSIS uninstaller. This
// is the legitimate, Microsoft-sanctioned equivalent of what a hand-rolled
// "resist getting killed" watchdog would otherwise try to fake, and it's
// exactly the roadmap item SafeModeCloak.cpp used to point at before that
// self-hiding code was removed for violating Defender's behavioral rules.
//
// Deliberately NOT done here, on purpose: nothing in this file resists an
// elevated admin action (sc stop/delete, Services.msc, the uninstaller).
// Fighting an admin's own deliberate action is what turns "hard to
// casually disable" into "malware-grade tamper resistance", and is exactly
// the kind of behavior that gets flagged.
//
// Deliberately NOT moved here: foreground-window scanning, UI Automation
// text scanning, and the red-wall/nightfall overlays. Windows services run
// in Session 0 with no access to the interactive user's desktop, so none
// of that can run from a service at all - it stays in the GUI process
// (Guardian.cpp) and remains something a user can pause simply by closing
// the GUI. Only the hosts-file block and the exe blocklist survive that.

#include <windows.h>
#include <tlhelp32.h>
#include <ctime>
#include <string>
#include <vector>
#include "include/DatabaseManager.h"
#include "include/StringUtils.h"
#include "../src/HostsBlocker.h"

namespace {
    const wchar_t* kServiceName = L"RHCGuardian";
    SERVICE_STATUS g_Status = {};
    SERVICE_STATUS_HANDLE g_StatusHandle = nullptr;
    HANDLE g_StopEvent = nullptr;

    std::wstring GetInstallDir() {
        wchar_t path[MAX_PATH];
        DWORD len = GetModuleFileNameW(NULL, path, MAX_PATH);
        std::wstring p(path, len);
        size_t slash = p.find_last_of(L"\\/");
        return (slash != std::wstring::npos) ? p.substr(0, slash) : L".";
    }

    std::string WideToUtf8(const std::wstring& wstr) {
        if (wstr.empty()) return std::string();
        int size = WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), (int)wstr.size(), NULL, 0, NULL, NULL);
        std::string str(size, 0);
        WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), (int)wstr.size(), &str[0], size, NULL, NULL);
        return str;
    }

    // Local copy of DesktopUtils::IsTimeAllowed's logic - kept standalone so
    // this service doesn't need to link any of the GUI/rendering code.
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

    void SyncHostsFromDb(RHC::DatabaseManager& db) {
        std::vector<std::string> domains;
        for (auto& entry : RHC::StringUtils::split(db.getString("BLOCKLIST_WEB", ""), ',')) {
            auto parts = RHC::StringUtils::split(entry, '|');
            if (parts.empty() || parts[0].empty()) continue;
            if (parts.size() >= 4 && parts[3] != "None" && IsTimeAllowed(parts[3])) continue;
            domains.push_back(parts[0]);
        }
        RHC::HostsBlocker::SyncHostsFile(domains);
    }

    void EnforceExeBlocklist(RHC::DatabaseManager& db) {
        auto blocked = RHC::StringUtils::split(db.getString("BLOCKLIST_EXE", ""), ',');
        if (blocked.empty()) return;

        HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        if (snap == INVALID_HANDLE_VALUE) return;

        PROCESSENTRY32W pe = {}; pe.dwSize = sizeof(pe);
        if (Process32FirstW(snap, &pe)) {
            do {
                std::string exeName = RHC::StringUtils::toLower(WideToUtf8(pe.szExeFile));
                for (auto& entry : blocked) {
                    auto parts = RHC::StringUtils::split(entry, '|');
                    if (parts.empty()) continue;
                    if (exeName != RHC::StringUtils::toLower(parts[0])) continue;
                    if (parts.size() >= 4 && parts[3] != "None" && IsTimeAllowed(parts[3])) continue;

                    HANDLE hProc = OpenProcess(PROCESS_TERMINATE, FALSE, pe.th32ProcessID);
                    if (hProc) { TerminateProcess(hProc, 0); CloseHandle(hProc); }
                    break;
                }
            } while (Process32NextW(snap, &pe));
        }
        CloseHandle(snap);
    }

    void ReportStatus(DWORD state, DWORD exitCode = NO_ERROR, DWORD waitHint = 0) {
        g_Status.dwCurrentState = state;
        g_Status.dwWin32ExitCode = exitCode;
        g_Status.dwWaitHint = waitHint;
        g_Status.dwControlsAccepted = (state == SERVICE_START_PENDING) ? 0 : SERVICE_ACCEPT_STOP | SERVICE_ACCEPT_SHUTDOWN;
        SetServiceStatus(g_StatusHandle, &g_Status);
    }

    DWORD WINAPI ServiceCtrlHandlerEx(DWORD ctrl, DWORD, LPVOID, LPVOID) {
        switch (ctrl) {
            case SERVICE_CONTROL_STOP:
            case SERVICE_CONTROL_SHUTDOWN:
                ReportStatus(SERVICE_STOP_PENDING);
                SetEvent(g_StopEvent);
                return NO_ERROR;
        }
        return NO_ERROR;
    }

    void WINAPI ServiceMain(DWORD, LPWSTR*) {
        g_Status.dwServiceType = SERVICE_WIN32_OWN_PROCESS;
        g_StatusHandle = RegisterServiceCtrlHandlerExW(kServiceName, ServiceCtrlHandlerEx, nullptr);
        if (!g_StatusHandle) return;

        ReportStatus(SERVICE_START_PENDING, NO_ERROR, 3000);
        g_StopEvent = CreateEventW(NULL, TRUE, FALSE, NULL);
        std::wstring dbPath = GetInstallDir() + L"\\rhc_state.db";
        std::string dbPathUtf8 = WideToUtf8(dbPath);
        ReportStatus(SERVICE_RUNNING);

        int syncCounter = 0;
        while (WaitForSingleObject(g_StopEvent, 2000) == WAIT_TIMEOUT) {
            RHC::DatabaseManager db(dbPathUtf8);
            EnforceExeBlocklist(db);
            if (syncCounter++ >= 30) { SyncHostsFromDb(db); syncCounter = 0; }
        }

        ReportStatus(SERVICE_STOPPED);
        CloseHandle(g_StopEvent);
    }
}

int main() {
    SERVICE_TABLE_ENTRYW table[] = {
        { const_cast<LPWSTR>(kServiceName), (LPSERVICE_MAIN_FUNCTIONW)ServiceMain },
        { NULL, NULL }
    };
    StartServiceCtrlDispatcherW(table);
    return 0;
}
