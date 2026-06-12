package org.pepsoft.worldpainter.swing;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.util.swing.ProgressComponent.Listener;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.util.swing.ScrollablePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static org.pepsoft.util.AwtUtils.doOnEventThreadAndWait;
import static org.pepsoft.util.ExceptionUtils.chainContains;
import static org.pepsoft.util.mdc.MDCUtils.decorateWithMdcContext;

/**
 * A component which can execute a task in the background, reporting its progress on a progress bar, displaying status
 * messages from the task, and optionally allowing the user to cancel the task.
 *
 * <p>Derived from {@code org.pepsoft.util.swing.MultiProgressComponent}, with the time remaining estimate replaced
 * (TP-128): instead of a linear extrapolation over the whole run — which hovers at a near-constant value for hours
 * when the task enters a slow tail phase — the estimate is computed by a {@link RollingEtaEstimator} from the rate of
 * progress over a recent window. A complete stall is detected and reported as such, and the progress bar shows the
 * percentage so that slow progress remains visibly distinguishable from no progress.
 *
 * @author pepijn
 */
@SuppressWarnings({"Convert2Lambda", "Anonymous2MethodRef", "unused"}) // Managed by NetBeans
public class WindowedMultiProgressComponent<T> extends javax.swing.JPanel implements ProgressReceiver, ActionListener {
    /**
     * Creates a new WindowedMultiProgressComponent
     */
    public WindowedMultiProgressComponent() {
        initComponents();

        // Size the scroll pane so that it can show eight progress viewers
        Dimension panelPrefdSize = scrollablePanel1.getPreferredSize();
        ProgressViewer testProgressViewer = new ProgressViewer();
        scrollablePanel1.add(testProgressViewer);
        panelPrefdSize.setSize(panelPrefdSize.getWidth(), testProgressViewer.getPreferredSize().getHeight() * 8);
        scrollablePanel1.remove(testProgressViewer);
        jScrollPane1.setMinimumSize(panelPrefdSize);

        scrollablePanel1.setTrackViewportWidth(true);
        scrollablePanel1.setTrackViewportHeight(false);
    }

    public void setListener(Listener<T> listener) {
        this.listener = listener;
    }

    public Listener<T> getListener() {
        return listener;
    }

    public void setTask(ProgressTask<T> task) {
        this.task = task;
    }

    public ProgressTask<?> getTask() {
        return task;
    }

    public void setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
    }

    public boolean getCancelable() {
        return cancelable;
    }

    public void start() {
        jButton1.setEnabled(cancelable);
        jProgressBar1.setIndeterminate(true);
        final Thread thread = new Thread(task.getName()) {
            @Override
            public void run() {
                try {
                    result = task.execute(WindowedMultiProgressComponent.this);
                    done();
                } catch (Throwable t) {
                    exceptionThrown(t);
                }
            }
        };
        thread.start();
        timer = new Timer(1000, this);
        timer.start();
    }

    /**
     * Add a {@link JButton} to the panel, to the left of the Cancel button.
     *
     * @param button The button to add.
     */
    public void addButton(JButton button) {
        jPanel1.add(button, 0);
        jPanel1.add(Box.createHorizontalStrut(5), 1);
    }

    // ProgressReceiver

    @Override
    public void setProgress(final float progress) throws OperationCancelled {
        checkForCancellation();
        etaEstimator.progressReported(progress, System.currentTimeMillis());
        doOnEventThreadAndWait(() -> {
            if (jProgressBar1.isIndeterminate()) {
                jProgressBar1.setIndeterminate(false);
                jProgressBar1.setStringPainted(true);
            }
            jProgressBar1.setValue(Math.round(progress * 100f));
        });
    }

    @Override
    public void exceptionThrown(final Throwable exception) {
        if (! exceptionReported) {
            // Make sure to capture the MDC context from the current thread
            final Throwable exceptionWithContext = decorateWithMdcContext(exception);
            doOnEventThreadAndWait(() -> {
                timer.stop();
                if (jProgressBar1.isIndeterminate()) {
                    jProgressBar1.setIndeterminate(false);
                }
                jButton1.setEnabled(false);
                inhibitDone = true;
                if (chainContains(exception, OperationCancelled.class)) {
                    jLabel2.setText("Cancelled");
                    if (listener != null) {
                        listener.cancelled();
                    }
                } else {
                    jLabel2.setText("Error");
                    if (listener != null) {
                        listener.exceptionThrown(exceptionWithContext);
                    }
                }
            });
            exceptionReported = true;
        }
    }

    @Override
    public void done() {
        doOnEventThreadAndWait(() -> {
            timer.stop();
            if (jProgressBar1.isIndeterminate()) {
                jProgressBar1.setIndeterminate(false);
            }
            jProgressBar1.setValue(100);
            jButton1.setEnabled(false);
            jLabel2.setText("Done");
            scrollablePanel1.removeAll();
            if ((listener != null) && (! inhibitDone)) {
                listener.done(result);
            }
        });
    }

    @Override
    public void setMessage(final String message) throws OperationCancelled {
        checkForCancellation();
    }

    @Override
    public void checkForCancellation() throws OperationCancelled {
        if (cancelRequested) {
            throw new OperationCancelledByUser();
        }
    }

    @Override
    public void reset() throws OperationCancelled {
        checkForCancellation();
        etaEstimator.reset();
        doOnEventThreadAndWait(() -> {
            jProgressBar1.setStringPainted(false);
            jProgressBar1.setIndeterminate(true);
            lastEtaText = null;
            jLabel2.setText(" ");
        });
    }

    @Override
    public void subProgressStarted(SubProgressReceiver subProgressReceiver) throws OperationCancelled {
        checkForCancellation();
        doOnEventThreadAndWait(() -> {
            ProgressViewer progressViewer = new ProgressViewer(subProgressReceiver);
            ProgressReceiver parent = subProgressReceiver.getParent();
            if (parent == null) {
                // No parent; insert at start
                scrollablePanel1.add(progressViewer, 0);
            } else {
                boolean parentFound = false;
                do {
                    for (int i = 0; i < scrollablePanel1.getComponentCount(); i++) {
                        Component component = scrollablePanel1.getComponent(i);
                        ProgressViewer parentViewer = (ProgressViewer) ((component instanceof ProgressViewer) ? component : ((JPanel) component).getComponent(1));
                        if (parentViewer.getSubProgressReceiver() == parent) {
                            // Progress viewer for parent found; insert below
                            Integer parentIndentation = (Integer) parentViewer.getClientProperty(CLIENT_PROPERTY_INDENTATION);
                            int indentation = (parentIndentation != null) ? parentIndentation + 1 : 1;
                            JPanel progressPanel = new JPanel();
                            progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.LINE_AXIS));
                            progressPanel.add(Box.createHorizontalStrut(indentation * INDENTATION_SIZE));
                            progressPanel.add(progressViewer);
                            scrollablePanel1.add(progressPanel, i + 1);
                            parentFound = true;
                            break;
                        }
                    }
                    if (parent instanceof SubProgressReceiver) {
                        parent = ((SubProgressReceiver) parent).getParent();
                    } else {
                        parent = null;
                    }
                } while ((! parentFound) && (parent != null));
                if (! parentFound) {
                    // Progress viewer not found for any ancestor; append to end
                    scrollablePanel1.add(progressViewer);
                }
            }

            subProgressReceiver.addListener(new ProgressReceiver() {
                @Override
                public void setProgress(float progress) {
                    if (progress >= 1.0f) {
                        doOnEventThreadAndWait(() -> removeViewerHierarchy(subProgressReceiver));
                    }
                }

                @Override
                public void exceptionThrown(Throwable exception) {
                    doOnEventThreadAndWait(() -> removeViewerHierarchy(subProgressReceiver));
                }

                @Override
                public void done() {
                    doOnEventThreadAndWait(() -> removeViewerHierarchy(subProgressReceiver));
                }

                /**
                 * Remove a particular viewer, and any children which may
                 * still exist (this happens in the wild; not entirely clear
                 * why; may be because they never started any progress;
                 * perhaps some kind of race condition).
                 */
                private void removeViewerHierarchy(SubProgressReceiver subProgressReceiver) {
                    // Remove any children
                    for (Component component: scrollablePanel1.getComponents()) {
                        ProgressViewer viewer = (ProgressViewer) ((component instanceof ProgressViewer) ? component : ((JPanel) component).getComponent(1));
                        if (viewer.getSubProgressReceiver().getParent() == subProgressReceiver) {
                            if (logger.isTraceEnabled()) {
                                logger.trace("Progress receiver still has child; removing child. Stack trace is of child creation", viewer.getSubProgressReceiver().getCreationTrace());
                            }
                            removeViewerHierarchy(viewer.getSubProgressReceiver());
                        }
                    }

                    // Remove the viewer for this sub progress receiver
                    // itself
                    for (Component component: scrollablePanel1.getComponents()) {
                        ProgressViewer viewer = (ProgressViewer) ((component instanceof ProgressViewer) ? component : ((JPanel) component).getComponent(1));
                        if (viewer.getSubProgressReceiver() == subProgressReceiver) {
                            scrollablePanel1.remove(component);
                            break;
                        }
                    }

                    jScrollPane1.validate();
                }

                @Override public void setMessage(String message) {}
                @Override public void checkForCancellation() {}
                @Override public void reset() {}
                @Override public void subProgressStarted(SubProgressReceiver subProgressReceiver) {}
            });
            jScrollPane1.validate();
        });
    }

    // ActionListener

    @Override
    public void actionPerformed(ActionEvent e) {
        final long now = System.currentTimeMillis();
        final String text;
        if (etaEstimator.isStalled(now)) {
            text = "No recent progress";
        } else {
            final long remaining = etaEstimator.remainingMillis(now);
            if (remaining == RollingEtaEstimator.UNKNOWN) {
                text = " ";
            } else {
                final int minutes = (int) (remaining / 60000);
                if (minutes < 1) {
                    text = "Less than a minute remaining";
                } else if (minutes < 90) {
                    text = "About " + (minutes + 1) + " minutes remaining";
                } else {
                    final int hours = (minutes + 30) / 60;
                    text = "About " + hours + " hours remaining";
                }
            }
        }
        if (! text.equals(lastEtaText)) {
            lastEtaText = text;
            jLabel2.setText(text);
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     */
    private void initComponents() {

        jProgressBar1 = new javax.swing.JProgressBar();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        scrollablePanel1 = new ScrollablePanel();
        jPanel1 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();

        jLabel2.setText(" ");

        jScrollPane1.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        scrollablePanel1.setLayout(new java.awt.GridLayout(0, 1));
        jScrollPane1.setViewportView(scrollablePanel1);

        jPanel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 0, 0));

        jButton1.setText("Cancel");
        jButton1.setEnabled(false);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel2)
                .addGap(0, 0, 0)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jProgressBar1, javax.swing.GroupLayout.DEFAULT_SIZE, 382, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 127, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jProgressBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2)
                    .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );
    }

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {
        cancelRequested = true;
        jButton1.setEnabled(false);
    }

    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private ScrollablePanel scrollablePanel1;

    private ProgressTask<T> task;
    private volatile boolean cancelRequested, exceptionReported;
    private volatile T result;
    private Timer timer;
    private Listener<T> listener;
    private boolean cancelable = true, inhibitDone;
    private String lastEtaText;
    private final RollingEtaEstimator etaEstimator = new RollingEtaEstimator(ETA_WINDOW_MILLIS, ETA_MIN_SPAN_MILLIS);

    private static final long ETA_WINDOW_MILLIS = 120_000L;
    private static final long ETA_MIN_SPAN_MILLIS = 30_000L;
    private static final String CLIENT_PROPERTY_INDENTATION = WindowedMultiProgressComponent.class.getName() + ".indentation";
    private static final int INDENTATION_SIZE = 32;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WindowedMultiProgressComponent.class);
    private static final long serialVersionUID = 1L;
}
