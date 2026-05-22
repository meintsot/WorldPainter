package org.pepsoft.worldpainter.cloud;

import org.pepsoft.worldpainter.cloud.adapt.CloudWorld2;
import org.pepsoft.worldpainter.cloud.api.CloudJobsClient;
import org.pepsoft.worldpainter.cloud.auth.CloudSession;
import org.pepsoft.worldpainter.cloud.auth.Session;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

/**
 * Cloud → Export world on cloud…
 *
 * <p>Submits an EXPORT_HYTALE job, shows the progress dialog, downloads the resulting zip
 * to a user-chosen folder. The export runs entirely server-side on a worker subprocess —
 * the client only submits + polls + downloads.
 */
public final class CloudExportAction extends AbstractAction {

    private static final URI DEFAULT_BACKEND = URI.create(
            System.getProperty("worldpainter.cloud.backend", "http://localhost:8080"));

    private final Frame owner;
    private final CloudWorld2 world;

    public CloudExportAction(Frame owner, CloudWorld2 world) {
        super("Export world on cloud…");
        this.owner = owner;
        this.world = world;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose folder to save exported Hytale world zip");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destDir = chooser.getSelectedFile();

        Session session = CloudSession.getInstance().current().orElseThrow();
        CloudJobsClient jobs = new CloudJobsClient(DEFAULT_BACKEND, session.token());

        CloudJobsClient.SubmitResult submit;
        try {
            submit = jobs.submit(world.cloudWorldId(), "EXPORT_HYTALE", "{}", null);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner,
                    "Submit failed: " + ex.getMessage(),
                    "Cloud export failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        CloudJobProgressDialog dialog = new CloudJobProgressDialog(owner, jobs, submit.jobId(),
                "Exporting on cloud",
                status -> downloadResult(jobs, status, destDir));
        dialog.startPolling();
        dialog.setVisible(true);
    }

    private void downloadResult(CloudJobsClient jobs, CloudJobsClient.JobStatus status, File destDir) {
        SwingWorker<File, Void> dl = new SwingWorker<>() {
            @Override
            protected File doInBackground() throws Exception {
                URL url = jobs.getResultUrl(status.id());
                File outFile = new File(destDir, "ExportedWorld-" + status.id() + ".zip");
                try (var in = url.openStream(); var out = new FileOutputStream(outFile)) {
                    in.transferTo(out);
                }
                return outFile;
            }

            @Override
            protected void done() {
                try {
                    File f = get();
                    JOptionPane.showMessageDialog(owner,
                            "Export saved to:\n" + f.getAbsolutePath(),
                            "Cloud export complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(owner,
                            "Download failed: " + ex.getMessage(),
                            "Cloud export failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        dl.execute();
    }
}
