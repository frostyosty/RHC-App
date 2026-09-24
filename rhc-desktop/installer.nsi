!include "MUI2.nsh"

!define APPNAME "RHC Momentum Shield"
!define APPEXE "rhc_desktop.exe"
!define SVCEXE "rhc_guardian_svc.exe"
!define SVCNAME "RHCGuardian"
!define UNINSTKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}"
; build.sh passes -DVERSION=X.Y.Z from the release tag; local builds get 1.0.0.
!ifndef VERSION
    !define VERSION "1.0.0"
!endif

Name "${APPNAME}"
OutFile "RHC_Installer.exe"
InstallDir "$PROGRAMFILES\RHC Shield"
; On an upgrade, install over the existing copy wherever it lives.
InstallDirRegKey HKLM "${UNINSTKEY}" "InstallLocation"
RequestExecutionLevel admin

VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "${APPNAME}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "FileDescription" "${APPNAME} Installer"
VIAddVersionKey "LegalCopyright" "Momentum"

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

; Stops the guardian service and waits (up to ~15s) until it has really
; stopped, so its exe can be overwritten/deleted. `sc stop` only *requests*
; a stop and returns straight away. If it's still running after the timeout
; the process is killed as a last resort.
!macro StopGuardianAndWait
    nsExec::Exec 'sc.exe stop ${SVCNAME}'
    Pop $0
    StrCpy $1 0
    stop_wait_${__MACRO_UNIQUE_ID}:
        ; Exit code 1060 = service doesn't exist (fresh install): nothing to wait for.
        nsExec::Exec 'sc.exe query ${SVCNAME}'
        Pop $0
        StrCmp $0 "0" 0 stop_done_${__MACRO_UNIQUE_ID}
        nsExec::Exec 'cmd.exe /c sc.exe query ${SVCNAME} | find "STOPPED"'
        Pop $0
        StrCmp $0 "0" stop_done_${__MACRO_UNIQUE_ID}
        IntOp $1 $1 + 1
        IntCmp $1 30 stop_kill_${__MACRO_UNIQUE_ID}
        Sleep 500
        Goto stop_wait_${__MACRO_UNIQUE_ID}
    stop_kill_${__MACRO_UNIQUE_ID}:
        nsExec::Exec 'taskkill /F /IM ${SVCEXE} /T'
        Pop $0
        Sleep 1000
    stop_done_${__MACRO_UNIQUE_ID}:
!macroend

Section "Install"
    ; 1. Kill the app if it's currently running
    ExecWait "taskkill /F /IM ${APPEXE} /T"

    ; 2. Stop any previous copy of the guardian service and wait until it
    ;    has actually exited, so its binary can be overwritten. The service
    ;    registration is kept (not deleted) on upgrade: `sc delete` on a
    ;    service that's still stopping only marks it for deletion, and the
    ;    `sc create` below would then fail with error 1072.
    !insertmacro StopGuardianAndWait

    ; 3. Set output path and copy files
    SetOutPath $INSTDIR
    File "rhc_desktop.exe"
    File "rhc_guardian_svc.exe"
    File "rhc_icon.ico"

    ; 4. Create Shortcuts
    CreateShortcut "$SMPROGRAMS\${APPNAME}.lnk" "$INSTDIR\${APPEXE}" "" "$INSTDIR\rhc_icon.ico"
    CreateShortcut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\${APPEXE}" "" "$INSTDIR\rhc_icon.ico"

    ; 5. Add to Windows Add/Remove Programs
    WriteRegStr HKLM "${UNINSTKEY}" "DisplayName" "${APPNAME}"
    WriteRegStr HKLM "${UNINSTKEY}" "DisplayVersion" "${VERSION}"
    WriteRegStr HKLM "${UNINSTKEY}" "InstallLocation" "$INSTDIR"
    WriteRegStr HKLM "${UNINSTKEY}" "UninstallString" "$\"$INSTDIR\uninstall.exe$\""
    WriteRegStr HKLM "${UNINSTKEY}" "DisplayIcon" "$\"$INSTDIR\rhc_icon.ico$\""
    WriteRegStr HKLM "${UNINSTKEY}" "Publisher" "Momentum"

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
    ;    On an upgrade the service already exists, so reconfigure it instead.
    nsExec::Exec 'sc.exe query ${SVCNAME}'
    Pop $0
    StrCmp $0 "0" 0 svc_create
        ExecWait 'sc.exe config ${SVCNAME} binPath= "$INSTDIR\${SVCEXE}" start= auto obj= LocalSystem DisplayName= "RHC Guardian"'
        Goto svc_configured
    svc_create:
        ExecWait 'sc.exe create ${SVCNAME} binPath= "$INSTDIR\${SVCEXE}" start= auto obj= LocalSystem DisplayName= "RHC Guardian"'
    svc_configured:
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
    ;    workaround. Wait for it to exit so its exe can be deleted.
    !insertmacro StopGuardianAndWait
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
    DeleteRegKey HKLM "${UNINSTKEY}"
SectionEnd
