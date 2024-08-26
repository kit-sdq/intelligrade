/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.listeners;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import edu.kit.kastel.state.AssessmentModeHandler;
import edu.kit.kastel.utils.ArtemisUtils;

/**
 * Listener to be called if the Assessment is to be saved.
 */
public class OnSaveAssessmentBtnClick implements ActionListener {

    private static final String NO_ASSESSMENT_OPEN_ERR = "You are currently not grading an assessment.";

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        if (!AssessmentModeHandler.getInstance().isInAssesmentMode()) {
            ArtemisUtils.displayGenericErrorBalloon(NO_ASSESSMENT_OPEN_ERR);
        }
        // TODO: implement
    }
}
