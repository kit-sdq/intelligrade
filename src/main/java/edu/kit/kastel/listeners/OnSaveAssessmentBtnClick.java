package edu.kit.kastel.listeners;

import edu.kit.kastel.sdq.artemis4j.api.artemis.assessment.AssessmentResult;
import edu.kit.kastel.state.AssessmentModeHandler;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.AssessmentUtils;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Listener to be called if the Assessment is to be saved.
 */
public class OnSaveAssessmentBtnClick implements ActionListener {

  private static final String NO_ASSESSMENT_OPEN_ERR =
          "You are currently not grading an assessment.";

  @Override
  public void actionPerformed(ActionEvent actionEvent) {
    if (!AssessmentModeHandler.getInstance().isInAssesmentMode()) {
      ArtemisUtils.displayGenericErrorBalloon(NO_ASSESSMENT_OPEN_ERR);
      return;
    }

//    AssessmentUtils.getAssessmentLock().ifPresent(
//            lockResult -> {
//              //save assessment
//              ArtemisUtils.getArtemisClientInstance()
//                      .getAssessmentArtemisClient()
//                      .saveAssessment(
//                              AssessmentUtils.getParticipationId(),
//                              false,
//                              AssessmentUtils.get
//                      );
//            }
//    );
  }
}
