package edu.kit.kastel.sdq.intelligrade

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException
import edu.kit.kastel.sdq.artemis4j.client.AnnotationSource
import edu.kit.kastel.sdq.artemis4j.grading.Assessment
import edu.kit.kastel.sdq.artemis4j.grading.Annotation
import edu.kit.kastel.sdq.artemis4j.grading.ClonedProgrammingSubmission
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState
import edu.kit.kastel.sdq.intelligrade.extensions.settings.VCSAccessOption
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.reflect.MethodUtils
import org.eclipse.jgit.lib.RepositoryCache
import org.eclipse.jgit.storage.file.WindowCacheConfig
import java.io.IOException
import java.lang.Exception
import java.nio.file.Path

fun interface AssessmentListener {
    fun update(value: ActiveAssessment?)
}

private val LOG = logger<AssessmentTracker>()

/**
 * This object (singleton) keeps track of the current assessment.
 */
object AssessmentTracker {
    var activeAssessment: ActiveAssessment? = null
    private val listeners: MutableList<AssessmentListener> = mutableListOf()

    /**
     * Adds a listener that will be notified when the active assessment changes.
     *
     * This can be when a new assessment is started or when the current assessment is cleared.
     *
     * Regarding Threading Model: There is no guarantee that the listener will be called on EDT,
     * make sure to account for that.
     */
    fun addListener(listener: AssessmentListener) {
        listeners.add(listener)
    }

    private fun updateAssessment(assessment: ActiveAssessment?) {
        this.activeAssessment = assessment
        for (listener in listeners) {
            listener.update(activeAssessment)
        }
    }

    fun clearAssessment() {
        updateAssessment(null)
    }

    suspend fun cleanupAssessment() {
        // Do not close the ClonedProgrammingSubmission, since this would try to delete the workspace file
        // Instead, we delete the project directory manually
        this.cleanupProjectDirectory()

        // Clear active assessment and notify listeners
        clearAssessment()
    }

    @Throws(ArtemisClientException::class)
    private suspend fun cloneSubmission(workspacePath: Path?, assessment: Assessment): ClonedProgrammingSubmission? {
        // Clone the new submission
        val submission = when (ArtemisSettingsState.getInstance().vcsAccessOption) {
            VCSAccessOption.SSH -> withContext(Dispatchers.IO) {
                ArtemisUtils.cloneViaSSH(assessment, workspacePath)
            }
            VCSAccessOption.TOKEN -> withContext(Dispatchers.IO) {
                assessment
                    .submission
                    .cloneViaVCSTokenInto(workspacePath, null)
            }
        }

        // Force a file sync to ensure the VFS knows about the new files:
        ProjectUtil.forceFilesSync()

        return submission
    }

    suspend fun initializeAssessment(assessment: Assessment): ActiveAssessment? {
        try {
            // Cleanup first, in case there are files left from a previous assessment
            cleanupProjectDirectory()

            val baseDirectory = IntellijUtil.getProjectRootDirectory()
            val clonedSubmission: ClonedProgrammingSubmission? = cloneSubmission(baseDirectory, assessment)

            withContext(Dispatchers.IO) {
                IntellijUtil.setupProjectProfile()
                // Force a file sync to ensure the VFS knows about the new files:
                ProjectUtil.forceFilesSync()
            }

            val mavenInitializer = MavenProjectInitializer.getInstance(IntellijUtil.getActiveProject())
            mavenInitializer.addListener {
                // Sometimes the SDK is not set properly, this will set the SDK if it is not set
                ProjectUtil.updateProjectSDK()
            }

            mavenInitializer.start()

            updateAssessment(ActiveAssessment(assessment, clonedSubmission))

            return activeAssessment
        } catch (e: ArtemisClientException) {
            LOG.warn(e)
            ArtemisUtils.displayGenericErrorBalloon("Error cloning submission", e.message)

            // Cancel the assessment to prevent spurious locks
            // but only if the assessment does not have any user-made annotations yet (non autograder annotations):
            val hasUserAnnotations = assessment.getAnnotations(true).stream()
                .anyMatch { annotation: Annotation? ->
                    assessment.correctionRound == CorrectionRound.FIRST
                            && annotation!!.source == AnnotationSource.MANUAL_FIRST_ROUND
                            || assessment.correctionRound == CorrectionRound.SECOND
                            && annotation!!.source == AnnotationSource.MANUAL_SECOND_ROUND
                }

            try {
                if (!hasUserAnnotations) {
                    assessment.cancel()
                }
            } catch (ex: ArtemisNetworkException) {
                LOG.warn(ex)
                ArtemisUtils.displayGenericErrorBalloon("Failed to free the assessment lock", ex.message)
            }

            return null
        }
    }

    private suspend fun unregisterGitRepository(project: Project) {
        // There is a lot of git stuff going on in the background, some of which will complain
        // when the .git directory is deleted.

        // Indicate to the VCS that we are about to delete the project directory
        IntellijUtil.getVcsManager().directoryMappings = mutableListOf<VcsDirectoryMapping?>()
        IntellijUtil.getVcsManager().fireDirectoryMappingsChanged()

        val repositoryManager = VcsRepositoryManager.getInstance(project)
        if (!repositoryManager.getRepositories().isEmpty()) {
            // When deleting the .git folder, the git4idea plugin will throw an exception when the .git/HEAD file
            // is deleted.
            //
            // It seems to be watching the .git directory in the background.

            // The VcsRepositoryManager seems to keep track of the repositories. We need it to dispose the class
            // that keeps track of the deleted repository.
            //
            // There isn't much code in the class, and even less that changes the list of repositories.
            // The below method will make the manager check which repositories are no longer mapped
            // (mappings were removed in the above calls)
            //
            // Sadly this method is not public, so reflection comes to the rescue.
            LOG.debug("Repositories before update: ${repositoryManager.getRepositories()}")
            try {
                MethodUtils.invokeMethod(repositoryManager, true, "checkAndUpdateRepositoryCollection", null)
            } catch (e: Exception) {
                // If anything crashes here, it is not a big deal, because the code still works.
                LOG.warn(e)
            }
            LOG.debug("Repositories after update: ${repositoryManager.getRepositories()}")
        }

        // This is a workaround for an issue with the jgit library that is used by artemis4j:
        withContext(Dispatchers.IO) {
            RepositoryCache.clear()
            WindowCacheConfig().install()
        }

    }

    suspend fun cleanupProjectDirectory() {
        // Close all open editors
        val project = IntellijUtil.getActiveProject()
        val editorManager = FileEditorManager.getInstance(project)
        for (editor in editorManager.allEditors) {
            withContext(Dispatchers.EDT) {
                editorManager.closeFile(editor.file)
            }
        }

        val rootFile = ProjectUtil.getProjectRootVirtualFile(project)
        if (rootFile == null) {
            LOG.error("Project root virtual file is null, cannot clean up project directory")
            return
        }

        this.unregisterGitRepository(project)

        // Delete all directory contents, but not the directory itself
        val deletionQueue = mutableListOf<VirtualFile>()
        withContext(Dispatchers.IO) {
            VfsUtil.visitChildrenRecursively(rootFile, object : VirtualFileVisitor<Unit?>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory) {
                        deletionQueue.add(file)
                        return false
                    }

                    return true
                }

                override fun afterChildrenVisited(file: VirtualFile) {
                    // delete empty directories
                    if (file.isDirectory && deletionQueue.containsAll(file.children.asList()) && file != rootFile) {
                        deletionQueue.add(file)
                    }
                }
            })
        }

        for (file in deletionQueue) {
            delete(file)
        }

        // ensure that the VFS knows about the deleted files:
        ProjectUtil.forceFilesSync()
    }

    private suspend fun delete(file: VirtualFile) {
        withContext(Dispatchers.IO) {
            try {
                writeAction { file.delete(AssessmentTracker::class.java) }
                LOG.debug("Deleted: " + file.presentableUrl)
            } catch (exception: IOException) {
                LOG.warn("Failed to delete: " + file.presentableUrl, exception)
            }
        }
    }
}
