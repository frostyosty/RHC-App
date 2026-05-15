#include "CrashReporter.h"
#include <windows.h>
#include <fstream>

namespace RHC {
    namespace CrashReporter {
        LONG WINAPI ExceptionFilter(EXCEPTION_POINTERS* ep) {
            std::ofstream out("rhc_crash_log.txt", std::ios::app);
            out << "--- RHC FATAL CRASH ---\n";
            out << "Exception Code: 0x" << std::hex << ep->ExceptionRecord->ExceptionCode << "\n";
            out << "Address: 0x" << ep->ExceptionRecord->ExceptionAddress << "\n\n";
            out.close();

            MessageBoxW(NULL, L"The Momentum Core encountered a critical instability.\nA detailed crash report has been saved to rhc_crash_log.txt.\nPlease forward this to the developer.", L"⚠️ SYSTEM FAILURE ⚠️", MB_ICONERROR | MB_OK);
            return EXCEPTION_EXECUTE_HANDLER;
        }
        void Initialize() { SetUnhandledExceptionFilter(ExceptionFilter); }
    }
}
