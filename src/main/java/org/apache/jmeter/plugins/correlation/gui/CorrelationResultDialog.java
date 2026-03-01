package org.apache.jmeter.plugins.correlation.gui;

import org.apache.jmeter.plugins.correlation.model.CorrelationCandidate;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog that presents detected correlation candidates in a table
 * with checkboxes for user review and selection.
 */
public class CorrelationResultDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final List<CorrelationCandidate> candidates;
    private final boolean[] selected;
    private List<CorrelationCandidate> selectedCandidates;
    private JTable table;

    public CorrelationResultDialog(Frame owner, List<CorrelationCandidate> candidates) {
        super(owner, "Auto-Correlation Results", true);
        this.candidates = candidates;
        this.selected = new boolean[candidates.size()];

        // Pre-select high confidence candidates
        for (int i = 0; i < candidates.size(); i++) {
            selected[i] = candidates.get(i).getConfidenceScore() >= 0.7;
        }

        initUI();
        setSize(900, 500);
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header
        JLabel headerLabel = new JLabel(
                "Correlation Candidates Found: " + candidates.size());
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
        add(headerLabel, BorderLayout.NORTH);

        // Table
        CandidateTableModel model = new CandidateTableModel();
        table = new JTable(model);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Column widths
        table.getColumnModel().getColumn(0).setMaxWidth(40);   // checkbox
        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(120); // parameter
        table.getColumnModel().getColumn(2).setPreferredWidth(90);  // type
        table.getColumnModel().getColumn(3).setPreferredWidth(90);  // extractor
        table.getColumnModel().getColumn(4).setPreferredWidth(120); // source
        table.getColumnModel().getColumn(5).setPreferredWidth(60);  // used in
        table.getColumnModel().getColumn(6).setPreferredWidth(65);  // confidence
        table.getColumnModel().getColumn(7).setPreferredWidth(180); // value

        // Center-align numeric columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

        JButton selectAllBtn = new JButton("Select All");
        selectAllBtn.addActionListener(e -> {
            for (int i = 0; i < selected.length; i++) {
                selected[i] = true;
            }
            model.fireTableDataChanged();
        });

        JButton deselectAllBtn = new JButton("Deselect All");
        deselectAllBtn.addActionListener(e -> {
            for (int i = 0; i < selected.length; i++) {
                selected[i] = false;
            }
            model.fireTableDataChanged();
        });

        JButton applyBtn = new JButton("Apply Selected");
        applyBtn.addActionListener(e -> {
            selectedCandidates = new ArrayList<>();
            for (int i = 0; i < selected.length; i++) {
                if (selected[i]) {
                    selectedCandidates.add(candidates.get(i));
                }
            }
            dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> {
            selectedCandidates = null;
            dispose();
        });

        buttonPanel.add(selectAllBtn);
        buttonPanel.add(deselectAllBtn);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(applyBtn);
        buttonPanel.add(cancelBtn);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Returns the list of user-selected candidates, or null if cancelled.
     */
    public List<CorrelationCandidate> getSelectedCandidates() {
        return selectedCandidates;
    }

    /**
     * Table model for the correlation candidates table.
     */
    private class CandidateTableModel extends AbstractTableModel {

        private final String[] COLUMNS = {
                "", "Parameter", "Type", "Extractor", "Found In", "Used In", "Confidence", "Sample Value"
        };

        @Override
        public int getRowCount() {
            return candidates.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Boolean.class;
            if (columnIndex == 5) return Integer.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0; // only checkbox is editable
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CorrelationCandidate c = candidates.get(rowIndex);
            switch (columnIndex) {
                case 0: return selected[rowIndex];
                case 1: return c.getParameterName();
                case 2: return c.getCorrelationType() != null
                        ? c.getCorrelationType().name() : "";
                case 3: return c.getExtractorType() != null
                        ? c.getExtractorType().name() : "";
                case 4: return c.getSourceSamplerName() != null
                        ? c.getSourceSamplerName() : "";
                case 5: return c.getUsageCount();
                case 6: return c.getConfidenceLevel();
                case 7:
                    String val = c.getSampleValue();
                    return val != null && val.length() > 35
                            ? val.substring(0, 35) + "..." : val;
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                selected[rowIndex] = (Boolean) aValue;
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}
