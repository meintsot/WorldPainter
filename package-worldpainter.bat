@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%"

if not exist "%PROJECT_DIR%pom.xml" (
    if exist "%PROJECT_DIR%WorldPainter\pom.xml" (
        set "PROJECT_DIR=%PROJECT_DIR%WorldPainter\"
    ) else (
        echo [ERROR] Could not find WorldPainter pom.xml.
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

rem === Maven detection (same logic as build-and-run-worldpainter.bat) ===
set "MVN_CMD="
if exist "%PROJECT_DIR%mvnw.cmd" (
    set "MVN_CMD=%PROJECT_DIR%mvnw.cmd"
    goto :mvn_found
)
if exist "%PROJECT_DIR%WorldPainter\mvnw.cmd" (
    set "MVN_CMD=%PROJECT_DIR%WorldPainter\mvnw.cmd"
    goto :mvn_found
)
where mvn >nul 2>&1
if not errorlevel 1 (
    set "MVN_CMD=mvn"
    goto :mvn_found
)
if defined MAVEN_HOME (
    if exist "%MAVEN_HOME%\bin\mvn.cmd" (
        set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
        goto :mvn_found
    )
)
if defined M2_HOME (
    if exist "%M2_HOME%\bin\mvn.cmd" (
        set "MVN_CMD=%M2_HOME%\bin\mvn.cmd"
        goto :mvn_found
    )
)
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "$env:PATH -split ';' | Where-Object {$_ -match 'apache-maven'} | Select-Object -First 1"`) do set "MAVEN_CAND=%%A"
if defined MAVEN_CAND (
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
for %%D in ("%ProgramFiles%","%ProgramFiles(x86)%") do (
    if exist "%%~D" (
        for /f "delims=" %%F in ('where /R "%%~D" mvn.cmd 2^>nul') do (
            set "MVN_CMD=%%F"
            goto :mvn_found
        )
    )
)
echo [ERROR] Maven was not found on PATH, MAVEN_HOME or in common locations.
pause
exit /b 1

:mvn_found
if "!MVN_CMD!"=="" (
    echo [ERROR] MVN_CMD is empty; cannot continue.
    pause
    exit /b 1
)

echo [INFO] Using Maven command: !MVN_CMD!
echo [INFO] Project directory: %PROJECT_DIR%
echo.
echo ============================================
echo   Building WorldPainter (Hytale Edition)
echo   Fat JAR for distribution
echo ============================================
echo.

pushd "%PROJECT_DIR%"
call "!MVN_CMD!" -DskipTests=true package -pl WPGUI -am
set BUILD_EXIT=%ERRORLEVEL%
if %BUILD_EXIT% NEQ 0 (
    echo.
    echo [ERROR] Build failed. Check output above.
    popd
    pause
    exit /b 1
)

echo.
echo [INFO] Build succeeded. Copying fat JAR to dist folder...

set "DIST_DIR=%SCRIPT_DIR%dist"
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

set "FAT_JAR="
for %%f in (WPGUI\target\WPGUI-*-full.jar) do set "FAT_JAR=%%f"

if not defined FAT_JAR (
    echo [ERROR] Fat JAR not found in WPGUI\target\
    popd
    pause
    exit /b 1
)

copy /Y "%FAT_JAR%" "%DIST_DIR%\" >nul
popd

echo.
echo ============================================
echo   BUILD COMPLETE
echo ============================================
echo.
echo   Fat JAR:  %DIST_DIR%\
for %%f in ("%DIST_DIR%\WPGUI-*-full.jar") do echo              %%~nxf (%%~zf bytes)
echo.
echo   To distribute to your team:
echo   1. Zip the "dist" folder
echo   2. Share via Google Drive / Discord / etc.
echo   3. Team members need only JDK 17 installed
echo   4. They double-click run-worldpainter.bat
echo.
echo ============================================

endlocal
pause
exit /b 0
