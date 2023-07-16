package edu.kit.kastel.listeners;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import edu.kit.kastel.exceptions.ImplementationMissing;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

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
    //store saved grading config path
    ArtemisSettingsState settings = ArtemisSettingsState.getInstance();
    settings.setSelectedGradingConfigPath(gradingConfigInput.getText());
  }

  @Override
  public void removeUpdate(DocumentEvent documentEvent) {
    throw new ImplementationMissing(
            "Wrong event `GradingConfigSelectedListener::removeUpdate` "
                    + "called. This requires bug fixing!"
    );
  }

  @Override
  public void changedUpdate(DocumentEvent documentEvent) {
    throw new ImplementationMissing(
            "Wrong event `GradingConfigSelectedListener::changedUpdate` "
                    + "called. This requires bug fixing!"
    );
  }
}
