#pragma once

namespace RHC {
    // Registers (and heals) a Windows Scheduled Task that relaunches the GUI
    // at logon, on workstation unlock, and on wake-from-sleep - the cases a
    // plain HKCU\...\Run value can't cover. The task lives in the local-machine
    // store and runs HighestAvailable, so (unlike the Run key) a standard user
    // can't disable it without admin. See TaskSchedulerSetup.cpp for the full
    // rationale and the deliberate limits (nothing here fights an admin).
    namespace TaskScheduler {
        // Create-or-overwrite the task, pointing at the currently running exe.
        // Safe to call every launch; /f makes it idempotent and self-healing.
        void EnsureTask();
        // Remove the task. Called from the consensual uninstall path.
        void RemoveTask();
    }
}
