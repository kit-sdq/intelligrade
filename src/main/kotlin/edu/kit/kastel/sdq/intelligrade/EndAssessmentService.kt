package edu.kit.kastel.sdq.intelligrade

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.reportProgress
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException
import edu.kit.kastel.sdq.artemis4j.grading.metajson.AnnotationMappingException
import edu.kit.kastel.sdq.intelligrade.AssessmentTracker.cleanupAssessment
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LOG = logger<EndAssessmentService>()

enum class SubmitAction {
    SUBMIT,
    SAVE,
    CANCEL,
    CLOSE;

    override fun toString(): String {
        return when (this) {
            SUBMIT -> "Submitting"
            SAVE -> "Saving"
            CANCEL -> "Cancelling"
            CLOSE -> "Closing"
        }
    }
}

@Service(Service.Level.PROJECT)
class EndAssessmentService(private val project: Project, private val cs: CoroutineScope) {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): EndAssessmentService {
            return project.service<EndAssessmentService>()
        }
    }

    fun queue(action: SubmitAction) {
        // Launch the coroutine in the given scope with a progress indicator.
        cs.launch {
            withBackgroundProgress(project, "$action assessment") {
                // A size of 100 = 100% progress
                reportProgress(100) { reporter -> run(reporter, action) }
            }
        }
    }

    suspend fun run(reporter: ProgressReporter, action: SubmitAction) {
        try {
            // Update the assessment state in artemis:
            reporter.sizedStep(50, "$action...") {
                when (action) {
                    SubmitAction.SUBMIT -> withContext(Dispatchers.IO) {
                        AssessmentTracker.activeAssessment?.assessment?.submit()
                    }
                    SubmitAction.SAVE -> withContext(Dispatchers.IO) {
                        AssessmentTracker.activeAssessment?.assessment?.save()
                        ArtemisUtils.displayGenericInfoBalloon("Assessment saved", "The assessment has been saved.")
                    }
                    SubmitAction.CANCEL -> withContext(Dispatchers.IO) {
                        AssessmentTracker.activeAssessment?.assessment?.cancel()
                    }
                    SubmitAction.CLOSE -> {
                        // Closing the assessment does not require any action on the server side,
                        // but we still want to clean up the local state.
                        LOG.debug("Closing assessment without submitting or cancelling.")
                    }
                }
            }

            // Cleanup the assessment
            reporter.sizedStep(50, "Cleaning...") {
                cleanupAssessment()
            }
        } catch (e: ArtemisNetworkException) {
            LOG.warn(e)
            ArtemisUtils.displayNetworkErrorBalloon("Could not submit assessment", e)
        } catch (e: AnnotationMappingException) {
            LOG.warn(e)
            ArtemisUtils.displayGenericErrorBalloon(
                "Could not submit assessment",
                "Failed to serialize the assessment. This is a serious bug; please contact the Übungsleitung!"
            )
        }
    }
}
