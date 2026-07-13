/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.extensions.settings;

import java.util.Objects;

import javax.swing.*;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.Nullable;

/**
 * This class implements the settings Dialog for this PlugIn.
 * Everything directly related to the Setting UI should be in here.
 */
public class ArtemisSettings implements Configurable {
    private JBTextField artemisURLField;

    private JBRadioButton useVcsSSH;
    private JBRadioButton useVcsToken;

    private JBRadioButton autograderDownloadButton;
    private JBRadioButton autograderPathButton;
    private TextFieldWithBrowseButton autograderPathField;
    private JBRadioButton autograderSkipButton;

    private JBCheckBox autoOpenMainClassCheckBox;
    private ThemeColorPanel highlighterColorChooser;
    private ThemeColorPanel activeAssessmentButtonColorChooser;
    private ThemeColorPanel finishedAssessmentButtonColorChooser;
    private ThemeColorPanel reportingAssessmentButtonColorChooser;

    /**
     * This class is a color picker that changes the color to select based on the current theme.
     * <p>
     * When the light theme is active, the bright colors can be selected and when the dark theme is active,
     * the dark colors can be selected.
     */
    private static class ThemeColorPanel extends JBPanel<JBPanel<?>> {
        private final ColorPanel brightColorChooser;
        private final ColorPanel darkColorChooser;

        public ThemeColorPanel() {
            super(new MigLayout("wrap 1", "[grow]"));

            this.brightColorChooser = new ColorPanel();
            this.brightColorChooser.setSupportTransparency(true);
            this.darkColorChooser = new ColorPanel();
            this.darkColorChooser.setSupportTransparency(true);

            if (JBColor.isBright()) {
                this.add(this.brightColorChooser, "growx");
            } else {
                this.add(this.darkColorChooser, "growx");
            }
        }

        public @Nullable ThemeColor getSelectedColor() {
            if (brightColorChooser.getSelectedColor() == null || darkColorChooser.getSelectedColor() == null) {
                return null;
            }
            return new ThemeColor(brightColorChooser.getSelectedColor(), darkColorChooser.getSelectedColor());
        }

        public void setSelectedColor(ThemeColor color) {
            brightColorChooser.setSelectedColor(color.getBrightColor());
            darkColorChooser.setSelectedColor(color.getDarkColor());
        }
    }

    /**
     * Returns the visible name of the configurable component.
     * Note, that this method must return the display name
     * that is equal to the display name declared in XML
     * to avoid unexpected errors.
     *
     * @return the visible name of the configurable component
     */
    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "Artemis (IntelliGrade)";
    }

    /**
     * Creates a new Swing form that enables the user to configure the settings.
     * Usually this method is called on the EDT, so it should not take a long time.
     * <p>Also, this place is designed to allocate resources (subscriptions/listeners etc.)</p>
     *
     * @return new Swing form to show, or {@code null} if it cannot be created
     * @see #disposeUIResources
     */
    @Override
    public @Nullable JComponent createComponent() {
        var contentPanel = new JBPanel<>(new MigLayout("wrap 2", "[] [grow]"));

        contentPanel.add(new JBLabel("Artemis URL:"));
        artemisURLField = new JBTextField();
        contentPanel.add(artemisURLField, "growx");

        // VCS Access
        contentPanel.add(new TitledSeparator("VCS Access"), "span 2, grow x");
        ButtonGroup vcsAccessButtonGroup = new ButtonGroup();

        useVcsToken = new JBRadioButton("VCS Token");
        contentPanel.add(useVcsToken);
        vcsAccessButtonGroup.add(useVcsToken);

        useVcsSSH = new JBRadioButton("SSH");
        contentPanel.add(useVcsSSH);
        vcsAccessButtonGroup.add(useVcsSSH);

        // Autograder options
        contentPanel.add(new TitledSeparator("Autograder"), "span 2, growx");
        ButtonGroup autograderButtonGroup = new ButtonGroup();

        autograderDownloadButton = new JBRadioButton("Download latest Autograder release from GitHub");
        autograderDownloadButton.addActionListener(a -> updateAutograderOptions());
        autograderButtonGroup.add(autograderDownloadButton);
        contentPanel.add(autograderDownloadButton, "span 2, growx");

        autograderPathButton = new JBRadioButton("Use local Autograder JAR");
        autograderPathButton.addActionListener(a -> updateAutograderOptions());
        autograderButtonGroup.add(autograderPathButton);
        contentPanel.add(autograderPathButton, "span 2, growx");
        autograderPathField = new TextFieldWithBrowseButton();
        var fileDescriptor =
                new TextBrowseFolderListener(new FileChooserDescriptor(true, false, true, true, false, false)
                        .withFileFilter(file -> "jar".equalsIgnoreCase(file.getExtension())));
        autograderPathField.addBrowseFolderListener(fileDescriptor);
        contentPanel.add(autograderPathField, "pad 0 40 0 0, span 2, growx");

        autograderSkipButton = new JBRadioButton("Skip Autograder");
        autograderSkipButton.addActionListener(a -> updateAutograderOptions());
        autograderButtonGroup.add(autograderSkipButton);
        contentPanel.add(autograderSkipButton, "span 2, growx");

        // UI / General options
        contentPanel.add(new TitledSeparator("General"), "span 2, growx");
        autoOpenMainClassCheckBox = new JBCheckBox("Auto-open main class");
        contentPanel.add(autoOpenMainClassCheckBox, "span 2, growx");

        contentPanel.add(new JBLabel("Highlighter color:"));
        highlighterColorChooser = new ThemeColorPanel();
        contentPanel.add(highlighterColorChooser, "growx");

        contentPanel.add(new JBLabel("Scoring grading button:"));
        activeAssessmentButtonColorChooser = new ThemeColorPanel();
        contentPanel.add(activeAssessmentButtonColorChooser, "growx");

        contentPanel.add(new JBLabel("Scoring grading button (limit reached):"));
        finishedAssessmentButtonColorChooser = new ThemeColorPanel();
        contentPanel.add(finishedAssessmentButtonColorChooser, "growx");

        contentPanel.add(new JBLabel("Reporting grading button:"));
        reportingAssessmentButtonColorChooser = new ThemeColorPanel();
        contentPanel.add(reportingAssessmentButtonColorChooser, "growx");

        return contentPanel;
    }

    /**
     * Indicates whether the Swing form was modified or not.
     * This method is called very often, so it should not take a long time.
     *
     * @return {@code true} if the settings were modified, {@code false} otherwise
     */
    @Override
    public boolean isModified() {
        var settings = ArtemisSettingsState.getInstance();

        boolean modified = !artemisURLField.getText().equals(settings.getArtemisInstanceUrl());
        modified |= !Objects.equals(highlighterColorChooser.getSelectedColor(), settings.getAnnotationColor());
        modified |= getSelectedAutograderOption() != settings.getAutograderOption();
        modified |= autoOpenMainClassCheckBox.isSelected() != settings.isAutoOpenMainClass();
        modified |= getSelectedVcsOption() != settings.getVcsAccessOption();
        modified |= !Objects.equals(
                activeAssessmentButtonColorChooser.getSelectedColor(), settings.getActiveAssessmentButtonColor());
        modified |= !Objects.equals(
                finishedAssessmentButtonColorChooser.getSelectedColor(), settings.getFinishedAssessmentButtonColor());
        modified |= !Objects.equals(
                reportingAssessmentButtonColorChooser.getSelectedColor(), settings.getReportingAssessmentButtonColor());
        return modified;
    }

    /**
     * Stores the settings from the Swing form to the configurable component.
     * This method is called on EDT upon user's request.
     */
    @Override
    public void apply() {
        var settings = ArtemisSettingsState.getInstance();

        settings.setArtemisInstanceUrl(artemisURLField.getText());

        settings.setVcsAccessOption(getSelectedVcsOption());

        settings.setAutograderOption(getSelectedAutograderOption());
        settings.setAutograderPath(autograderPathField.getText());

        settings.setAutoOpenMainClass(autoOpenMainClassCheckBox.isSelected());
        settings.setAnnotationColor(highlighterColorChooser.getSelectedColor());
        settings.setActiveAssessmentButtonColor(activeAssessmentButtonColorChooser.getSelectedColor());
        settings.setFinishedAssessmentButtonColor(finishedAssessmentButtonColorChooser.getSelectedColor());
        settings.setReportingAssessmentButtonColor(reportingAssessmentButtonColorChooser.getSelectedColor());
    }

    /**
     * Loads the settings from the configurable component to the Swing form.
     * This method is called on EDT immediately after the form creation or later upon user's request.
     */
    @Override
    public void reset() {
        var settings = ArtemisSettingsState.getInstance();

        artemisURLField.setText(settings.getArtemisInstanceUrl());

        switch (settings.getVcsAccessOption()) {
            case SSH -> useVcsSSH.setSelected(true);
            case TOKEN -> useVcsToken.setSelected(true);
        }

        switch (settings.getAutograderOption()) {
            case FROM_GITHUB -> autograderDownloadButton.setSelected(true);
            case FROM_FILE -> autograderPathButton.setSelected(true);
            case SKIP -> autograderSkipButton.setSelected(true);
        }
        autograderPathField.setText(settings.getAutograderPath());

        autoOpenMainClassCheckBox.setSelected(settings.isAutoOpenMainClass());
        highlighterColorChooser.setSelectedColor(settings.getAnnotationColor());
        activeAssessmentButtonColorChooser.setSelectedColor(settings.getActiveAssessmentButtonColor());
        finishedAssessmentButtonColorChooser.setSelectedColor(settings.getFinishedAssessmentButtonColor());
        reportingAssessmentButtonColorChooser.setSelectedColor(settings.getReportingAssessmentButtonColor());

        updateAutograderOptions();
    }

    private void updateAutograderOptions() {
        autograderPathField.setEnabled(autograderPathButton.isSelected());
    }

    private AutograderOption getSelectedAutograderOption() {
        if (autograderDownloadButton.isSelected()) {
            return AutograderOption.FROM_GITHUB;
        } else if (autograderPathButton.isSelected()) {
            return AutograderOption.FROM_FILE;
        } else {
            return AutograderOption.SKIP;
        }
    }

    private VCSAccessOption getSelectedVcsOption() {
        if (useVcsSSH.isSelected()) {
            return VCSAccessOption.SSH;
        } else {
            return VCSAccessOption.TOKEN;
        }
    }
}
