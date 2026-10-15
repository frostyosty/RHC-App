// RHC Scheduled-Task setup
//
// The GUI's only autostart used to be a single HKCU\...\Run value
// (SafeModeCloak::RegisterStartup). That fires at interactive logon only -
// NOT on wake-from-sleep and NOT on unlock - and, living in HKCU, a user can
// delete it with no admin rights (Task Manager > Startup, msconfig, regedit).
//
// This registers a Scheduled Task instead, with three triggers the Run key
// can't express: At log on, On workstation unlock, and On wake-from-sleep
// (an event trigger on Microsoft-Windows-Power-Troubleshooter/Operational
// event 1, the record Windows writes when it resumes). The task is created
// in the local-machine store by this already-elevated process and runs
// HighestAvailable, so removing or disabling it needs admin - a real step up
// from the Run key.
//
// Deliberately NOT done, on purpose and in the spirit of GuardianService.cpp:
// nothing here resists an admin deleting the task (schtasks /delete, the Task
// Scheduler UI) or uninstalling. The consensual uninstall path calls
// RemoveTask() itself. This is about surviving a reboot/sleep/logoff and a
// casual Startup-tab toggle, not about fighting the machine's owner.
//
// It is re-created (/f) on every launch, so if the task is ever cleared it
// heals the next time the app runs. The service watchdog (GuardianService)
// is the complementary half: it relaunches the GUI the instant it's killed
// mid-session, which triggers alone can't catch.

#include "TaskSchedulerSetup.h"
#include <windows.h>
#include <string>
#include <vector>

namespace {
    const wchar_t* kTaskName = L"RHCGuardianAgent";

    // Run a command with no visible console window and wait for it to finish.
    void RunHidden(std::wstring cmdline) {
        STARTUPINFOW si = {}; si.cb = sizeof(si);
        si.dwFlags = STARTF_USESHOWWINDOW; si.wShowWindow = SW_HIDE;
        PROCESS_INFORMATION pi = {};
        std::vector<wchar_t> buf(cmdline.begin(), cmdline.end()); buf.push_back(0);
        if (CreateProcessW(NULL, buf.data(), NULL, NULL, FALSE,
                           CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
            WaitForSingleObject(pi.hProcess, 15000);
            CloseHandle(pi.hProcess); CloseHandle(pi.hThread);
        }
    }

    std::wstring CurrentUserId() {
        wchar_t user[256] = {}; DWORD un = 256;
        wchar_t dom[256]  = {}; DWORD dn = 256;
        GetEnvironmentVariableW(L"USERNAME", user, un);
        GetEnvironmentVariableW(L"USERDOMAIN", dom, dn);
        if (dom[0]) return std::wstring(dom) + L"\\" + user;
        return std::wstring(user);
    }

    std::wstring ExePath() {
        wchar_t path[MAX_PATH] = {};
        GetModuleFileNameW(NULL, path, MAX_PATH);
        return std::wstring(path);
    }

    // Write a UTF-16LE file with BOM - schtasks /xml expects Unicode.
    bool WriteUtf16(const std::wstring& path, const std::wstring& text) {
        HANDLE h = CreateFileW(path.c_str(), GENERIC_WRITE, 0, NULL,
                               CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
        if (h == INVALID_HANDLE_VALUE) return false;
        DWORD w = 0;
        const unsigned char bom[2] = { 0xFF, 0xFE };
        WriteFile(h, bom, 2, &w, NULL);
        WriteFile(h, text.data(), (DWORD)(text.size() * sizeof(wchar_t)), &w, NULL);
        CloseHandle(h);
        return true;
    }

    std::wstring BuildTaskXml(const std::wstring& userId, const std::wstring& exe) {
        // Power-Troubleshooter event 1 = "The system has resumed from sleep".
        // The subscription is XML-inside-XML, so its angle brackets are escaped.
        std::wstring xml =
            L"<?xml version=\"1.0\" encoding=\"UTF-16\"?>\r\n"
            L"<Task version=\"1.3\" xmlns=\"http://schemas.microsoft.com/windows/2004/02/mit/task\">\r\n"
            L"  <RegistrationInfo>\r\n"
            L"    <Description>Keeps RHC Momentum Shield running at logon, unlock and wake-from-sleep.</Description>\r\n"
            L"  </RegistrationInfo>\r\n"
            L"  <Triggers>\r\n"
            L"    <LogonTrigger><Enabled>true</Enabled><UserId>" + userId + L"</UserId></LogonTrigger>\r\n"
            L"    <SessionStateChangeTrigger><Enabled>true</Enabled><StateChange>SessionUnlock</StateChange><UserId>" + userId + L"</UserId></SessionStateChangeTrigger>\r\n"
            L"    <EventTrigger><Enabled>true</Enabled>"
            L"<Subscription>&lt;QueryList&gt;&lt;Query Id=\"0\" Path=\"Microsoft-Windows-Power-Troubleshooter/Operational\"&gt;&lt;Select Path=\"Microsoft-Windows-Power-Troubleshooter/Operational\"&gt;*[System[(EventID=1)]]&lt;/Select&gt;&lt;/Query&gt;&lt;/QueryList&gt;</Subscription>"
            L"</EventTrigger>\r\n"
            L"  </Triggers>\r\n"
            L"  <Principals>\r\n"
            L"    <Principal id=\"Author\">\r\n"
            L"      <UserId>" + userId + L"</UserId>\r\n"
            L"      <LogonType>InteractiveToken</LogonType>\r\n"
            L"      <RunLevel>HighestAvailable</RunLevel>\r\n"
            L"    </Principal>\r\n"
            L"  </Principals>\r\n"
            L"  <Settings>\r\n"
            L"    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>\r\n"
            L"    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>\r\n"
            L"    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>\r\n"
            L"    <AllowHardTerminate>true</AllowHardTerminate>\r\n"
            L"    <StartWhenAvailable>true</StartWhenAvailable>\r\n"
            L"    <RunOnlyIfNetworkAvailable>false</RunOnlyIfNetworkAvailable>\r\n"
            L"    <IdleSettings><StopOnIdleEnd>false</StopOnIdleEnd><RestartOnIdle>false</RestartOnIdle></IdleSettings>\r\n"
            L"    <AllowStartOnDemand>true</AllowStartOnDemand>\r\n"
            L"    <Enabled>true</Enabled>\r\n"
            L"    <Hidden>false</Hidden>\r\n"
            L"    <RunOnlyIfIdle>false</RunOnlyIfIdle>\r\n"
            L"    <WakeToRun>false</WakeToRun>\r\n"
            L"    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>\r\n"
            L"    <Priority>7</Priority>\r\n"
            L"    <RestartOnFailure><Interval>PT1M</Interval><Count>3</Count></RestartOnFailure>\r\n"
            L"  </Settings>\r\n"
            L"  <Actions Context=\"Author\">\r\n"
            L"    <Exec><Command>" + exe + L"</Command></Exec>\r\n"
            L"  </Actions>\r\n"
            L"</Task>\r\n";
        return xml;
    }
}

namespace RHC {
    namespace TaskScheduler {
        void EnsureTask() {
            std::wstring exe = ExePath();
            // Mirror RegisterStartup's guard: only wire up the real installed
            // exe, never a Downloads/temp copy that happens to run.
            if (exe.find(L"rhc_desktop.exe") == std::wstring::npos) return;

            std::wstring xml = BuildTaskXml(CurrentUserId(), exe);

            // Stage the XML next to the exe (install dir), import, then remove.
            size_t slash = exe.find_last_of(L"\\/");
            std::wstring dir = (slash != std::wstring::npos) ? exe.substr(0, slash) : L".";
            std::wstring xmlPath = dir + L"\\rhc_agent_task.xml";
            if (!WriteUtf16(xmlPath, xml)) return;

            RunHidden(L"schtasks /create /tn \"" + std::wstring(kTaskName) +
                      L"\" /xml \"" + xmlPath + L"\" /f");
            DeleteFileW(xmlPath.c_str());
        }

        void RemoveTask() {
            RunHidden(L"schtasks /delete /tn \"" + std::wstring(kTaskName) + L"\" /f");
        }
    }
}
