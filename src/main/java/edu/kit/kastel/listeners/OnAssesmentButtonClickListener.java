package edu.kit.kastel.listeners;

import edu.kit.kastel.sdq.artemis4j.grading.model.MistakeType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * This class represents a generic listener that is called if an assessment button
 * is clicked. It should create and save the new annotation.
 */
public class OnAssesmentButtonClickListener implements ActionListener {

  private final MistakeType mistakeType;

  public OnAssesmentButtonClickListener(MistakeType mistakeType) {
    this.mistakeType = mistakeType;
  }

  @Override
  public void actionPerformed(ActionEvent actionEvent) {
    //TODO: implement an action listener here
    System.out.println(
            "Button for mistake type ``" + mistakeType.getButtonText("en") + "`` has been clicked."
    );
  }
}
