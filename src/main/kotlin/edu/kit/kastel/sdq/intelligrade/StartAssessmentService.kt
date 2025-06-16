package edu.kit.kastel.sdq.intelligrade

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.reportProgress
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise
import edu.kit.kastel.sdq.artemis4j.grading.metajson.AnnotationMappingException
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig
import edu.kit.kastel.sdq.intelligrade.extensions.guis.SplashDialog
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val LOG = logger<StartAssessmentService>()

@Service(Service.Level.PROJECT)
class StartAssessmentService(private val project: Project, private val cs: CoroutineScope) {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): StartAssessmentService {
            return project.service<StartAssessmentService>()
        }
    }

    fun queue(correctionRound: Int, gradingConfig: GradingConfig, activeExercise: ProgrammingExercise) {
        // Launch the coroutine in the given scope with a progress indicator.
        // modal = progress is in the foreground and not in the right bottom corner
        cs.launch {
            withModalProgress(project, "Starting assessment") {
                // A size of 100 = 100% progress
                reportProgress(100) { reporter -> run(reporter, correctionRound, gradingConfig, activeExercise) }
            }
        }
    }

    suspend fun run(reporter: ProgressReporter, correctionRound: Int, gradingConfig: GradingConfig, activeExercise: ProgrammingExercise) {
        try {
            val nextAssessment = reporter.sizedStep(20, "Locking...") {
                activeExercise.tryLockNextSubmission(correctionRound, gradingConfig)
            }

            if (nextAssessment.isEmpty) {
                ArtemisUtils.displayGenericInfoBalloon(
                    "Could not start assessment",
                    "There are no more submissions to assess. Thanks for your work :)"
                )

                return
            }

            val activeAssessment = reporter.sizedStep(80, "Cloning...") {
                AssessmentTracker.initializeAssessment(nextAssessment.get())
            }

            if (activeAssessment == null) {
                return
            }

            SplashDialog.showMaybe()

            // Now everything is done - the submission is properly locked, and the repository is cloned
            if (activeAssessment.assessment.annotations.isEmpty()) {
                activeAssessment.runAutograder()
            } else {
                ArtemisUtils.displayGenericInfoBalloon(
                    "Skipping Autograder",
                    "The submission already has annotations. Skipping the Autograder."
                )
            }

            ArtemisUtils.displayGenericInfoBalloon(
                "Assessment started",
                "You can now grade the submission. Please make sure you are familiar with all "
                        + "grading guidelines."
            )
        } catch (e: ArtemisNetworkException) {
            LOG.warn(e)
            ArtemisUtils.displayNetworkErrorBalloon("Could not lock assessment", e)
        } catch (e: AnnotationMappingException) {
            LOG.warn(e)
            ArtemisUtils.displayGenericErrorBalloon(
                "Could not parse assessment",
                "Could not parse previous assessment. This is a serious bug; please contact the "
                        + "Übungsleitung!"
            )
        }
    }
}
