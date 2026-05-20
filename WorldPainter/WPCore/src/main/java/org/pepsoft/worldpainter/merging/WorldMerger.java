package org.pepsoft.worldpainter.merging;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.worldpainter.World2;

import java.io.File;
import java.io.IOException;

/**
 * Common contract implemented by world mergers across platforms (currently
 * {@link JavaWorldMerger} and
 * {@code org.pepsoft.worldpainter.hytale.export.HytaleWorldMerger}). The
 * interface only exposes what {@code MergeProgressDialog} and
 * {@code MergeWorldDialog} need to drive a merge end-to-end.
 *
 * <p>{@link #getWarnings()} returns a newline-separated, optionally HTML-flavoured
 * {@link String} (or {@code null} when there are none) to match the existing
 * {@link JavaWorldMerger#getWarnings()} signature; consumers feed the value
 * directly into {@code ImportWarningsDialog.setWarnings(String)}.
 */
public interface WorldMerger {
    World2 getWorld();
    File getMapDir();
    File selectBackupDir(File mapDir) throws IOException;
    void merge(File backupDir, ProgressReceiver progressReceiver) throws IOException, ProgressReceiver.OperationCancelled;
    boolean isAborted();
    String getWarnings();
}
