@echo off
title WorldPainter (Hytale Edition)

:: Check for Java
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Java not found. Please install JDK 17 from:
    echo   https://learn.microsoft.com/en-us/java/openjdk/download
    echo.
    pause
    exit /b 1
)

:: Get the directory this script is in
set "SCRIPT_DIR=%~dp0"

:: Find the JAR file
for %%f in ("%SCRIPT_DIR%WPGUI-*-full.jar") do set "JAR_FILE=%%f"

if not defined JAR_FILE (
    echo.
    echo ERROR: Could not find WPGUI-*-full.jar in %SCRIPT_DIR%
    echo.
    pause
    exit /b 1
)

echo Starting WorldPainter (Hytale Edition)...
echo.

java --add-opens java.desktop/sun.swing=ALL-UNNAMED ^
     --add-opens java.desktop/javax.swing.plaf.basic=ALL-UNNAMED ^
     --add-opens java.desktop/java.awt=ALL-UNNAMED ^
     --add-opens java.desktop/sun.awt=ALL-UNNAMED ^
     --add-opens java.base/java.lang=ALL-UNNAMED ^
     -Xmx10G ^
     -jar "%JAR_FILE%"

if %ERRORLEVEL% neq 0 (
    echo.
    echo WorldPainter exited with an error. Check the output above.
    pause
)
