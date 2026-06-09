package org.pepsoft.worldpainter.hytale.export;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.awt.Point;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class HytaleExportMetadataTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void writeThenReadRoundTripsTheOffset() throws Exception {
        File worldDir = tempDir.newFolder("world");
        HytaleExportMetadata.writeBlockOffset(worldDir, -512, 256);

        File sidecar = new File(worldDir, HytaleExportMetadata.SIDECAR_NAME);
        assertTrue("Sidecar file must be created", sidecar.isFile());

        Point read = HytaleExportMetadata.readBlockOffset(worldDir);
        assertNotNull(read);
        assertEquals(-512, read.x);
        assertEquals(256, read.y);
    }

    @Test
    public void readReturnsNullWhenAbsent() throws Exception {
        assertNull(HytaleExportMetadata.readBlockOffset(tempDir.newFolder("empty")));
    }

    @Test
    public void readReturnsNullWhenMalformed() throws Exception {
        File worldDir = tempDir.newFolder("bad");
        Files.write(new File(worldDir, HytaleExportMetadata.SIDECAR_NAME).toPath(),
                "{ not valid json".getBytes(StandardCharsets.UTF_8));
        assertNull("Malformed sidecar must read as null, not throw",
                HytaleExportMetadata.readBlockOffset(worldDir));
    }
}
