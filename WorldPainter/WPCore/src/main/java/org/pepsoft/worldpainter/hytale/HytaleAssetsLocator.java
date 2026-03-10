package org.pepsoft.worldpainter.hytale;

import org.pepsoft.util.FileUtils;
import org.pepsoft.worldpainter.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resolves Hytale assets from either a directly extracted HytaleAssets folder or
 * a launcher installation containing Server and Assets.zip. For launcher installs,
 * a minimal local cache is materialised under the WorldPainter config directory.
 */
public final class HytaleAssetsLocator {
    private HytaleAssetsLocator() {
    }

    public static synchronized File ensureAssetsConfigured() {
        final File resolved = resolveAssetsDir();
        if (resolved != null) {
            HytaleTerrain.setHytaleAssetsDir(resolved);
        }
        return resolved;
    }

    public static synchronized File configureAssetsSource(File sourceDir) {
        final File resolved = resolveSource(sourceDir);
        if (resolved == null) {
            return null;
        }
        final Configuration configuration = Configuration.getInstance();
        if (configuration != null) {
            configuration.setHytaleAssetsSourceDirectory(sourceDir.getAbsoluteFile());
            try {
                configuration.save();
            } catch (IOException e) {
                logger.warn("Failed to persist configured Hytale assets source {}", sourceDir.getAbsolutePath(), e);
            }
        }
        HytaleTerrain.setHytaleAssetsDir(resolved);
        return resolved;
    }

    public static boolean hasPrefabAssets(File assetsDir) {
        return (assetsDir != null) && new File(assetsDir, "Server" + File.separator + "Prefabs").isDirectory();
    }

    public static boolean hasGameplayConfigs(File assetsDir) {
        return (assetsDir != null) && new File(assetsDir, "Server" + File.separator + "GameplayConfigs").isDirectory();
    }

    public static boolean isSelectableSource(File dir) {
        return normaliseSource(dir) != null;
    }

    private static File resolveAssetsDir() {
        final File current = HytaleTerrain.getHytaleAssetsDir();
        if (HytaleTerrain.hasUsableAssetsDir(current)) {
            return current;
        }

        final Configuration configuration = Configuration.getInstance();
        if (configuration != null) {
            final File configuredSource = configuration.getHytaleAssetsSourceDirectory();
            final File resolved = resolveSource(configuredSource);
            if (resolved != null) {
                return resolved;
            }
        }

        final String sysProp = System.getProperty(SYSTEM_PROPERTY_ASSETS_DIR);
        if ((sysProp != null) && (! sysProp.trim().isEmpty())) {
            final File resolved = resolveSource(new File(sysProp.trim()));
            if (resolved != null) {
                return resolved;
            }
        }

        for (File candidate : buildCandidateRoots()) {
            final File resolved = resolveSource(candidate);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    private static Set<File> buildCandidateRoots() {
        final Set<File> candidates = new LinkedHashSet<>();
        candidates.add(new File("HytaleAssets"));
        candidates.add(new File("..", "HytaleAssets"));
        candidates.add(new File(System.getProperty("user.dir"), "HytaleAssets"));
        candidates.add(new File(System.getProperty("user.dir"), ".." + File.separator + "HytaleAssets"));
        candidates.add(new File(System.getProperty("user.home"), "Desktop" + File.separator + "TalePainter" + File.separator + "HytaleAssets"));

        addWindowsCandidate(candidates, System.getenv("APPDATA"));
        addWindowsCandidate(candidates, System.getenv("LOCALAPPDATA"));
        addWindowsCandidate(candidates, System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming");
        addWindowsCandidate(candidates, System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Local");
        candidates.add(new File(System.getProperty("user.home"), "Hytale" + File.separator + "install" + File.separator + "release" + File.separator + "package" + File.separator + "game" + File.separator + "latest"));
        candidates.add(new File(System.getProperty("user.home"), "Application Support" + File.separator + "Hytale" + File.separator + "install" + File.separator + "release" + File.separator + "package" + File.separator + "game" + File.separator + "latest"));

        return candidates;
    }

    private static void addWindowsCandidate(Set<File> candidates, String baseDir) {
        if ((baseDir == null) || baseDir.trim().isEmpty()) {
            return;
        }
        candidates.add(new File(baseDir, "Hytale" + File.separator + "install" + File.separator + "release" + File.separator + "package" + File.separator + "game" + File.separator + "latest"));
    }

    private static File resolveSource(File sourceDir) {
        final File normalised = normaliseSource(sourceDir);
        if (normalised == null) {
            return null;
        }
        if (HytaleTerrain.hasUsableAssetsDir(normalised)) {
            return normalised;
        }
        if (isLauncherInstallDir(normalised)) {
            return materialiseCache(normalised);
        }
        return null;
    }

    private static File normaliseSource(File sourceDir) {
        if ((sourceDir == null) || (! sourceDir.isDirectory())) {
            return null;
        }
        final File[] candidates = {
                sourceDir,
                new File(sourceDir, "HytaleAssets"),
                new File(sourceDir, "install" + File.separator + "release" + File.separator + "package" + File.separator + "game" + File.separator + "latest"),
                new File(sourceDir, "release" + File.separator + "package" + File.separator + "game" + File.separator + "latest")
        };
        for (File candidate : candidates) {
            if (HytaleTerrain.hasUsableAssetsDir(candidate) || isLauncherInstallDir(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isLauncherInstallDir(File dir) {
        return (dir != null)
                && dir.isDirectory()
                && new File(dir, "Assets.zip").isFile()
                && new File(dir, "Server").isDirectory();
    }

    private static File materialiseCache(File installRoot) {
        final File assetsZip = new File(installRoot, "Assets.zip");
        final File serverDir = new File(installRoot, "Server");
        if ((! assetsZip.isFile()) || (! serverDir.isDirectory())) {
            return null;
        }

        final File cacheRoot = new File(Configuration.getConfigDir(), CACHE_DIR_NAME);
        final File extractedAssetsDir = new File(cacheRoot, "HytaleAssets");
        final File markerFile = new File(cacheRoot, CACHE_MARKER_NAME);

        if (cacheIsCurrent(extractedAssetsDir, markerFile, installRoot, assetsZip) && HytaleTerrain.hasUsableAssetsDir(extractedAssetsDir)) {
            return extractedAssetsDir;
        }

        try {
            if (extractedAssetsDir.exists() && (! FileUtils.deleteDir(extractedAssetsDir))) {
                logger.warn("Could not clear stale Hytale assets cache at {}", extractedAssetsDir.getAbsolutePath());
                return null;
            }
            if ((! cacheRoot.isDirectory()) && (! cacheRoot.mkdirs())) {
                logger.warn("Could not create Hytale assets cache directory {}", cacheRoot.getAbsolutePath());
                return null;
            }
            if ((! extractedAssetsDir.isDirectory()) && (! extractedAssetsDir.mkdirs())) {
                logger.warn("Could not create extracted Hytale assets directory {}", extractedAssetsDir.getAbsolutePath());
                return null;
            }

            extractCommonAssets(assetsZip, extractedAssetsDir);
            copyServerSubset(serverDir, extractedAssetsDir);
            writeCacheMarker(markerFile, installRoot, assetsZip);
            return HytaleTerrain.hasUsableAssetsDir(extractedAssetsDir) ? extractedAssetsDir : null;
        } catch (IOException e) {
            logger.warn("Failed to build Hytale assets cache from {}", installRoot.getAbsolutePath(), e);
            return null;
        }
    }

    private static boolean cacheIsCurrent(File extractedAssetsDir, File markerFile, File installRoot, File assetsZip) {
        if ((! extractedAssetsDir.isDirectory()) || (! markerFile.isFile())) {
            return false;
        }
        final Properties properties = new Properties();
        try (InputStream in = new FileInputStream(markerFile)) {
            properties.load(in);
        } catch (IOException e) {
            return false;
        }
        return installRoot.getAbsolutePath().equals(properties.getProperty(PROPERTY_SOURCE_PATH))
                && Long.toString(assetsZip.lastModified()).equals(properties.getProperty(PROPERTY_ASSETS_ZIP_LAST_MODIFIED))
                && Long.toString(assetsZip.length()).equals(properties.getProperty(PROPERTY_ASSETS_ZIP_LENGTH));
    }

    private static void writeCacheMarker(File markerFile, File installRoot, File assetsZip) throws IOException {
        final Properties properties = new Properties();
        properties.setProperty(PROPERTY_SOURCE_PATH, installRoot.getAbsolutePath());
        properties.setProperty(PROPERTY_ASSETS_ZIP_LAST_MODIFIED, Long.toString(assetsZip.lastModified()));
        properties.setProperty(PROPERTY_ASSETS_ZIP_LENGTH, Long.toString(assetsZip.length()));
        try (OutputStream out = new FileOutputStream(markerFile)) {
            properties.store(out, "Hytale assets cache metadata");
        }
    }

    private static void extractCommonAssets(File assetsZip, File extractedAssetsDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(assetsZip)) {
            final var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                final String entryName = entry.getName();
                if (! shouldExtract(entryName)) {
                    continue;
                }
                final File targetFile = createZipTarget(extractedAssetsDir, entryName);
                final File parentDir = targetFile.getParentFile();
                if ((parentDir != null) && (! parentDir.isDirectory()) && (! parentDir.mkdirs())) {
                    throw new IOException("Could not create directory " + parentDir.getAbsolutePath());
                }
                try (InputStream in = zipFile.getInputStream(entry); OutputStream out = new FileOutputStream(targetFile)) {
                    in.transferTo(out);
                }
            }
        }
    }

    private static File createZipTarget(File rootDir, String entryName) throws IOException {
        final File targetFile = new File(rootDir, entryName.replace('/', File.separatorChar));
        final String rootPath = rootDir.getCanonicalPath() + File.separator;
        final String targetPath = targetFile.getCanonicalPath();
        if (! targetPath.startsWith(rootPath)) {
            throw new IOException("Refusing to extract zip entry outside target directory: " + entryName);
        }
        return targetFile;
    }

    private static boolean shouldExtract(String entryName) {
        for (String prefix : COMMON_ASSET_PREFIXES) {
            if (entryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void copyServerSubset(File serverDir, File extractedAssetsDir) throws IOException {
        copyServerSubdir(serverDir, extractedAssetsDir, "BlockTypeList");
        copyServerSubdir(serverDir, extractedAssetsDir, "GameplayConfigs");
        copyServerSubdir(serverDir, extractedAssetsDir, "Prefabs");
    }

    private static void copyServerSubdir(File serverDir, File extractedAssetsDir, String name) throws IOException {
        final File sourceDir = new File(serverDir, name);
        if (! sourceDir.isDirectory()) {
            return;
        }
        final File targetDir = new File(new File(extractedAssetsDir, "Server"), name);
        FileUtils.copyDir(sourceDir, targetDir);
    }

    private static final Logger logger = LoggerFactory.getLogger(HytaleAssetsLocator.class);

    private static final String SYSTEM_PROPERTY_ASSETS_DIR = "org.pepsoft.worldpainter.hytaleAssetsDir";
    private static final String CACHE_DIR_NAME = "hytale-assets-cache";
    private static final String CACHE_MARKER_NAME = "cache.properties";
    private static final String PROPERTY_SOURCE_PATH = "source.path";
    private static final String PROPERTY_ASSETS_ZIP_LAST_MODIFIED = "assetsZip.lastModified";
    private static final String PROPERTY_ASSETS_ZIP_LENGTH = "assetsZip.length";
    private static final String[] COMMON_ASSET_PREFIXES = {
            "Common/BlockTextures/",
            "Common/Icons/ItemsGenerated/",
            "Common/Icons/Items/",
            "Common/Items/",
            "Common/UI/WorldMap/MapMarkers/"
    };
}