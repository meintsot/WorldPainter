package org.pepsoft.worldpainter.cloud;

import org.pepsoft.worldpainter.cloud.adapt.CloudWorld2;
import org.pepsoft.worldpainter.cloud.auth.CloudSession;
import org.pepsoft.worldpainter.cloud.auth.Session;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Bundles the {@code AbstractAction} instances for the Cloud menu in {@link
 * org.pepsoft.worldpainter.App}.
 *
 * <p>One instance per {@code App}. Actions enable/disable themselves based on the current
 * {@link CloudSession} state — Sign In is enabled when signed out, Sign Out and Open Cloud
 * World are enabled when signed in. Export/Import/Merge are additionally gated on the current
 * world being a {@link CloudWorld2}.
 */
public final class CloudMenuActions {

    private final Frame owner;

    public final Action signIn = new SignInAction();
    public final Action signOut = new SignOutAction();
    public final Action openCloudWorld = new OpenCloudWorldAction();
    public final Action exportOnCloud = new ExportOnCloudAction();
    public final Action importHytaleWorld = new ImportHytaleWorldAction();

    public CloudMenuActions(Frame owner) {
        this.owner = owner;
        updateEnablement();
        CloudSession.getInstance().addListener(new CloudSession.Listener() {
            @Override public void onSignIn(Session s)  { SwingUtilities.invokeLater(CloudMenuActions.this::updateEnablement); }
            @Override public void onSignOut()          { SwingUtilities.invokeLater(CloudMenuActions.this::updateEnablement); }
        });
    }

    /** Called by {@link org.pepsoft.worldpainter.App} whenever the open world changes. */
    public void updateEnablement() {
        boolean signedIn = CloudSession.getInstance().isSignedIn();
        boolean cloudWorldOpen = signedIn
                && (org.pepsoft.worldpainter.App.getInstance().getWorld()
                        instanceof CloudWorld2);
        signIn.setEnabled(!signedIn);
        signOut.setEnabled(signedIn);
        openCloudWorld.setEnabled(signedIn);
        exportOnCloud.setEnabled(cloudWorldOpen);
        importHytaleWorld.setEnabled(cloudWorldOpen);
    }

    private final class SignInAction extends AbstractAction {
        SignInAction() { super("Sign in…"); }
        @Override public void actionPerformed(ActionEvent e) {
            CloudSignInDialog.prompt(owner);
        }
    }

    private final class SignOutAction extends AbstractAction {
        SignOutAction() { super("Sign out"); }
        @Override public void actionPerformed(ActionEvent e) {
            CloudSession.getInstance().signOut();
        }
    }

    private final class OpenCloudWorldAction extends AbstractAction {
        OpenCloudWorldAction() { super("Open cloud world…"); }
        @Override public void actionPerformed(ActionEvent e) {
            CloudWorldsDialog.showAndPick(owner).ifPresent(sel -> {
                org.pepsoft.worldpainter.App app = org.pepsoft.worldpainter.App.getInstance();
                org.pepsoft.worldpainter.storage.WorldRef ref =
                        org.pepsoft.worldpainter.storage.WorldRef.cloud(sel.id());

                // Run the open in a SwingWorker so the network handshake doesn't block the EDT.
                javax.swing.SwingWorker<Void, Void> worker = new javax.swing.SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        app.openWorld(ref, null);
                        return null;
                    }
                    @Override
                    protected void done() {
                        try { get(); }
                        catch (Exception ex) {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            javax.swing.JOptionPane.showMessageDialog(owner,
                                    "Failed to open cloud world: " + cause.getMessage(),
                                    "Cloud open failed",
                                    javax.swing.JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
                worker.execute();
            });
        }
    }

    private final class ExportOnCloudAction extends AbstractAction {
        ExportOnCloudAction() { super("Export world on cloud…"); }
        @Override public void actionPerformed(ActionEvent e) {
            CloudWorld2 cloud = (CloudWorld2) org.pepsoft.worldpainter.App.getInstance().getWorld();
            new CloudExportAction(owner, cloud).actionPerformed(e);
        }
    }

    private final class ImportHytaleWorldAction extends AbstractAction {
        ImportHytaleWorldAction() { super("Import existing Hytale world…"); }
        @Override public void actionPerformed(ActionEvent e) {
            CloudWorld2 cloud = (CloudWorld2) org.pepsoft.worldpainter.App.getInstance().getWorld();
            new CloudHytaleImportAction(owner, cloud).actionPerformed(e);
        }
    }
}
