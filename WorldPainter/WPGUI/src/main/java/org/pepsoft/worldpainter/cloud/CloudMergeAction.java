package org.pepsoft.worldpainter.cloud;

import org.pepsoft.worldpainter.cloud.adapt.CloudWorld2;
import org.pepsoft.worldpainter.cloud.api.CloudJobsClient;
import org.pepsoft.worldpainter.cloud.api.CloudUploadsClient;
import org.pepsoft.worldpainter.cloud.auth.CloudSession;
import org.pepsoft.worldpainter.cloud.auth.Session;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

/**
 * Cloud → Merge with Hytale world…
 *
 * <p>Picks a source Hytale zip + destination folder, uploads source via presigned PUT,
 * submits MERGE_HYTALE job, polls. On DONE, downloads the merged result zip to the chosen
 * destination.
 */
public final class CloudMergeAction extends AbstractAction {

    private static final URI DEFAULT_BACKEND = URI.create(
            System.getProperty("worldpainter.cloud.backend", "http://localhost:8080"));

    private final Frame owner;
    private final CloudWorld2 world;

    public CloudMergeAction(Frame owner, CloudWorld2 world) {
        super("Merge with Hytale world…");
        this.owner = owner;
        this.world = world;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser srcChooser = new JFileChooser();
        srcChooser.setDialogTitle("Choose source Hytale world zip to merge with");
        srcChooser.setFileFilter(new FileNameExtensionFilter("Zip files", "zip"));
        if (srcChooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File srcZip = srcChooser.getSelectedFile();

        JFileChooser destChooser = new JFileChooser();
        destChooser.setDialogTitle("Choose folder for merged Hytale world");
        destChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (destChooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destDir = destChooser.getSelectedFile();

        Session session = CloudSession.getInstance().current().orElseThrow();
        CloudUploadsClient uploads = new CloudUploadsClient(DEFAULT_BACKEND, session.token());
        CloudJobsClient jobs = new CloudJobsClient(DEFAULT_BACKEND, session.token());

        SwingWorker<UUID, Void> worker = new SwingWorker<>() {
            @Override
            protected UUID doInBackground() {
                String blobKey = uploads.uploadZip(srcZip, "merge-input");
                CloudJobsClient.SubmitResult sr = jobs.submit(world.cloudWorldId(),
                        "MERGE_HYTALE", "{}", blobKey);
                return sr.jobId();
            }

            @Override
            protected void done() {
                try {
                    UUID jobId = get();
                    CloudJobProgressDialog dialog = new CloudJobProgressDialog(owner, jobs, jobId,
                            "Merging with Hytale world",
                            status -> downloadMergedResult(jobs, status, destDir));
                    dialog.startPolling();
                    dialog.setVisible(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(owner,
                            "Merge submit failed: " + ex.getMessage(),
                            "Cloud merge failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void downloadMergedResult(CloudJobsClient jobs, CloudJobsClient.JobStatus status, File destDir) {
        SwingWorker<File, Void> dl = new SwingWorker<>() {
            @Override
            protected File doInBackground() throws Exception {
                URL url = jobs.getResultUrl(status.id());
                File out = new File(destDir, "MergedWorld-" + status.id() + ".zip");
                try (var in = url.openStream(); var os = new FileOutputStream(out)) {
                    in.transferTo(os);
                }
                return out;
            }

            @Override
            protected void done() {
                try {
                    File f = get();
                    JOptionPane.showMessageDialog(owner,
                            "Merged Hytale world saved to:\n" + f.getAbsolutePath(),
                            "Merge complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(owner,
                            "Download failed: " + ex.getMessage(),
                            "Cloud merge failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        dl.execute();
    }
}
