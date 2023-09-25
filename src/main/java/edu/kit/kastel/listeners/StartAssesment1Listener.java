package edu.kit.kastel.listeners;

import com.intellij.ide.plugins.PluginManager;
import edu.kit.kastel.extensions.guis.AssessmentViewContent;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.api.artemis.Exercise;
import edu.kit.kastel.sdq.artemis4j.api.artemis.assessment.LockResult;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.wrappers.Displayable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Optional;

/**
 * Listener that gets called when the first grading round is started.
 */
public class StartAssesment1Listener implements ActionListener {

  private static final String NO_CONFIG_SELECTED_MSG =
          "Please select the appropriate grading config";

  private static final String SELECT_EXERCISE_MSG =
          "Please select an exercise to start grading";

  private static final String ERROR_NEXT_ASSESSMENT_FORMATTER =
          "Error requestung a new submission lock: %s";
  private final AssessmentViewContent gui;

  public StartAssesment1Listener(AssessmentViewContent gui) {
    this.gui = gui;
  }

  @Override
  public void actionPerformed(ActionEvent actionEvent) {
    //check if any config is selected. If wrong config is selected Button will be unclickable
    if (gui.getGradingConfigPathInput().getText().isBlank()) {
      ArtemisUtils.displayGenericErrorBalloon(NO_CONFIG_SELECTED_MSG);
      return;
    }

    if (gui.getExercisesDropdown().getSelectedItem() == null) {
      ArtemisUtils.displayGenericErrorBalloon(SELECT_EXERCISE_MSG);
      return;
    }

    Exercise selectedExercise = ((Displayable<Exercise>) gui.getExercisesDropdown().getSelectedItem())
            .getWrappedValue();

    Optional<LockResult> assessmentLockWrapper;
    try {
      assessmentLockWrapper = ArtemisUtils
              .getArtemisClientInstance()
              .getAssessmentArtemisClient()
              .startNextAssessment(selectedExercise, 1);
    } catch (ArtemisClientException e) {
      ArtemisUtils.displayGenericErrorBalloon(
              String.format(ERROR_NEXT_ASSESSMENT_FORMATTER, e.getMessage())
      );
      return;
    }

    assessmentLockWrapper.ifPresent(assessmentLock -> {
      //TODO: clone Submission here
      System.out.println("Locked submission with ID " + assessmentLock.getSubmissionId());
    });

  }
}
