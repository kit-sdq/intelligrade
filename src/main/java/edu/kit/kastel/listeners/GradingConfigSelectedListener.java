package edu.kit.kastel.listeners;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

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

  }

  @Override
  public void changedUpdate(DocumentEvent documentEvent) {

  }
}
