/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.settings;

import java.util.Objects;

import javax.swing.*;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;

/**
 * This class implements the settings Dialog for this PlugIn.
 * Everything directly related to the Setting UI should be in here.
 */
public class ArtemisSettings implements Configurable {
    private JBTextField artemisURLField;

    private JBLabel usernameLabel;
    private JBRadioButton useTokenLoginButton;
    private JBLabel passwordLabel;
    private JBRadioButton usePasswordLoginButton;
    private JBTextField usernameField;
    private JBPasswordField passwordField;
    private JBRadioButton useVcsSSH;
    private JBRadioButton useVcsToken;

    private JBRadioButton autograderDownloadButton;
    private JBRadioButton autograderPathButton;
    private TextFieldWithBrowseButton autograderPathField;
    private JBRadioButton autograderSkipButton;

    private JBCheckBox autoOpenMainClassCheckBox;
    private JBIntSpinner columnsPerRatingGroupSpinner;
    private ColorPanel highlighterColorChooser;
    private ColorPanel activeAssessmentButtonColorChooser;
    private ColorPanel finishedAssessmentButtonColorChooser;
    private ColorPanel reportingAssessmentButtonColorChooser;

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

        var loginButton = new JButton("(Re-)Connect");
        loginButton.addActionListener(a -> {
            ArtemisCredentialsProvider.getInstance().setJwt(null);
            ArtemisSettingsState.getInstance().setJwtExpiry(null);
            this.apply();
            PluginState.getInstance().connect();
        });
        contentPanel.add(loginButton, "span 1, growx");

        // Button to log out of artemis
        var logoutButton = new JButton("Logout");
        logoutButton.addActionListener(a -> {
            // request a logout
            PluginState.getInstance().logout();
            this.apply();
        });
        contentPanel.add(logoutButton, "span 1, growx");

        // Login options
        contentPanel.add(new TitledSeparator("Login Options"), "span 2, growx");
        var loginButtonGroup = new ButtonGroup();

        useTokenLoginButton = new JBRadioButton("Token Login (Preferred)");
        useTokenLoginButton.addActionListener(a -> updateLoginType());
        loginButtonGroup.add(useTokenLoginButton);
        contentPanel.add(useTokenLoginButton, "span 2, growx");

        usePasswordLoginButton = new JBRadioButton("Password Login");
        usePasswordLoginButton.addActionListener(a -> updateLoginType());
        loginButtonGroup.add(usePasswordLoginButton);
        contentPanel.add(usePasswordLoginButton, "span 2, growx");

        usernameLabel = new JBLabel("Username:");
        contentPanel.add(usernameLabel, "pad 0 40 0 0, growx");
        usernameField = new JBTextField();
        contentPanel.add(usernameField, "growx");

        passwordLabel = new JBLabel("Password:");
        contentPanel.add(passwordLabel, "pad 0 40 0 0, growx");
        passwordField = new JBPasswordField();
        contentPanel.add(passwordField, "growx");

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

        contentPanel.add(new JBLabel("Columns per rating group:"));
        columnsPerRatingGroupSpinner = new JBIntSpinner(3, 1, 50);
        contentPanel.add(columnsPerRatingGroupSpinner, "growx");

        contentPanel.add(new JBLabel("Highlighter color:"));
        highlighterColorChooser = new ColorPanel();
        contentPanel.add(highlighterColorChooser, "growx");

        contentPanel.add(new JBLabel("Scoring grading button:"));
        activeAssessmentButtonColorChooser = new ColorPanel();
        contentPanel.add(activeAssessmentButtonColorChooser, "growx");

        contentPanel.add(new JBLabel("Scoring grading button (limit reached):"));
        finishedAssessmentButtonColorChooser = new ColorPanel();
        contentPanel.add(finishedAssessmentButtonColorChooser, "growx");

        contentPanel.add(new JBLabel("Reporting grading button:"));
        reportingAssessmentButtonColorChooser = new ColorPanel();
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
        var credentials = ArtemisCredentialsProvider.getInstance();

        boolean modified = !new String(passwordField.getPassword()).equals(credentials.getArtemisPassword());
        modified |= !usernameField.getText().equals(settings.getUsername());
        modified |= !artemisURLField.getText().equals(settings.getArtemisInstanceUrl());
        modified |= !columnsPerRatingGroupSpinner.getValue().equals(settings.getColumnsPerRatingGroup());
        modified |= !Objects.equals(highlighterColorChooser.getSelectedColor(), settings.getAnnotationColor());
        modified |= useTokenLoginButton.isSelected() != settings.isUseTokenLogin();
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
        var credentials = ArtemisCredentialsProvider.getInstance();

        settings.setArtemisInstanceUrl(artemisURLField.getText());

        settings.setUseTokenLogin(useTokenLoginButton.isSelected());
        settings.setUsername(usernameField.getText());
        credentials.setArtemisPassword(new String(passwordField.getPassword()));

        settings.setVcsAccessOption(getSelectedVcsOption());

        settings.setAutograderOption(getSelectedAutograderOption());
        settings.setAutograderPath(autograderPathField.getText());

        settings.setAutoOpenMainClass(autoOpenMainClassCheckBox.isSelected());
        settings.setColumnsPerRatingGroup(
                Integer.parseInt(columnsPerRatingGroupSpinner.getValue().toString()));
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
        var credentials = ArtemisCredentialsProvider.getInstance();

        artemisURLField.setText(settings.getArtemisInstanceUrl());

        useTokenLoginButton.setSelected(settings.isUseTokenLogin());
        usePasswordLoginButton.setSelected(!settings.isUseTokenLogin());
        usernameField.setText(settings.getUsername());
        passwordField.setText(credentials.getArtemisPassword());

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
        columnsPerRatingGroupSpinner.setValue(settings.getColumnsPerRatingGroup());
        highlighterColorChooser.setSelectedColor(settings.getAnnotationColor());
        activeAssessmentButtonColorChooser.setSelectedColor(settings.getActiveAssessmentButtonColor());
        finishedAssessmentButtonColorChooser.setSelectedColor(settings.getFinishedAssessmentButtonColor());
        reportingAssessmentButtonColorChooser.setSelectedColor(settings.getReportingAssessmentButtonColor());

        updateLoginType();
        updateAutograderOptions();
    }

    private void updateLoginType() {
        var useToken = useTokenLoginButton.isSelected();
        usernameLabel.setEnabled(!useToken);
        passwordLabel.setEnabled(!useToken);
        usernameField.setEnabled(!useToken);
        passwordField.setEnabled(!useToken);
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
