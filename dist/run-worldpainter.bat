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
set "HYTALE_ASSETS_DIR=%SCRIPT_DIR%HytaleAssets"

:: Find the JAR file
for %%f in ("%SCRIPT_DIR%WPGUI-*-full.jar") do set "JAR_FILE=%%f"

if not defined JAR_FILE (
    echo.
    echo ERROR: Could not find WPGUI-*-full.jar in %SCRIPT_DIR%
    echo.
    pause
    exit /b 1
)

if not exist "%HYTALE_ASSETS_DIR%\Common\Icons\ItemsGenerated" if not exist "%HYTALE_ASSETS_DIR%\Common\Icons\Items" if not exist "%HYTALE_ASSETS_DIR%\Common\Items" (
    echo.
    echo ERROR: Could not find the bundled HytaleAssets subset in %HYTALE_ASSETS_DIR%
    echo        Make sure you extracted and kept the full dist folder.
    echo.
    pause
    exit /b 1
)

echo Starting WorldPainter (Hytale Edition)...
echo.

if not defined WP_MAX_THREADS set "WP_MAX_THREADS=1"

java --add-opens java.desktop/sun.swing=ALL-UNNAMED ^
     --add-opens java.desktop/javax.swing.plaf.basic=ALL-UNNAMED ^
     --add-opens java.desktop/java.awt=ALL-UNNAMED ^
     --add-opens java.desktop/sun.awt=ALL-UNNAMED ^
     --add-opens java.base/java.lang=ALL-UNNAMED ^
    -Dorg.pepsoft.worldpainter.classifier=hytale ^
    -Dorg.pepsoft.worldpainter.threads=%WP_MAX_THREADS% ^
    -Dorg.pepsoft.worldpainter.hytaleAssetsDir="%HYTALE_ASSETS_DIR%" ^
     -Xmx10G ^
     -jar "%JAR_FILE%"

if %ERRORLEVEL% neq 0 (
    echo.
    echo WorldPainter exited with an error. Check the output above.
    pause
)
