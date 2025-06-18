package edu.kit.kastel.sdq.intelligrade

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.asDisposable
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val LOG = logger<MavenProjectInitializer>()

@Service(Service.Level.PROJECT)
class MavenProjectInitializer(private val project: Project, private val cs: CoroutineScope) {
    private val runningJobs: MutableList<Job> = mutableListOf()
    private val listeners: MutableList<suspend CoroutineScope.() -> Unit> = mutableListOf()
    private var isInitialized = false
    private var isResolved = false
    private var expectsInit = false

    init {
        // This class (including its scope) should live for the entire duration of the project.
        // To prevent duplicate listeners, they are registered here instead of in the start() method.
        //
        // To prevent them from setting the variables to true, before the project initialization was even started,
        // the expectsInit variable is used to indicate whether the project initialization is expected to be started.

        // These two callbacks are called independently of each other, sometimes one is called when the other is not.
        //
        // A successfully loaded project will call both of them, which is why they are listened for here.
        MavenProjectsManager.getInstance(project)
            .addManagerListener(object : MavenProjectsManager.Listener {
                override fun projectImportCompleted() {
                    if (expectsInit) {
                        isInitialized = true
                    }
                }
            }, cs.asDisposable())

        MavenProjectsManager.getInstance(project)
            .addProjectsTreeListener(object: MavenProjectsTree.Listener {
                override fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
                    if (expectsInit) {
                        isResolved = true
                    }
                }
            }, cs.asDisposable())
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): MavenProjectInitializer {
            return project.service<MavenProjectInitializer>()
        }
    }

    /**
     * Registers a listener that will be called once when the project is initialized.
     */
    fun addListener(listener: suspend CoroutineScope.() -> Unit) {
        listeners.add(listener)
    }

    fun isFinished(): Boolean {
        return isInitialized && isResolved
    }

    private fun launchMavenProjectInit(): Job {
        // It is important that intellij loads the maven project files, otherwise no files are visible
        // in the project view.
        //
        // Without this code, it will sometimes detect the maven project files, and sometimes not.
        return cs.launch {
            val root = ProjectUtil.getProjectRootVirtualFile(project)
            if (root == null) {
                LOG.error("Project root virtual file is null, cannot add maven project files")
                return@launch
            }

            // This registers the files with maven, which should load the project correctly.
            addMavenProjectFiles(project, root)
        }
    }

    /**
     * Launches a coroutine that monitors the maven project initialization.
     *
     * This coroutine will ensure that the maven tool window is kept closed throughout the initialization
     * and will notify the listeners once the project is fully initialized.
     */
    private fun launchInitMonitor(): Job {
        // The addMavenProjectFiles function has several problems:
        // - the intellij code opens the maven tool window upon finishing loading
        // -> the user would have to close it manually, which is annoying
        // - the code runs asynchronously in the background -> can not execute code upon finishing the loading
        // - some code relies on the maven project to be fully loaded (like setting the JDK, opening the main file, etc.)
        //
        // The below code makes sure that the maven tool window stays closed and that the listeners are called
        // after the maven project is initialized.
        return cs.launch {
            val isFinishedPartialInitTimeout = 5.seconds
            var instant: TimeMark? = null
            val projectRoot = ProjectUtil.getProjectRootVirtualFile(project) ?: return@launch
            while (!isFinished()) {
                delay(500) // Wait a bit before checking again

                // in some cases the initialization with maven does not work, either isInitialized or isResolved
                // will then be false.
                if (isInitialized && !isResolved || !isInitialized && isResolved) {
                    if (instant == null) {
                        instant = TimeSource.Monotonic.markNow()
                    }

                    // It might just be a timing issue, e.g. the variable will become true after a few more seconds.
                    if (instant.elapsedNow() > isFinishedPartialInitTimeout) {
                        LOG.warn("Maven project initialization is not fully completed after"
                            + " ${isFinishedPartialInitTimeout.inWholeSeconds} seconds."
                            + " isInitialized: $isInitialized, isResolved: $isResolved")
                        instant = null
                        // Try to force a new initialization of the maven project files:
                        addMavenProjectFiles(project, projectRoot)
                    }
                }

                // This fetches the maven tool window (the window on the right side of the IDE with the maven logo)
                val window = withContext(Dispatchers.EDT) {
                    val manager = ToolWindowManager.getInstance(project)
                    manager.getToolWindow("Maven")
                }

                if (window == null) {
                    LOG.error("Maven tool window is missing")
                    addMavenProjectFiles(project, projectRoot)
                    continue
                }

                if (window.isActive) {
                    withContext(Dispatchers.EDT) {
                        window.hide()
                    }
                }
            }

            // A huge problem is that I could not find a good way to wait for the maven project
            // to be fully initialized.
            //
            // The intellij code is complicated, involves many classes and lots of things happening
            // in the background. In addition to that, the code is almost completely without documentation,
            // likely because it is not meant to be used by plugins.
            //
            // Intuitive things like `mavenManager.isMavenizedProject(project)` return true, even though the
            // project is not fully initialized yet.
            //
            // As a stopgap solution, this timer exists, which should hopefully resolve some of the issues.
            delay(2000) // Wait a bit to ensure everything is loaded

            for (listener in listeners) {
                listener()
            }

            // This class only has one instance, but the listeners are added in code that is called multiple times
            // over the lifetime -> would cause duplicate listeners
            //
            // So we clear the listeners after they were called
            listeners.clear()
        }
    }

    fun start() {
        isInitialized = false
        isResolved = false
        expectsInit = true

        // There might still be running jobs from the previous initialization,
        // so we cancel them to prevent multiple initializations.
        for (job in runningJobs) {
            if (job.isActive) {
                job.cancel()
            }
        }

        runningJobs.clear()

        runningJobs.add(launchMavenProjectInit())
        runningJobs.add(launchInitMonitor())
    }

    /**
     * Registers the given project files to be managed by maven.
     *
     * This replicates the behavior of the "Add Maven project files" action, which calls
     * the `AddManagedFilesAction`.
     */
    private suspend fun addMavenProjectFiles(project: Project, projectFile: VirtualFile) {
        // Reset the state of the global variables that indicate whether the project is initialized or resolved.
        isInitialized = false
        isResolved = false

        val manager = MavenProjectsManager.getInstance(project)

        // The projectFile can be either a directory with the pom.xml (or equivalent) or the pom.xml file itself.
        if (projectFile.isFile && (!MavenActionUtil.isMavenProjectFile(projectFile) || manager.isManagedFile(projectFile))) {
            // If the given virtual file is a file that is
            // - not a maven project file (e.g. not a pom.xml)
            // - or is a maven project file but is already managed by maven
            //
            // then it is not a valid selection
            LOG.error("The selected file is not a valid maven project file: ${projectFile.presentableUrl}")
            return
        }

        val selectedFiles = if (projectFile.isDirectory) projectFile.children else arrayOf(projectFile)
        if (!selectedFiles.any { MavenActionUtil.isMavenProjectFile(it) }) {
            ArtemisUtils.displayGenericErrorBalloon(
                "Failed to find Maven project files",
                "No project files found in ${projectFile.presentableUrl}"
            )
            return
        }

        // The given method is marked as internal.
        //
        // This is not ideal, but a lot of other code like
        //
        // manager.addManagedFiles(listOf(pomFile))
        // manager.updateAllMavenProjects(MavenSyncSpec.incremental("MavenProjectInitializer", false))
        //
        // has been tried. Some of them work, but they are a lot less reliable that the one below.
        // There might be a better way to do this, but because this is the method called by the action
        // it seems like the obvious choice.
        val openProjectProvider = MavenOpenProjectProvider()
        openProjectProvider.forceLinkToExistingProjectAsync(projectFile, project)
    }
}
