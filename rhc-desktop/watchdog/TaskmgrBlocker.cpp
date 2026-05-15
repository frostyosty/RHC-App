#include <windows.h>

// This module is isolated. If it triggers AV, we can simply drop it from the build script.
void PreventTaskManager() {
    // TODO: Loop to detect and close Taskmgr.exe
}
