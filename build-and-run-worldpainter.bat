@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%"

if not exist "%PROJECT_DIR%pom.xml" (
    if exist "%PROJECT_DIR%WorldPainter\pom.xml" (
        set "PROJECT_DIR=%PROJECT_DIR%WorldPainter\"
    ) else (
        echo [ERROR] Could not find WorldPainter pom.xml.
        echo         Expected either:
        echo         - %PROJECT_DIR%pom.xml
        echo         - %PROJECT_DIR%WorldPainter\pom.xml
        pause
        exit /b 1
    )
)

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java was not found on PATH.
    echo         Install JDK 17 and make sure "java" is available.
    pause
    exit /b 1
)

rem Prefer a project-local Maven wrapper if present
set "MVN_CMD="
if exist "%PROJECT_DIR%mvnw.cmd" (
    set "MVN_CMD=%PROJECT_DIR%mvnw.cmd"
    goto :mvn_found
)
if exist "%PROJECT_DIR%WorldPainter\mvnw.cmd" (
    set "MVN_CMD=%PROJECT_DIR%WorldPainter\mvnw.cmd"
    goto :mvn_found
)

rem Check if mvn is on PATH
where mvn >nul 2>&1
if not errorlevel 1 (
    set "MVN_CMD=mvn"
    goto :mvn_found
)

rem Check MAVEN_HOME
if defined MAVEN_HOME (
    if exist "%MAVEN_HOME%\bin\mvn.cmd" (
        set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
        goto :mvn_found
    )
)

rem Check M2_HOME
if defined M2_HOME (
    if exist "%M2_HOME%\bin\mvn.cmd" (
        set "MVN_CMD=%M2_HOME%\bin\mvn.cmd"
        goto :mvn_found
    )
)

rem Attempt to locate apache-maven referenced inside PATH (handles Git Bash /c/... style paths)
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "$env:PATH -split ';' | Where-Object {$_ -match 'apache-maven'} | Select-Object -First 1"`) do set "MAVEN_CAND=%%A"
if defined MAVEN_CAND (
    rem Convert unix-style /c/... paths to Windows (C:\...) if necessary
    set "MAVEN_WIN=!MAVEN_CAND!"
    if "!MAVEN_WIN:~0,1!"=="/" (
        set "drive=!MAVEN_WIN:~1,1!"
        set "rest=!MAVEN_WIN:~3!"
        set "MAVEN_WIN=!drive!:\!rest!"
        set "MAVEN_WIN=!MAVEN_WIN:/=\!"
    )
    if exist "!MAVEN_WIN!\mvn.cmd" (
        set "MVN_CMD=!MAVEN_WIN!\mvn.cmd"
        goto :mvn_found
    )
    if exist "!MAVEN_CAND!\mvn.cmd" (
        set "MVN_CMD=!MAVEN_CAND!\mvn.cmd"
        goto :mvn_found
    )
)

rem Search Program Files and Program Files (x86)
for %%D in ("%ProgramFiles%","%ProgramFiles(x86)%") do (
    if exist "%%~D" (
        for /f "delims=" %%F in ('where /R "%%~D" mvn.cmd 2^>nul') do (
            set "MVN_CMD=%%F"
            goto :mvn_found
        )
    )
)

rem No Maven found; print a clear error and exit
echo [ERROR] Maven was not found on PATH, MAVEN_HOME or in common locations.
echo         If you set PATH in Git Bash (e.g. export PATH="/c/tmp/apache-maven-3.9.5/bin:$PATH"^), run this script from that shell or set MAVEN_HOME.
echo         Alternatively you can place a project-local "mvnw.cmd" next to the project's pom.xml.
pause
exit /b 1

:mvn_found
rem If we reached here, %MVN_CMD% is set to either "mvnw.cmd", "mvn" or the full path to mvn.cmd

rem Sanity check: ensure MVN_CMD is not empty before invoking it
if "!MVN_CMD!"=="" (
    echo [ERROR] MVN_CMD is empty; cannot continue.
    pause
    exit /b 1
)

echo [INFO] Using Maven command: !MVN_CMD!


echo.
echo [INFO] Project directory: %PROJECT_DIR%
echo [INFO] Building WPGUI and required modules...
pushd "%PROJECT_DIR%"
call "!MVN_CMD!" -DskipTests=true -pl WPGUI -am install
set BUILD_EXIT=%ERRORLEVEL%
if %BUILD_EXIT% NEQ 0 (
    echo.
    echo [ERROR] Build failed.
    echo         Check output above for missing dependencies (for example JIDE jars/toolchains^).
    popd
    pause
    exit /b 1
)

echo.
echo [INFO] Build succeeded. Launching WorldPainter...
call "!MVN_CMD!" -pl WPGUI exec:exec
set RUN_EXIT=%ERRORLEVEL%
if %RUN_EXIT% NEQ 0 (
    echo.
    echo [ERROR] WorldPainter failed to start.
    popd
    pause
    exit /b 1
)

popd
echo.
echo [INFO] WorldPainter exited normally.
endlocal
exit /b 0
