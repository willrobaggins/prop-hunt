package com.idyl.prophunt;

import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class PropHuntPanel extends PluginPanel implements ActionListener {
    private PropHuntPlugin plugin;

    private JButton myButton;
    private JToggleButton hideModeToggleButton;
    private JTextField modelIdTextField;

    public PropHuntPanel(PropHuntPlugin plugin) {
        this.plugin = plugin;

        // Set layout of the overall panel to BorderLayout
        setLayout(new BorderLayout());

        // Add padding to the top of the whole panel
        setBorder(BorderFactory.createEmptyBorder(40, 0, 0, 0));

        // Create a container panel for the title with padding
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BorderLayout());

        // Title label outside the bordered panel
        JLabel title = new JLabel("Prop Hunt", SwingConstants.CENTER);
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(30f));
        title.setForeground(Color.YELLOW);

        // Add the title label to the title panel
        titlePanel.add(title, BorderLayout.CENTER);

        // Add padding to the bottom of the title by setting an EmptyBorder on the title panel
        titlePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // Add the titlePanel (with padding) to the top of the main panel
        add(titlePanel, BorderLayout.NORTH);

        // Create subtitle section below the title
        JPanel subtitlePanel = new JPanel();
        JLabel subtitle = new JLabel("Hider Settings:");
        subtitle.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        subtitlePanel.add(subtitle);

        // Add padding to the subtitle
        subtitlePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        // Add the subtitlePanel right below the title section
        add(subtitlePanel, BorderLayout.CENTER);

        // Create the "Model ID" section panel with components
        JPanel modelPanel = createModelPanel();

        // Create the "Orientation" section panel with components
        JPanel orientationPanel = createOrientationPanel();

        // Create the "Hide Mode" section panel with the toggle button
        JPanel hideModePanel = createHideModePanel();

        // Add all sections to the main panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.add(modelPanel);
        contentPanel.add(orientationPanel);
        contentPanel.add(hideModePanel);

        add(contentPanel, BorderLayout.SOUTH);
    }

    private JPanel createModelPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setToolTipText("Set your Model ID.");
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Model"),
                BorderFactory.createEmptyBorder(2, 0, 7, 0)
        ));
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(5, 0, 5, 0);
        JLabel modelIdLabel = new JLabel("Model ID");
        panel.add(modelIdLabel, gbc);

        modelIdTextField = new JTextField(10);
        modelIdTextField.setText(String.valueOf(plugin.getModelId()));
        ((AbstractDocument) modelIdTextField.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        onCommit(modelIdTextField, () -> {
            Integer modelId = parseOrNull(modelIdTextField.getText());
            if (modelId == null) {
                modelIdTextField.setText(String.valueOf(plugin.getModelId()));
            } else if (modelId != plugin.getModelId()) {
                plugin.setModelID(new PropHuntModelId("", modelId));
            }
        });
        gbc.gridx = 1;
        panel.add(modelIdTextField, gbc);

        JPanel randomPanel = new JPanel();
        randomPanel.setLayout(new GridBagLayout());
        randomPanel.setToolTipText("Set the range of the model IDs you would like to randomize from.");
        randomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Randomizer"),
                BorderFactory.createEmptyBorder(2, 30, 7, 30)
        ));

        // Add min ID label and field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(5, 0, 5, 0);
        JLabel minLabel = new JLabel("Min ID");
        randomPanel.add(minLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.insets = new Insets(5, 0, 5, 0);
        JTextField minField = new JTextField(Integer.toString(plugin.getMin()), 4);
        ((AbstractDocument) minField.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        onCommit(minField, () -> {
            Integer minValue = parseOrNull(minField.getText());
            if (minValue == null) {
                minField.setText(Integer.toString(plugin.getMin()));
            } else if (minValue != plugin.getMin()) {
                plugin.setMinModelID(minValue);
            }
        });
        randomPanel.add(minField, gbc);

        // Add max ID label and field
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel maxLabel = new JLabel("Max ID");
        randomPanel.add(maxLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        JTextField maxField = new JTextField(Integer.toString(plugin.getMax()), 4);
        ((AbstractDocument) maxField.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        onCommit(maxField, () -> {
            Integer maxValue = parseOrNull(maxField.getText());
            if (maxValue == null) {
                maxField.setText(Integer.toString(plugin.getMax()));
            } else if (maxValue != plugin.getMax()) {
                plugin.setMaxModelID(maxValue);
            }
        });
        randomPanel.add(maxField, gbc);

        // Add randomize button inside the randomPanel
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;  // Make the button span both columns
        gbc.insets = new Insets(10, 0, 10, 0);
        myButton = new JButton("Randomize Model");
        myButton.addActionListener(this);
        randomPanel.add(myButton, gbc);

        // Add the randomPanel to the main panel
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;  // Make this panel span across both columns of the grid
        gbc.insets = new Insets(10, 0, 10, 0);
        panel.add(randomPanel, gbc);

        return panel;
    }


    private JPanel createOrientationPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setToolTipText("Rotate model orientation.");
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Orientation"),  // Original titled border
                BorderFactory.createEmptyBorder(2, 0, 7, 0)  // 10px top padding, 0 for left, right, and bottom
        ));
        GridBagConstraints gbc = new GridBagConstraints();

        // Rotate clockwise button
        gbc.gridx = 0;
        gbc.gridy = 0;
        JButton rotateClockwiseButton = new JButton("↻");
        rotateClockwiseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                plugin.rotateModel(1);
            }
        });
        panel.add(rotateClockwiseButton, gbc);

        // Rotate label
        gbc.gridx = 1;
        gbc.gridy = 0;
        JLabel rotateLabel = new JLabel("Rotate");
        panel.add(rotateLabel, gbc);

        // Rotate counter-clockwise button
        gbc.gridx = 2;
        gbc.gridy = 0;
        JButton rotateCounterButton = new JButton("↺");
        rotateCounterButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                plugin.rotateModel(-1);
            }
        });
        panel.add(rotateCounterButton, gbc);

        return panel;
    }

    public JPanel createHideModePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0)); // No label, just padding

        hideModeToggleButton = new JToggleButton();
        hideModeToggleButton.setSelected(plugin.getHideMode());
        if(plugin.getHideMode()) {
            hideModeToggleButton.setText("Hide Mode: ON");
        }
        else {
            hideModeToggleButton.setText("Hide Mode: OFF");
        }
        hideModeToggleButton.addActionListener(e -> toggleHideMode());
        hideModeToggleButton.setFont(FontManager.getRunescapeBoldFont());

        hideModeToggleButton.setBackground(Color.DARK_GRAY);
        hideModeToggleButton.setForeground(hideModeToggleButton.isSelected() ? Color.GREEN : Color.RED);

        hideModeToggleButton.addChangeListener(e -> {
            if (hideModeToggleButton.isSelected()) {
                hideModeToggleButton.setForeground(Color.WHITE);
                hideModeToggleButton.setText("Hide Mode: ON");
            } else {
                hideModeToggleButton.setForeground(Color.RED);
                hideModeToggleButton.setText("Hide Mode: OFF");
            }
        });

        panel.add(hideModeToggleButton, new GridBagConstraints());

        return panel;
    }

    public void updatePanelWithDefaults() {
        PropHuntConfig config = plugin.getConfig();
        hideModeToggleButton.setSelected(config.hideMode());
        hideModeToggleButton.setText(config.hideMode() ? "Hide Mode: ON" : "Hide Mode: OFF");
        hideModeToggleButton.setForeground(config.hideMode() ? Color.GREEN : Color.RED);
    }

    private void updateHideModeButtonText(JToggleButton button, boolean isHideModeOn) {
        if (isHideModeOn) {
            button.setText("Hide Mode: ON");
            button.setForeground(Color.GREEN);
        } else {
            button.setText("Hide Mode: OFF");
            button.setForeground(Color.RED);
        }
    }

    // Update the model ID box
    public void setModelIdText(int modelId) {
        SwingUtilities.invokeLater(() -> modelIdTextField.setText(String.valueOf(modelId)));
    }

    // Save on Enter or click away
    private static void onCommit(JTextField field, Runnable action) {
        field.addActionListener(e -> action.run());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                action.run();
            }
        });
    }

    private static Integer parseOrNull(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        plugin.setRandomModelID();
        int newModelID = plugin.getModelId();
        modelIdTextField.setText(String.valueOf(newModelID));
        plugin.setModelID(new PropHuntModelId("", newModelID));
    }

    private void toggleHideMode() {
        boolean currentHideMode = hideModeToggleButton.isSelected();
        plugin.setHideMode(currentHideMode);
    }

    static class NumericDocumentFilter extends javax.swing.text.DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, javax.swing.text.AttributeSet attr)
                throws javax.swing.text.BadLocationException {
            if (string.matches("[0-9]")) {
                super.insertString(fb, offset, string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs)
                throws javax.swing.text.BadLocationException {
            if (text.matches("[0-9]*")) {
                super.replace(fb, offset, length, text, attrs);
            }
        }
    }
}
