!include "MUI2.nsh"

!define APPNAME "RHC Momentum Shield"
!define APPEXE "rhc_desktop.exe"
!define SVCEXE "rhc_guardian_svc.exe"
!define SVCNAME "RHCGuardian"
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

    ; 2. Stop and remove any previous copy of the guardian service so an
    ;    upgrade replaces its binary cleanly instead of failing to overwrite
    ;    a running exe.
    ExecWait 'sc.exe stop ${SVCNAME}'
    ExecWait 'sc.exe delete ${SVCNAME}'

    ; 3. Set output path and copy files
    SetOutPath $INSTDIR
    File "rhc_desktop.exe"
    File "rhc_guardian_svc.exe"
    File "rhc_icon.ico"

    ; 4. Create Shortcuts
    CreateShortcut "$SMPROGRAMS\${APPNAME}.lnk" "$INSTDIR\${APPEXE}" "" "$INSTDIR\rhc_icon.ico"
    CreateShortcut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\${APPEXE}" "" "$INSTDIR\rhc_icon.ico"

    ; 5. Add to Windows Add/Remove Programs
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "DisplayName" "${APPNAME}"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "UninstallString" "$\"$INSTDIR\uninstall.exe$\""
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "DisplayIcon" "$\"$INSTDIR\rhc_icon.ico$\""
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "Publisher" "Momentum"

    ; 6. Generate Uninstaller
    WriteUninstaller "$INSTDIR\uninstall.exe"

    ; 7. Install the guardian service (LocalSystem, auto-start) and give it
    ;    a recovery policy so it restarts itself if killed/crashed. Stopping
    ;    or deleting a LocalSystem service requires admin rights, so this -
    ;    unlike the app itself - can't be casually ended from a standard
    ;    user's Task Manager. It CAN always be stopped by an admin via
    ;    `sc stop`/Services.msc or this installer's own uninstaller: this is
    ;    meant to resist a casual End Task, not an admin's deliberate
    ;    decision to remove it.
    ExecWait 'sc.exe create ${SVCNAME} binPath= "$INSTDIR\${SVCEXE}" start= auto obj= LocalSystem DisplayName= "RHC Guardian"'
    ExecWait 'sc.exe description ${SVCNAME} "Enforces RHC Momentum Shield blocklists. Stopping this requires administrator rights."'
    ExecWait 'sc.exe failure ${SVCNAME} reset= 86400 actions= restart/5000/restart/5000/restart/60000'
    ExecWait 'sc.exe start ${SVCNAME}'

    ; 8. Auto-start the new version!
    Exec '"$INSTDIR\${APPEXE}"'
SectionEnd

Section "Uninstall"
    ; 1. Kill the app before uninstalling
    ExecWait "taskkill /F /IM ${APPEXE} /T"

    ; 2. Stop and remove the guardian service. This runs from the generated
    ;    uninstall.exe, which inherits RequestExecutionLevel admin above, so
    ;    it's already elevated - a real admin-initiated removal, not a
    ;    workaround.
    ExecWait 'sc.exe stop ${SVCNAME}'
    ExecWait 'sc.exe delete ${SVCNAME}'

    ; 3. Delete files
    Delete "$INSTDIR\${APPEXE}"
    Delete "$INSTDIR\${SVCEXE}"
    Delete "$INSTDIR\rhc_icon.ico"
    Delete "$INSTDIR\uninstall.exe"

    ; 4. Remove shortcuts
    Delete "$SMPROGRAMS\${APPNAME}.lnk"
    Delete "$DESKTOP\${APPNAME}.lnk"

    ; 5. Remove directory and registry keys
    RMDir "$INSTDIR"
    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}"
SectionEnd
