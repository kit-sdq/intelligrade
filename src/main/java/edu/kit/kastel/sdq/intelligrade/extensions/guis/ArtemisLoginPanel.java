/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.event.ItemEvent;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisCredentialsProvider;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.state.ArtemisConnectionService;
import edu.kit.kastel.sdq.intelligrade.state.ArtemisConnectionState;
import edu.kit.kastel.sdq.intelligrade.state.ArtemisConnectionState.Failed;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisLoginValidator;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUrlValidator;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This is the panel that will be shown when a login is required.
 */
class ArtemisLoginPanel extends JBPanel<ArtemisLoginPanel> {
    private final JBTextField artemisUrl;
    private final JBRadioButton useTokenLoginButton;
    private final JBRadioButton usePasswordLoginButton;
    private final JBTextField usernameField;
    private final JBPasswordField passwordField;

    private final @Nullable ComponentValidator artemisUrlValidator;
    private final @Nullable ComponentValidator usernameValidator;
    private final @Nullable ComponentValidator passwordValidator;
    private @Nullable Failed connectionFailure;

    ArtemisLoginPanel(@NonNull Disposable parentDisposable, @NonNull Project project) {
        super(new MigLayout("wrap 1", "[grow, fill]"));

        artemisUrl = new JBTextField(ArtemisSettingsState.getInstance().getArtemisInstanceUrl());

        useTokenLoginButton = new JBRadioButton("Token Login (Preferred)", true);
        usePasswordLoginButton = new JBRadioButton("Password Login");

        ButtonGroup loginButtonGroup = new ButtonGroup();
        loginButtonGroup.add(useTokenLoginButton);
        loginButtonGroup.add(usePasswordLoginButton);

        usernameField = new JBTextField(ArtemisSettingsState.getInstance().getUsername());
        passwordField = new JBPasswordField();
        passwordField.setText(ArtemisCredentialsProvider.getInstance().getArtemisPassword());

        JPanel credentialsPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Username:", usernameField)
                .addLabeledComponent("Password:", passwordField)
                .addTooltip("You need a local artemis login for this")
                .getPanel();

        // The username/password fields are only for the password-login, so they are hidden/shown
        // depending on what the user selects as a login type:
        usePasswordLoginButton.addItemListener(event -> {
            credentialsPanel.setVisible(event.getStateChange() == ItemEvent.SELECTED);
            clearConnectionFailure();
            revalidateAllFields();

            credentialsPanel.revalidate();
            credentialsPanel.repaint();
        });

        // Preselect the right option based on the settings
        loginButtonGroup.setSelected(
                ArtemisSettingsState.getInstance().isUseTokenLogin()
                        ? useTokenLoginButton.getModel()
                        : usePasswordLoginButton.getModel(),
                true);
        credentialsPanel.setVisible(usePasswordLoginButton.isSelected());

        artemisUrlValidator =
                installValidator(parentDisposable, artemisUrl, this::validateArtemisUrl, this::onInputChanged);
        usernameValidator =
                installValidator(parentDisposable, usernameField, this::validateUsername, this::onInputChanged);
        passwordValidator =
                installValidator(parentDisposable, passwordField, this::validatePassword, this::onInputChanged);

        var loginButton = new JButton("Login");
        // This makes the button blue instead of the default color, which is gray.
        // Is easier to read and makes the UI look a bit better.
        ClientProperty.put(loginButton, DarculaButtonUI.DEFAULT_STYLE_KEY, true);

        // While the browser window is open, the login widgets should not be touched, therefore
        // they are disabled
        List<JComponent> loginControls = List.of(
                artemisUrl, useTokenLoginButton, usePasswordLoginButton, usernameField, passwordField, loginButton);

        loginButton.addActionListener(_ -> {
            clearConnectionFailure();
            revalidateAllFields();
            if (!isLoginValid()) {
                return;
            }

            setEnabled(loginControls, false);

            ArtemisConnectionService.LoginMethod loginMethod = new ArtemisConnectionService.TokenLogin();
            if (usePasswordLoginButton.isSelected()) {
                loginMethod = new ArtemisConnectionService.PasswordLogin(
                        usernameField.getText(), new String(passwordField.getPassword()));
            }

            ArtemisConnectionService.getInstance(project)
                    .connectInBackground(ArtemisUrlValidator.normalize(artemisUrl.getText()), loginMethod);
        });

        ArtemisConnectionService.getInstance(project).subscribe(parentDisposable, state -> {
            boolean isConnecting = state instanceof ArtemisConnectionState.Connecting;
            setEnabled(loginControls, !isConnecting);

            connectionFailure = state instanceof Failed failed ? failed : null;
            revalidateAllFields();
        });

        this.add(
                FormBuilder.createFormBuilder()
                        .addLabeledComponent("Artemis URL:", artemisUrl)
                        .addComponent(FormBuilder.createFormBuilder()
                                .addComponent(useTokenLoginButton)
                                .addComponent(usePasswordLoginButton)
                                .getPanel())
                        .addComponent(credentialsPanel)
                        .addComponentFillVertically(new JPanel(), 0)
                        .addComponent(loginButton)
                        .getPanel(),
                "grow, shrink");
    }

    private @Nullable ValidationInfo validateArtemisUrl() {
        var result = ArtemisUrlValidator.validate(artemisUrl.getText());
        if (result instanceof ArtemisUrlValidator.Result.Invalid(String message)) {
            return new ValidationInfo(message, artemisUrl);
        }

        return connectionFailureFor(ArtemisConnectionState.FailureTarget.URL, artemisUrl);
    }

    private @Nullable ValidationInfo validateUsername() {
        if (useTokenLoginButton.isSelected()) {
            return null;
        }

        String error = ArtemisLoginValidator.validateUsername(usernameField.getText());
        return error == null ? null : new ValidationInfo(error, usernameField);
    }

    private @Nullable ValidationInfo validatePassword() {
        if (useTokenLoginButton.isSelected()) {
            return null;
        }

        String error = ArtemisLoginValidator.validatePassword(new String(passwordField.getPassword()));
        return error == null
                ? connectionFailureFor(ArtemisConnectionState.FailureTarget.CREDENTIALS, passwordField)
                : new ValidationInfo(error, passwordField);
    }

    private @Nullable ValidationInfo connectionFailureFor(
            ArtemisConnectionState.FailureTarget target, JComponent component) {
        if (connectionFailure != null && connectionFailure.target() == target) {
            return new ValidationInfo(connectionFailure.message(), component);
        }
        return null;
    }

    private boolean isLoginValid() {
        boolean urlValid = isValid(artemisUrlValidator);
        boolean credentialsValid =
                useTokenLoginButton.isSelected() || (isValid(usernameValidator) && isValid(passwordValidator));

        if (!urlValid) {
            artemisUrl.requestFocusInWindow();
        } else if (!credentialsValid) {
            if (!isValid(usernameValidator)) {
                usernameField.requestFocusInWindow();
            } else {
                passwordField.requestFocusInWindow();
            }
        }
        return urlValid && credentialsValid;
    }

    private void clearConnectionFailure() {
        connectionFailure = null;
    }

    private void onInputChanged() {
        clearConnectionFailure();
        revalidateAllFields();
    }

    private void revalidateAllFields() {
        ComponentValidator.getInstance(artemisUrl).ifPresent(ComponentValidator::revalidate);
        ComponentValidator.getInstance(usernameField).ifPresent(ComponentValidator::revalidate);
        ComponentValidator.getInstance(passwordField).ifPresent(ComponentValidator::revalidate);
    }

    private static ComponentValidator installValidator(
            Disposable parentDisposable,
            JTextComponent component,
            Supplier<? extends ValidationInfo> validator,
            Runnable onInputChanged) {
        var componentValidator = new ComponentValidator(parentDisposable)
                .withValidator(validator)
                .andRegisterOnDocumentListener(component)
                .installOn(component);

        DocumentListener documentListener = new DocumentAdapter() {
            @Override
            protected void textChanged(@NonNull DocumentEvent event) {
                onInputChanged.run();
            }
        };
        component.getDocument().addDocumentListener(documentListener);
        Disposer.register(parentDisposable, () -> component.getDocument().removeDocumentListener(documentListener));

        componentValidator.revalidate();
        return componentValidator;
    }

    private static boolean isValid(@Nullable ComponentValidator validator) {
        return validator == null || validator.getValidationInfo() == null;
    }

    private static void setEnabled(Iterable<? extends JComponent> components, boolean enabled) {
        for (JComponent component : components) {
            component.setEnabled(enabled);
        }
    }
}
