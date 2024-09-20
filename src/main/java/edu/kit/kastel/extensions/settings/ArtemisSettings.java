/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.settings;

import java.util.Objects;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JSeparator;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import edu.kit.kastel.state.PluginState;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;

/**
 * This class implements the settings Dialog for this PlugIn.
 * Everything directly related to the Setting UI should be in here.
 */
public class ArtemisSettings implements Configurable {
    private JBPanel<?> contentPanel;

    private JBTextField artemisURLField;
    private JButton loginButton;

    private JBLabel usernameLabel;
    private JBRadioButton useTokenLoginButton;
    private JBLabel passwordLabel;
    private JBRadioButton usePasswordLoginButton;
    private JBTextField usernameField;
    private JBPasswordField passwordField;

    private JBRadioButton autograderDownloadButton;
    private JBRadioButton autograderPathButton;
    private TextFieldWithBrowseButton autograderPathField;
    private JBRadioButton autograderSkipButton;

    private JBIntSpinner columnsPerRatingGroupSpinner;
    private ColorPanel highlighterColorChooser;

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
        return "IntelliGrade Settings";
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
        contentPanel = new JBPanel<>(new MigLayout("wrap 2", "[] [grow]"));

        contentPanel.add(new JBLabel("Artemis URL:"));
        artemisURLField = new JBTextField();
        contentPanel.add(artemisURLField, "growx");

        loginButton = new JButton("(Re-)Connect");
        loginButton.addActionListener(a -> {
            ArtemisCredentialsProvider.getInstance().setJwt(null);
            ArtemisSettingsState.getInstance().setJwtExpiry(null);
            this.apply();
            PluginState.getInstance().connect();
        });
        contentPanel.add(loginButton, "span 2, growx");

        // Login options
        contentPanel.add(new JSeparator(), "span 2, growx");
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

        // Autograder options
        contentPanel.add(new JSeparator(), "span 2, growx");
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

        // UI options
        contentPanel.add(new JSeparator(), "span 2, growx");
        contentPanel.add(new JBLabel("Columns per rating group:"));
        columnsPerRatingGroupSpinner = new JBIntSpinner(3, 1, 50);
        contentPanel.add(columnsPerRatingGroupSpinner, "growx");

        contentPanel.add(new JBLabel("Highlighter color:"));
        highlighterColorChooser = new ColorPanel();
        highlighterColorChooser.setSelectedColor(new JBColor(0x9b3636, 0x662323));
        contentPanel.add(highlighterColorChooser, "growx");

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
        return modified;
    }

    /**
     * Stores the settings from the Swing form to the configurable component.
     * This method is called on EDT upon user's request.
     *
     * @throws ConfigurationException if values cannot be applied
     */
    @Override
    public void apply() {
        var settings = ArtemisSettingsState.getInstance();
        var credentials = ArtemisCredentialsProvider.getInstance();

        settings.setArtemisInstanceUrl(artemisURLField.getText());

        settings.setUseTokenLogin(useTokenLoginButton.isSelected());
        settings.setUsername(usernameField.getText());
        credentials.setArtemisPassword(new String(passwordField.getPassword()));

        if (autograderDownloadButton.isSelected()) {
            settings.setAutograderOption(AutograderOption.FROM_GITHUB);
        } else if (autograderPathButton.isSelected()) {
            settings.setAutograderOption(AutograderOption.FROM_FILE);
        } else {
            settings.setAutograderOption(AutograderOption.SKIP);
        }
        settings.setAutograderPath(autograderPathField.getText());

        settings.setColumnsPerRatingGroup(
                Integer.parseInt(columnsPerRatingGroupSpinner.getValue().toString()));
        settings.setAnnotationColor(highlighterColorChooser.getSelectedColor());
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
        usernameField.setText(settings.getUsername());
        passwordField.setText(credentials.getArtemisPassword());

        switch (settings.getAutograderOption()) {
            case FROM_GITHUB -> autograderDownloadButton.setSelected(true);
            case FROM_FILE -> autograderPathButton.setSelected(true);
            case SKIP -> autograderSkipButton.setSelected(true);
        }
        autograderPathField.setText(settings.getAutograderPath());

        columnsPerRatingGroupSpinner.setValue(settings.getColumnsPerRatingGroup());
        highlighterColorChooser.setSelectedColor(settings.getAnnotationColor());

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
}
