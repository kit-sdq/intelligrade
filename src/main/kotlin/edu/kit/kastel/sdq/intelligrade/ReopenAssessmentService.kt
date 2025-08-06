package edu.kit.kastel.sdq.intelligrade

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.reportProgress
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException
import edu.kit.kastel.sdq.artemis4j.grading.MoreRecentSubmissionException
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission
import edu.kit.kastel.sdq.artemis4j.grading.metajson.AnnotationMappingException
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LOG = logger<ReopenAssessmentService>()

@Service(Service.Level.PROJECT)
class ReopenAssessmentService(private val project: Project, private val cs: CoroutineScope) {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): ReopenAssessmentService {
            return project.service<ReopenAssessmentService>()
        }
    }

    fun queue(submission: ProgrammingSubmission, gradingConfig: GradingConfig) {
        // Launch the coroutine in the given scope with a progress indicator.
        // modal = progress is in the foreground and not in the right bottom corner
        cs.launch {
            withModalProgress(project, "Reopening assessment") {
                // A size of 100 = 100% progress
                reportProgress(100) { reporter -> run(reporter, submission, gradingConfig) }
            }
        }
    }

    suspend fun run(reporter: ProgressReporter, submission: ProgrammingSubmission, gradingConfig: GradingConfig) {
        // TODO check for review config/assessment
        try {
            val assessment = reporter.sizedStep(20, "Locking...") {
                withContext(Dispatchers.IO) {
                    submission.tryLock(gradingConfig)
                }
            }

            if (assessment.isEmpty) {
                ArtemisUtils.displayGenericErrorBalloon(
                    "Failed to reopen assessment", "Most likely, your lock has been taken by someone else."
                )
                return
            }

            reporter.sizedStep(80, "Cloning...") {
                AssessmentTracker.initializeAssessment(assessment.get())
            }
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
        } catch (e: MoreRecentSubmissionException) {
            LOG.warn(e)
            ArtemisUtils.displayGenericErrorBalloon(
                "Could not reopen assessment", "The student has submitted a newer version of his code."
            )
        }
    }
}
