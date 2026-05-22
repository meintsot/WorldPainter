package org.pepsoft.worldpainter.storage;

import org.junit.Test;
import org.pepsoft.worldpainter.World2;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LocalFileStorageBackendTest {

    @Test
    public void backend_advertises_local_kind() {
        LocalFileStorageBackend backend = new LocalFileStorageBackend();
        assertEquals(WorldRef.Kind.LOCAL, backend.kind());
    }

    @Test
    public void can_open_known_fixture_world() throws Exception {
        URL fixture = getClass().getResource("/testset/test-v2.3.6-1.world");
        if (fixture == null) {
            // Skip silently if no fixture is checked in
            return;
        }
        LocalFileStorageBackend backend = new LocalFileStorageBackend();
        World2 world = backend.open(WorldRef.local(new File(fixture.toURI())), null);
        assertNotNull(world);
    }
}
