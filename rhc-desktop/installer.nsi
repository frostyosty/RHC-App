!include "MUI2.nsh"

!define APPNAME "RHC Momentum Shield"
!define APPEXE "rhc_desktop.exe"
!define VERSION "1.0.0.0"

Name "${APPNAME}"
OutFile "RHC_Installer.exe"
InstallDir "$PROGRAMFILES\RHC Shield"
RequestExecutionLevel admin

; ==========================================
; VISUAL THEME (Dark Mode)
; ==========================================
!define MUI_ICON "rhc_icon.ico"
!define MUI_BGCOLOR "121212"                  ; Dark background
!define MUI_TEXTCOLOR "FFFFFF"                ; White text
!define MUI_INSTFILESPAGE_COLORS "FFFFFF 121212" ; Dark console with white text
!define MUI_PROGRESSBAR "smooth"

; ==========================================
; INSTALLER PAGES (One-Click Setup)
; ==========================================
; By ONLY including the INSTFILES page, we skip the Welcome/Directory screens.
; The installer will just open, show the progress bar, and finish.
!insertmacro MUI_PAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

; ==========================================
; BEHAVIOR
; ==========================================
AutoCloseWindow true        ; Close automatically when finished
ShowInstDetails nevershow   ; Hide the technical log by default

Section "Install"
    ; 1. Kill the app if it's currently running
    ExecWait "taskkill /F /IM ${APPEXE} /T"
    
    ; 2. Set output path and copy files
    SetOutPath $INSTDIR
    File "rhc_desktop.exe"
    File "rhc_icon.ico"
    
    ; 3. Create Shortcuts
    CreateShortcut "$SMPROGRAMS\${APPNAME}.lnk" "$INSTDIR\${APPEXE}" "" "$INSTDIR\rhc_icon.ico"
    CreateShortcut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\${APPEXE}" "" "$INSTDIR\rhc_icon.ico"
    
    ; 4. Add to Windows Add/Remove Programs
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "DisplayName" "${APPNAME}"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "UninstallString" "$\"$INSTDIR\uninstall.exe$\""
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "DisplayIcon" "$\"$INSTDIR\rhc_icon.ico$\""
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "Publisher" "Rock Hard Blocker"
    
    ; 5. Generate Uninstaller
    WriteUninstaller "$INSTDIR\uninstall.exe"
    
    ; 6. Auto-start the new version!
    Exec '"$INSTDIR\${APPEXE}"'
SectionEnd

Section "Uninstall"
    ; 1. Kill the app before uninstalling
    ExecWait "taskkill /F /IM ${APPEXE} /T"
    
    ; 2. Delete files
    Delete "$INSTDIR\${APPEXE}"
    Delete "$INSTDIR\rhc_icon.ico"
    Delete "$INSTDIR\uninstall.exe"
    
    ; 3. Remove shortcuts
    Delete "$SMPROGRAMS\${APPNAME}.lnk"
    Delete "$DESKTOP\${APPNAME}.lnk"
    
    ; 4. Remove directory and registry keys
    RMDir "$INSTDIR"
    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}"
SectionEnd
