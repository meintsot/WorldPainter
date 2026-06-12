package org.pepsoft.worldpainter.swing;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.util.mdc.MDCCapturingRuntimeException;

import static org.pepsoft.util.AwtUtils.doOnEventThreadAndWait;

/**
 * A component which can show the progress of a {@link SubProgressReceiver} by attaching to it as a listener.
 *
 * <p>Copied from {@code org.pepsoft.util.swing.ProgressViewer} (which is package private) for use by
 * {@link WindowedMultiProgressComponent} (TP-128).
 *
 * @author pepijn
 */
final class ProgressViewer extends javax.swing.JPanel implements ProgressReceiver {
    @SuppressWarnings("LeakingThisInConstructor") // Construction done at that point
    ProgressViewer(SubProgressReceiver subProgressReceiver) {
        this.subProgressReceiver = subProgressReceiver;
        initComponents();
        subProgressReceiver.addListener(this);
        String lastMessage = subProgressReceiver.getLastMessage();
        if (lastMessage != null) {
            try {
                setMessage(lastMessage);
            } catch (OperationCancelled operationCancelled) {
                throw new MDCCapturingRuntimeException("Operation cancelled");
            }
        }
    }

    ProgressViewer() {
        subProgressReceiver = null;
        initComponents();
    }

    SubProgressReceiver getSubProgressReceiver() {
        return subProgressReceiver;
    }

    // ProgressReceiver

    @Override
    public void setProgress(final float progress) {
        doOnEventThreadAndWait(() -> {
            if (jProgressBar1.isIndeterminate()) {
                jProgressBar1.setIndeterminate(false);
            }
            jProgressBar1.setValue(Math.round(progress * 100f));
        });
    }

    @Override
    public void exceptionThrown(final Throwable exception) {
        doOnEventThreadAndWait(() -> {
            if (jProgressBar1.isIndeterminate()) {
                jProgressBar1.setIndeterminate(false);
            }
        });
    }

    @Override
    public void done() {
        doOnEventThreadAndWait(() -> {
            if (jProgressBar1.isIndeterminate()) {
                jProgressBar1.setIndeterminate(false);
            }
            jProgressBar1.setValue(100);
        });
    }

    @Override
    public void setMessage(final String message) throws OperationCancelled {
        doOnEventThreadAndWait(() -> jLabel1.setText(message));
    }

    @Override
    public void checkForCancellation() {
        // Do nothing
    }

    @Override
    public void reset() {
        doOnEventThreadAndWait(() -> jProgressBar1.setIndeterminate(true));
    }

    @Override
    public void subProgressStarted(SubProgressReceiver subProgressReceiver) {
        // Do nothing
    }

    private void initComponents() {
        jLabel1 = new javax.swing.JLabel();
        jProgressBar1 = new javax.swing.JProgressBar();

        jLabel1.setText(" ");

        jProgressBar1.setIndeterminate(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jProgressBar1, javax.swing.GroupLayout.DEFAULT_SIZE, 318, Short.MAX_VALUE)
            .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jProgressBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }

    private javax.swing.JLabel jLabel1;
    private javax.swing.JProgressBar jProgressBar1;

    private final SubProgressReceiver subProgressReceiver;

    private static final long serialVersionUID = 1L;
}
