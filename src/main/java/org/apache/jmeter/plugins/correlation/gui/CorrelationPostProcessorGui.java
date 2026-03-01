package org.apache.jmeter.plugins.correlation.gui;

import org.apache.jmeter.plugins.correlation.CorrelationPostProcessor;
import org.apache.jmeter.plugins.correlation.engine.CorrelationApplicator;
import org.apache.jmeter.plugins.correlation.engine.CorrelationEngine;
import org.apache.jmeter.plugins.correlation.model.CorrelationCandidate;
import org.apache.jmeter.processor.gui.AbstractPostProcessorGui;
import org.apache.jmeter.testelement.TestElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * GUI for the Auto-Correlation Detector.
 * Appears in JMeter under Add > Post Processors > Auto-Correlation Detector.
 * Provides a "Click to Correlate" button that triggers the full correlation workflow:
 * replay test -> detect dynamic values -> show dialog -> apply selected correlations.
 */
public class CorrelationPostProcessorGui extends AbstractPostProcessorGui {

    private static final long serialVersionUID = 2L;
    private static final Logger log = LoggerFactory.getLogger(CorrelationPostProcessorGui.class);

    private JButton correlateButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    public CorrelationPostProcessorGui() {
        super();
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 8));
        setBorder(makeBorder());

        add(makeTitlePanel(), BorderLayout.NORTH);

        // Center panel with button and status
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Description
        JLabel descLabel = new JLabel(
                "<html><b>Auto-Correlation Detector</b><br><br>"
                + "This plugin automatically detects dynamic values (session IDs, CSRF tokens, "
                + "JWT, OAuth tokens, ViewState, etc.) in your recorded script and correlates them.<br><br>"
                + "<b>How to use:</b><br>"
                + "1. Record your script using HTTP(S) Test Script Recorder<br>"
                + "2. Add this element under your Thread Group<br>"
                + "3. Click the button below to start correlation<br>"
                + "4. The plugin will re-run your test to capture responses<br>"
                + "5. Review detected correlations and apply the ones you want</html>");
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(descLabel);

        centerPanel.add(Box.createVerticalStrut(20));

        // Correlate button
        correlateButton = new JButton("Click to Correlate");
        correlateButton.setFont(correlateButton.getFont().deriveFont(Font.BOLD, 16f));
        correlateButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        correlateButton.setMaximumSize(new Dimension(250, 50));
        correlateButton.setPreferredSize(new Dimension(250, 50));
        correlateButton.addActionListener(e -> onCorrelateClicked());
        centerPanel.add(correlateButton);

        centerPanel.add(Box.createVerticalStrut(12));

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setMaximumSize(new Dimension(250, 20));
        centerPanel.add(progressBar);

        centerPanel.add(Box.createVerticalStrut(8));

        // Status label
        statusLabel = new JLabel("Ready. Click the button above to start.");
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(statusLabel);

        add(centerPanel, BorderLayout.CENTER);
    }

    /**
     * Called when the "Click to Correlate" button is pressed.
     */
    private void onCorrelateClicked() {
        correlateButton.setEnabled(false);
        statusLabel.setText("Running test plan to capture responses...");
        progressBar.setVisible(true);

        new SwingWorker<List<CorrelationCandidate>, Void>() {
            private CorrelationEngine engine;

            @Override
            protected List<CorrelationCandidate> doInBackground() throws Exception {
                engine = new CorrelationEngine();
                return engine.execute();
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    List<CorrelationCandidate> candidates = get();

                    if (candidates == null || candidates.isEmpty()) {
                        statusLabel.setText("No correlation candidates found.");

                        // Build a diagnostic message showing where the pipeline stopped
                        String diagMsg = buildDiagnosticMessage(engine);
                        JOptionPane.showMessageDialog(
                                CorrelationPostProcessorGui.this,
                                diagMsg,
                                "No Correlations Found",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        statusLabel.setText("Found " + candidates.size()
                                + " correlation candidates. Showing dialog...");

                        // Show the result dialog
                        Frame frame = (Frame) SwingUtilities.getWindowAncestor(
                                CorrelationPostProcessorGui.this);
                        CorrelationResultDialog dialog =
                                new CorrelationResultDialog(frame, candidates);
                        dialog.setVisible(true); // modal, blocks here

                        List<CorrelationCandidate> selected =
                                dialog.getSelectedCandidates();
                        if (selected != null && !selected.isEmpty()) {
                            // Apply correlations
                            CorrelationApplicator applicator =
                                    new CorrelationApplicator();
                            applicator.apply(selected);

                            statusLabel.setText("Applied " + selected.size()
                                    + " correlations successfully.");
                            JOptionPane.showMessageDialog(
                                    CorrelationPostProcessorGui.this,
                                    "Successfully applied " + selected.size()
                                    + " correlations.\n\n"
                                    + "Extractors have been added and hardcoded "
                                    + "values replaced with variables.\n"
                                    + "You can now remove this element and run "
                                    + "your test plan.",
                                    "Correlations Applied",
                                    JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            statusLabel.setText("Correlation cancelled by user.");
                        }
                    }
                } catch (Exception ex) {
                    log.error("Correlation failed", ex);
                    statusLabel.setText("Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(
                            CorrelationPostProcessorGui.this,
                            "Correlation failed:\n" + ex.getMessage()
                            + "\n\nCheck the JMeter log for details.",
                            "Correlation Error",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    correlateButton.setEnabled(true);
                }
            }
        }.execute();
    }

    /**
     * Build a human-readable diagnostic message explaining why no candidates were found.
     */
    private String buildDiagnosticMessage(CorrelationEngine engine) {
        if (engine == null) {
            return "Correlation engine did not initialise — check the JMeter log for errors.";
        }

        int samplers  = engine.getLastSamplersFound();
        int results   = engine.getLastResultsCaptured();
        int raw       = engine.getLastRawCandidatesFound();
        int reused    = engine.getLastReusedCandidatesFound();

        StringBuilder sb = new StringBuilder();
        sb.append("No dynamic values found that are reused in subsequent requests.\n\n");
        sb.append("Pipeline diagnostics:\n");
        sb.append(String.format("  \u2022 HTTP samplers collected : %d%n", samplers));
        sb.append(String.format("  \u2022 Responses captured      : %d%n", results));
        sb.append(String.format("  \u2022 Raw candidates detected : %d%n", raw));
        sb.append(String.format("  \u2022 Reused in later requests: %d%n", reused));
        sb.append("\n");

        if (samplers == 0) {
            sb.append("CAUSE: No HTTP samplers found in the test plan.\n"
                    + "Make sure the plugin is inside a Thread Group that contains HTTP requests.");
        } else if (results == 0) {
            sb.append("CAUSE: Test replay produced no responses.\n"
                    + "The test run may have failed — check the JMeter log (jmeter.log) for errors.");
        } else if (raw == 0) {
            sb.append("CAUSE: Responses were captured but no dynamic values were detected.\n"
                    + "Check that the responses actually contain hidden fields, JSON tokens,\n"
                    + "session IDs, or CSRF tokens. Look at jmeter.log for detection details.");
        } else {
            sb.append("CAUSE: Dynamic values were detected but none appeared in later requests.\n"
                    + "Verify that subsequent samplers send those parameter names or values\n"
                    + "in their request body or URL.");
        }

        return sb.toString();
    }

    @Override
    public String getStaticLabel() {
        return "Auto-Correlation Detector";
    }

    @Override
    public String getLabelResource() {
        return getClass().getSimpleName();
    }

    @Override
    public TestElement createTestElement() {
        CorrelationPostProcessor element = new CorrelationPostProcessor();
        modifyTestElement(element);
        return element;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
    }

    @Override
    public void clearGui() {
        super.clearGui();
        statusLabel.setText("Ready. Click the button above to start.");
        progressBar.setVisible(false);
        correlateButton.setEnabled(true);
    }
}
