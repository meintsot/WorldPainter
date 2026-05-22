package org.pepsoft.worldpainter.cloud;

import org.pepsoft.worldpainter.cloud.auth.AuthClient;
import org.pepsoft.worldpainter.cloud.auth.CloudSession;
import org.pepsoft.worldpainter.cloud.auth.Session;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

/**
 * Modal sign-in dialog. Prompts the user for a display name, calls {@link AuthClient#login},
 * and stores the result in {@link CloudSession}.
 *
 * <p>Phase 0 uses {@code NoOpAuthProvider} on the backend, so "sign-in" is really just
 * "claim a display name". Phase 1 will swap this for OAuth.
 */
public final class CloudSignInDialog extends JDialog {

    private static final URI DEFAULT_BACKEND = URI.create(
            System.getProperty("worldpainter.cloud.backend", "http://localhost:8080"));

    private final JTextField nameField = new JTextField(20);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton signInButton = new JButton("Sign in");
    private final JButton cancelButton = new JButton("Cancel");

    public CloudSignInDialog(Frame owner) {
        super(owner, "Sign in to TalePainter Cloud", true);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.LINE_START;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Display name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        statusLabel.setForeground(new Color(180, 0, 0));
        form.add(statusLabel, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(signInButton);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(signInButton);

        signInButton.addActionListener(e -> attemptSignIn());
        cancelButton.addActionListener(e -> setVisible(false));

        pack();
        setLocationRelativeTo(owner);
    }

    private void attemptSignIn() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            statusLabel.setText("Please enter a display name.");
            return;
        }
        signInButton.setEnabled(false);
        cancelButton.setEnabled(false);
        statusLabel.setForeground(new Color(0, 80, 0));
        statusLabel.setText("Connecting…");

        SwingWorker<Session, Void> worker = new SwingWorker<>() {
            @Override
            protected Session doInBackground() {
                AuthClient client = new AuthClient(DEFAULT_BACKEND);
                return client.login(name);
            }

            @Override
            protected void done() {
                try {
                    Session session = get();
                    CloudSession.getInstance().signIn(session);
                    setVisible(false);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setForeground(new Color(180, 0, 0));
                    statusLabel.setText("Sign-in failed: " + truncate(cause.getMessage(), 60));
                    signInButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "Unknown error";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Convenience: show the dialog modally and return whether sign-in succeeded. */
    public static boolean prompt(Frame owner) {
        CloudSignInDialog dialog = new CloudSignInDialog(owner);
        dialog.setVisible(true);
        return CloudSession.getInstance().isSignedIn();
    }
}
