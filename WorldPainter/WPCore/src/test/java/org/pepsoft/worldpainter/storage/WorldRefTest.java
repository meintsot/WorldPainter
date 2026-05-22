package org.pepsoft.worldpainter.storage;

import org.junit.Test;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.*;

public class WorldRefTest {

    @Test
    public void local_ref_carries_file() {
        File f = new File("/tmp/world.world");
        WorldRef ref = WorldRef.local(f);
        assertTrue(ref.isLocal());
        assertFalse(ref.isCloud());
        assertEquals(f, ref.localFile());
    }

    @Test
    public void cloud_ref_carries_uuid() {
        UUID id = UUID.randomUUID();
        WorldRef ref = WorldRef.cloud(id);
        assertFalse(ref.isLocal());
        assertTrue(ref.isCloud());
        assertEquals(id, ref.cloudWorldId());
    }

    @Test(expected = IllegalStateException.class)
    public void local_ref_throws_for_cloud_accessor() {
        WorldRef ref = WorldRef.local(new File("/tmp/x.world"));
        ref.cloudWorldId();
    }

    @Test(expected = IllegalStateException.class)
    public void cloud_ref_throws_for_local_accessor() {
        WorldRef ref = WorldRef.cloud(UUID.randomUUID());
        ref.localFile();
    }
}
