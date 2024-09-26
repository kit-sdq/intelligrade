/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.listeners;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;

/**
 * This class handles everything related to the grading config and related UI events.
 * It loads and parses the config and updates the UI.
 */
public class GradingConfigSelectedListener implements DocumentListener {

    private final TextFieldWithBrowseButton gradingConfigInput;

    public GradingConfigSelectedListener(TextFieldWithBrowseButton gradingConfigInput) {
        this.gradingConfigInput = gradingConfigInput;
    }

    @Override
    public void insertUpdate(DocumentEvent documentEvent) {
        String gradingConfigPath = gradingConfigInput.getText();
        // store saved grading config path
        ArtemisSettingsState settings = ArtemisSettingsState.getInstance();
        settings.setSelectedGradingConfigPath(gradingConfigPath);
    }

    @Override
    public void removeUpdate(DocumentEvent documentEvent) {
        ArtemisSettingsState.getInstance().setSelectedGradingConfigPath(null);
    }

    @Override
    public void changedUpdate(DocumentEvent documentEvent) {
        throw new IllegalStateException(
                "Wrong event `GradingConfigSelectedListener::changedUpdate` " + "called. This requires bug fixing!");
    }
}
