package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.worldpainter.Configuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HytaleAssetsLocatorTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final String CLASSIFIER_PROPERTY = "org.pepsoft.worldpainter.classifier";

    @Test
    public void testDirectAssetsDirectoryCanBeConfigured() throws Exception {
        final File assetsDir = tempFolder.newFolder("HytaleAssets");
        mkdirs(assetsDir, "Common/BlockTextures");
        mkdirs(assetsDir, "Server/GameplayConfigs");
        mkdirs(assetsDir, "Server/Prefabs");
        mkdirs(assetsDir, "Server/BlockTypeList");

        final Configuration previousConfiguration = Configuration.getInstance();
        final String previousClassifier = System.getProperty(CLASSIFIER_PROPERTY);
        try {
            System.setProperty(CLASSIFIER_PROPERTY, "hytale-assets-locator-test-direct");
            Configuration.setInstance(new Configuration());
            final File resolved = HytaleAssetsLocator.configureAssetsSource(assetsDir);

            assertNotNull(resolved);
            assertTrue(new File(resolved, "Server/Prefabs").isDirectory());
            assertTrue(new File(resolved, "Common/BlockTextures").isDirectory());
        } finally {
            restoreProperty(CLASSIFIER_PROPERTY, previousClassifier);
            Configuration.setInstance(previousConfiguration);
        }
    }

    @Test
    public void testLauncherInstallIsMaterialisedIntoMinimalCache() throws Exception {
        final File installRoot = tempFolder.newFolder("launcher-install");
        final File serverDir = mkdirs(installRoot, "Server");
        mkdirs(serverDir, "BlockTypeList");
        mkdirs(serverDir, "GameplayConfigs");
        mkdirs(serverDir, "Prefabs/Trees/Oak");
        writeText(new File(serverDir, "GameplayConfigs/Default.json"), "{}");
        writeText(new File(serverDir, "Prefabs/Trees/Oak/Test.prefab.json"), "{}");

        final File assetsZip = new File(installRoot, "Assets.zip");
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(assetsZip))) {
            addZipEntry(zipOut, "Common/BlockTextures/Blocks/Test.png", "png");
            addZipEntry(zipOut, "Common/Icons/ItemsGenerated/Test.png", "png");
            addZipEntry(zipOut, "Common/UI/WorldMap/MapMarkers/Test.png", "png");
            addZipEntry(zipOut, "Common/Unused/Ignore.txt", "ignored");
        }

        final Configuration previousConfiguration = Configuration.getInstance();
        final String previousClassifier = System.getProperty(CLASSIFIER_PROPERTY);
        try {
            System.setProperty(CLASSIFIER_PROPERTY, "hytale-assets-locator-test-launcher");
            Configuration.setInstance(new Configuration());
            final File resolved = HytaleAssetsLocator.configureAssetsSource(installRoot);

            assertNotNull(resolved);
            assertTrue(new File(resolved, "Common/BlockTextures/Blocks/Test.png").isFile());
            assertTrue(new File(resolved, "Server/Prefabs/Trees/Oak/Test.prefab.json").isFile());
            assertFalse(new File(resolved, "Common/Unused/Ignore.txt").exists());
        } finally {
            restoreProperty(CLASSIFIER_PROPERTY, previousClassifier);
            Configuration.setInstance(previousConfiguration);
        }
    }

    private static File mkdirs(File root, String relativePath) {
        final File dir = new File(root, relativePath.replace('/', File.separatorChar));
        assertTrue(dir.mkdirs() || dir.isDirectory());
        return dir;
    }

    private static void writeText(File file, String text) throws Exception {
        final File parentDir = file.getParentFile();
        if ((parentDir != null) && (! parentDir.isDirectory())) {
            assertTrue(parentDir.mkdirs());
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }

    private static void addZipEntry(ZipOutputStream zipOut, String name, String content) throws Exception {
        zipOut.putNextEntry(new ZipEntry(name));
        zipOut.write(content.getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}