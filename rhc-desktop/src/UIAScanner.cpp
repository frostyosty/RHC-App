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

            // One cross-process request that brings back every element's name, instead of a call into the
            // app per element. Mode None: just the cached names, not live elements
            static IUIAutomationCacheRequest* pNameCache = NULL;
            if (pAutomation != NULL && pNameCache == NULL && SUCCEEDED(pAutomation->CreateCacheRequest(&pNameCache))) {
                pNameCache->AddProperty(UIA_NamePropertyId);
                pNameCache->put_AutomationElementMode(AutomationElementMode_None);
            }

            if (pAutomation != NULL) {
                IUIAutomationElement* pWindow = NULL;
                HRESULT hr = pAutomation->ElementFromHandle(hwnd, &pWindow);
                if (SUCCEEDED(hr) && pWindow != NULL) {
                    IUIAutomationCondition* pCondition = NULL; 
                    pAutomation->CreateTrueCondition(&pCondition);
                    
                    IUIAutomationElementArray* pArray = NULL; 
                    if (pNameCache) pWindow->FindAllBuildCache(TreeScope_Descendants, pCondition, pNameCache, &pArray);
                    else pWindow->FindAll(TreeScope_Descendants, pCondition, &pArray);
                    
                    if (pArray != NULL) {
                        int count = 0; pArray->get_Length(&count);
                        for (int i = 0; i < count; i++) {
                            IUIAutomationElement* pChild = NULL;
                            if (SUCCEEDED(pArray->GetElement(i, &pChild))) {
                                BSTR name = NULL;
                                HRESULT hrName = pNameCache ? pChild->get_CachedName(&name) : pChild->get_CurrentName(&name);
                                if (SUCCEEDED(hrName) && name != NULL) {
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
