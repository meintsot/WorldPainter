package org.pepsoft.worldpainter.cloud;

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
 * World are enabled when signed in.
 */
public final class CloudMenuActions {

    private final Frame owner;

    public final Action signIn = new SignInAction();
    public final Action signOut = new SignOutAction();
    public final Action openCloudWorld = new OpenCloudWorldAction();

    public CloudMenuActions(Frame owner) {
        this.owner = owner;
        updateEnablement();
        CloudSession.getInstance().addListener(new CloudSession.Listener() {
            @Override public void onSignIn(Session s)  { SwingUtilities.invokeLater(CloudMenuActions.this::updateEnablement); }
            @Override public void onSignOut()          { SwingUtilities.invokeLater(CloudMenuActions.this::updateEnablement); }
        });
    }

    private void updateEnablement() {
        boolean signedIn = CloudSession.getInstance().isSignedIn();
        signIn.setEnabled(!signedIn);
        signOut.setEnabled(signedIn);
        openCloudWorld.setEnabled(signedIn);
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
                CloudEditorFrame frame = new CloudEditorFrame(sel.id(), sel.name());
                frame.connectAndShow();
            });
        }
    }
}
