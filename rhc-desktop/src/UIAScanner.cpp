#include "UIAScanner.h"
#include <windows.h>
#include <uiautomation.h>

namespace RHC {
    namespace UIAScanner {
        std::string ScanForeground() {
            HWND hwnd = GetForegroundWindow();
            if (!hwnd) return "";

            char title[1024]; GetWindowTextA(hwnd, title, sizeof(title));
            std::string result = std::string(title) + " ";

            // FIX: Instantiate COM Object ONCE and keep it alive in memory.
            // Drops CPU usage significantly and stops COM memory leak.
            static IUIAutomation* pAutomation = NULL;
            if (pAutomation == NULL) {
                HRESULT hr = CoCreateInstance(__uuidof(CUIAutomation), NULL, CLSCTX_INPROC_SERVER, __uuidof(IUIAutomation), (void**)&pAutomation);
                if (FAILED(hr)) return result; // Fallback to just scanning window title
            }

            if (pAutomation != NULL) {
                IUIAutomationElement* pWindow = NULL;
                HRESULT hr = pAutomation->ElementFromHandle(hwnd, &pWindow);
                if (SUCCEEDED(hr) && pWindow != NULL) {
                    IUIAutomationCondition* pCondition = NULL; 
                    pAutomation->CreateTrueCondition(&pCondition);
                    
                    IUIAutomationElementArray* pArray = NULL; 
                    pWindow->FindAll(TreeScope_Children, pCondition, &pArray); 
                    
                    if (pArray != NULL) {
                        int count = 0; pArray->get_Length(&count);
                        for (int i = 0; i < count; i++) {
                            IUIAutomationElement* pChild = NULL;
                            if (SUCCEEDED(pArray->GetElement(i, &pChild))) {
                                BSTR name;
                                if (SUCCEEDED(pChild->get_CurrentName(&name)) && name != NULL) {
                                    int len = SysStringLen(name); 
                                    int size_needed = WideCharToMultiByte(CP_UTF8, 0, name, len, NULL, 0, NULL, NULL);
                                    std::string strTo(size_needed, 0); 
                                    WideCharToMultiByte(CP_UTF8, 0, name, len, &strTo[0], size_needed, NULL, NULL);
                                    result += strTo + " "; 
                                    SysFreeString(name);
                                }
                                pChild->Release();
                            }
                        }
                        pArray->Release();
                    }
                    if (pCondition) pCondition->Release(); 
                    pWindow->Release();
                }
            }
            return result;
        }
    }
}
