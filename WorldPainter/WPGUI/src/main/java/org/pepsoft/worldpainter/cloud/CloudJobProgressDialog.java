package org.pepsoft.worldpainter.cloud;

import org.pepsoft.worldpainter.cloud.api.CloudJobsClient;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Modal job-progress dialog used by Cloud → Export / Import / Merge actions. Polls
 * /v1/jobs/{id} every 500ms via {@link SwingWorker}; updates the progress bar; on DONE
 * invokes {@code onDone(JobStatus)} (caller typically downloads the result); on
 * FAILED/CANCELLED shows the error and stays open until user closes.
 */
public final class CloudJobProgressDialog extends JDialog {

    private final CloudJobsClient jobs;
    private final UUID jobId;
    private final String operationLabel;
    private final Consumer<CloudJobsClient.JobStatus> onDone;

    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton cancelButton = new JButton("Cancel");
    private volatile boolean stopPolling = false;

    public CloudJobProgressDialog(Frame owner, CloudJobsClient jobs, UUID jobId,
                                   String operationLabel,
                                   Consumer<CloudJobsClient.JobStatus> onDone) {
        super(owner, operationLabel, true);
        this.jobs = jobs;
        this.jobId = jobId;
        this.operationLabel = operationLabel;
        this.onDone = onDone;

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(420, 26));
        statusLabel.setForeground(new Color(80, 80, 80));

        add(progressBar, BorderLayout.NORTH);
        add(statusLabel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        add(buttons, BorderLayout.SOUTH);
        cancelButton.addActionListener(e -> onCancel());

        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    public void startPolling() {
        SwingWorker<Void, CloudJobsClient.JobStatus> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                while (!stopPolling) {
                    CloudJobsClient.JobStatus s = jobs.getStatus(jobId);
                    publish(s);
                    if (s.status().equals("DONE")
                            || s.status().equals("FAILED")
                            || s.status().equals("CANCELLED")) {
                        return null;
                    }
                    Thread.sleep(500);
                }
                return null;
            }
            @Override
            protected void process(java.util.List<CloudJobsClient.JobStatus> chunks) {
                CloudJobsClient.JobStatus latest = chunks.get(chunks.size() - 1);
                int p = latest.progressPct();
                progressBar.setValue(Math.max(0, p));
                progressBar.setString(p < 0 ? "Starting…" : (p + "%"));
                statusLabel.setText(operationLabel + " — " + latest.status());
            }
            @Override
            protected void done() {
                try {
                    CloudJobsClient.JobStatus s = jobs.getStatus(jobId);
                    if ("DONE".equals(s.status())) {
                        setVisible(false);
                        if (onDone != null) {
                            onDone.accept(s);
                        }
                    } else if ("CANCELLED".equals(s.status())) {
                        statusLabel.setText("Cancelled.");
                        replaceCancelWithClose();
                    } else if ("FAILED".equals(s.status())) {
                        statusLabel.setForeground(new Color(180, 0, 0));
                        statusLabel.setText("Failed: " + (s.errorMessage() == null
                                ? "(no message)" : s.errorMessage()));
                        replaceCancelWithClose();
                    }
                } catch (Exception ignored) {
                    statusLabel.setForeground(new Color(180, 0, 0));
                    statusLabel.setText("Polling error.");
                    replaceCancelWithClose();
                }
            }
        };
        worker.execute();
    }

    private void onCancel() {
        stopPolling = true;
        try { jobs.cancel(jobId); } catch (Exception ignored) {}
        statusLabel.setText("Cancelling…");
        cancelButton.setEnabled(false);
    }

    private void replaceCancelWithClose() {
        for (var l : cancelButton.getActionListeners()) {
            cancelButton.removeActionListener(l);
        }
        cancelButton.setText("Close");
        cancelButton.setEnabled(true);
        cancelButton.addActionListener(e -> setVisible(false));
    }
}
