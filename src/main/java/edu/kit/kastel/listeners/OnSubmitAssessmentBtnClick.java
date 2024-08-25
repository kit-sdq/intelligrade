/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.listeners;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Optional;

import com.intellij.openapi.diagnostic.Logger;
import edu.kit.kastel.extensions.guis.StatisticsContainer;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.grading.artemis.AnnotationMapper;
import edu.kit.kastel.sdq.artemis4j.grading.config.ExerciseConfig;
import edu.kit.kastel.state.AssessmentModeHandler;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.AssessmentUtils;

public class OnSubmitAssessmentBtnClick implements ActionListener {

    private static final String ARTEMIS_ERROR_STRING = "An error occurred submitting the assessment to Artemis.";

    private static final String IO_ERROR_STRING = "Error creating assessment result";

    private static final String ERROR_NOT_ASSESSING =
            "Error obtaining exercise config. Are you currently assessing a submission?";

    private final StatisticsContainer statisticsContainer;

    public OnSubmitAssessmentBtnClick(StatisticsContainer statisticsContainer) {
        this.statisticsContainer = statisticsContainer;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        // submit iff a lock is present
        AssessmentModeHandler.getInstance().getAssessmentLock().ifPresent(lockResult -> {

            // trigger a statistics update
            this.statisticsContainer.triggerUpdate(lockResult.getExercise());

            Optional<ExerciseConfig> config = AssessmentUtils.getConfigAsExerciseCfg();

            // only assess if the exercise config can be obtained
            config.ifPresentOrElse(
                    exerciseConfig -> {
                        try {
                            // create assessment results
                            AnnotationMapper annotationMapper = new AnnotationMapper(
                                    lockResult.getExercise(),
                                    lockResult.getSubmission(),
                                    AssessmentUtils.getAllAnnotations(),
                                    exerciseConfig.getIRatingGroups(),
                                    ArtemisUtils.getArtemisClientInstance()
                                            .getAuthenticationClient()
                                            .getUser(),
                                    lockResult.getSubmissionLock());
                            // save the assessment
                            ArtemisUtils.getArtemisClientInstance()
                                    .getAssessmentArtemisClient()
                                    .saveAssessment(
                                            lockResult.getLockedSubmissionId(),
                                            true,
                                            annotationMapper.createAssessmentResult());
                        } catch (ArtemisClientException ace) {
                            Logger.getInstance(OnSubmitAssessmentBtnClick.class).error(ace);
                            ArtemisUtils.displayGenericErrorBalloon(ARTEMIS_ERROR_STRING);
                        } catch (IOException ioe) {
                            Logger.getInstance(OnSubmitAssessmentBtnClick.class).error(ioe);
                            ArtemisUtils.displayGenericErrorBalloon(IO_ERROR_STRING);
                        }

                        // disable the assessment mode
                        AssessmentModeHandler.getInstance().disableAssessmentMode();
                    },
                    () -> ArtemisUtils.displayGenericErrorBalloon(ERROR_NOT_ASSESSING));
        });
    }
}
