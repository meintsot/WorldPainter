package org.pepsoft.worldpainter.cloud.adapt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteOpContextTest {

    @Test
    void default_is_not_applying_remote_op() {
        assertThat(RemoteOpContext.isApplyingRemote()).isFalse();
    }

    @Test
    void run_applying_remote_sets_then_clears() {
        boolean[] insideFlag = { false };
        RemoteOpContext.runApplyingRemote(() -> {
            insideFlag[0] = RemoteOpContext.isApplyingRemote();
        });
        assertThat(insideFlag[0]).isTrue();
        assertThat(RemoteOpContext.isApplyingRemote()).isFalse();
    }

    @Test
    void flag_is_thread_local() throws Exception {
        boolean[] otherThreadSawFlag = { false };
        Thread t = new Thread(() -> {
            RemoteOpContext.runApplyingRemote(() -> {
                if (RemoteOpContext.isApplyingRemote()) {
                    otherThreadSawFlag[0] = true;
                }
            });
        });
        t.start();
        t.join();
        assertThat(otherThreadSawFlag[0]).isTrue();   // the other thread saw it true
        assertThat(RemoteOpContext.isApplyingRemote()).isFalse();  // main thread never affected
    }

    @Test
    void nested_apply_preserves_outer_flag() {
        boolean[] inner = { false };
        boolean[] afterInner = { false };
        RemoteOpContext.runApplyingRemote(() -> {
            RemoteOpContext.runApplyingRemote(() -> { inner[0] = RemoteOpContext.isApplyingRemote(); });
            afterInner[0] = RemoteOpContext.isApplyingRemote();
        });
        assertThat(inner[0]).isTrue();
        assertThat(afterInner[0]).isTrue();   // outer scope still active
        assertThat(RemoteOpContext.isApplyingRemote()).isFalse();
    }
}
