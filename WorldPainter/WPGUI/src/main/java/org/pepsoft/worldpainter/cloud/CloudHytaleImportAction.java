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
import java.net.URI;
import java.util.UUID;

/**
 * Cloud → Import existing Hytale world…
 *
 * <p>Picks a Hytale world zip, uploads to BlobStore via presigned PUT, submits an
 * IMPORT_HYTALE job, polls for completion. On DONE, the cloud world contains the imported
 * terrain (the worker emits CRDT ops to the OpLog; no result zip is downloaded).
 */
public final class CloudHytaleImportAction extends AbstractAction {

    private static final URI DEFAULT_BACKEND = URI.create(
            System.getProperty("worldpainter.cloud.backend", "http://localhost:8080"));

    private final Frame owner;
    private final CloudWorld2 world;

    public CloudHytaleImportAction(Frame owner, CloudWorld2 world) {
        super("Import existing Hytale world…");
        this.owner = owner;
        this.world = world;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose Hytale world zip to import");
        chooser.setFileFilter(new FileNameExtensionFilter("Zip files", "zip"));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File zip = chooser.getSelectedFile();

        Session session = CloudSession.getInstance().current().orElseThrow();
        CloudUploadsClient uploads = new CloudUploadsClient(DEFAULT_BACKEND, session.token());
        CloudJobsClient jobs = new CloudJobsClient(DEFAULT_BACKEND, session.token());

        SwingWorker<UUID, Void> worker = new SwingWorker<>() {
            @Override
            protected UUID doInBackground() {
                String blobKey = uploads.uploadZip(zip, "hytale-import");
                CloudJobsClient.SubmitResult sr = jobs.submit(world.cloudWorldId(),
                        "IMPORT_HYTALE", "{}", blobKey);
                return sr.jobId();
            }

            @Override
            protected void done() {
                try {
                    UUID jobId = get();
                    CloudJobProgressDialog dialog = new CloudJobProgressDialog(owner, jobs, jobId,
                            "Importing Hytale world",
                            status -> JOptionPane.showMessageDialog(owner,
                                    "Hytale import complete.\nThe cloud world now contains "
                                            + "the imported terrain — pan around to see it.",
                                    "Import done", JOptionPane.INFORMATION_MESSAGE));
                    dialog.startPolling();
                    dialog.setVisible(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(owner,
                            "Import submit failed: " + ex.getMessage(),
                            "Cloud import failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
