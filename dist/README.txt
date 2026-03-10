TalePainter — Hytale Edition
==============================

Setup Instructions:
1. Install Java 17 (JDK) from: https://learn.microsoft.com/en-us/java/openjdk/download
2. Keep the entire dist folder together, including the packaged HytaleAssets folder
3. Double-click "run-worldpainter.bat" to launch

That's it! No other tools required.

Notes:
- Do not copy only the JAR file; the Hytale icons and supporting metadata are loaded from the bundled HytaleAssets folder
- The packaged HytaleAssets folder is intentionally trimmed to a small subset needed by TalePainter, not the full game asset dump
- If Windows SmartScreen blocks the .bat file, click "More info" → "Run anyway"
- Uses a separate "TalePainter [HYTALE]" config folder so it does not clash with regular TalePainter
- Allocates 10GB RAM by default. Edit the .bat file to change -Xmx10G if needed
- The prefab browser falls back to the bundled prefab catalog; full raw prefab JSON files are not included in this slim package
- To create a Hytale world: File → New → select "Hytale" as the platform
