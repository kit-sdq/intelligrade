package edu.kit.kastel.sdq.intelligrade

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.util.lang.JavaVersion
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.function.Function

private val LOG = logger<ProjectUtil>()

object ProjectUtil {
    suspend fun getProjectRootVirtualFile(project: Project): VirtualFile? {
        val rootDirectory = Path.of(project.basePath!!)

        return withContext(Dispatchers.IO) {
            VfsUtil.findFile(rootDirectory, true)
        }
    }

    suspend fun forceFilesSync() = coroutineScope {
        val rootVirtualFile = withContext(Dispatchers.IO) {
            VfsUtil.findFileByIoFile(IntellijUtil.getProjectRootDirectory().toFile(), true)
        }
        if (rootVirtualFile == null) {
            LOG.warn("Root virtual file is null, cannot force files sync")
            return@coroutineScope
        }

        launch {
            withContext(Dispatchers.IO) {
                // This is necessary to ensure that the file system is up-to-date before we start the sync
                VfsUtil.markDirtyAndRefresh(false, true, true, rootVirtualFile)
            }
        }

        launch {
            withContext(Dispatchers.IO) {
                rootVirtualFile.refresh(false, true)
            }
        }
    }


    /**
     * Checks if the active project has a JDK set and if not, it will set a JDK.
     */
    suspend fun updateProjectSDK() {
        // It is amazing how much is possible, the difficult part is finding the right classes
        // that do what you want... this was much more work than it looks like.
        val manager = ProjectRootManager.getInstance(IntellijUtil.getActiveProject())

        // check if SDK is already set
        val projectSdk = manager.projectSdk
        if (projectSdk != null) {
            LOG.debug("SDK already set: " + projectSdk.versionString)
            return
        }

        // if not, set the SDK.
        val jdkTable = ProjectJdkTable.getInstance()

        val javaSdkTypeId = jdkTable.getSdkTypeByName(IntellijUtil.JAVA_SDK_TYPE_NAME)

        val availableSdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdkTypeId)
        if (availableSdks.isEmpty()) {
            LOG.warn("No SDK found, please install a JDK")
            return
        }

        // they are sorted starting from the latest version
        val sortedSdks = availableSdks.stream()
            .filter { sdk: Sdk? -> sdk!!.versionString != null && JavaVersion.tryParse(sdk.versionString) != null }
            .sorted(Comparator.comparing(Function { sdk: Sdk? -> JavaVersion.parse(sdk!!.versionString!!) }))
            .toList()

        LOG.debug("Available SDKs: $sortedSdks")
        for (availableSdk in availableSdks) {
            if (availableSdk.versionString == null) {
                continue
            }

            if (JavaVersion.parse(availableSdk.versionString!!).isAtLeast(IntellijUtil.TARGET_SDK_VERSION)) {
                // update the project sdk, this has to be done in a dedicated thread to prevent crashes

                writeAction { manager.projectSdk = availableSdk }
                return
            }
        }

        LOG.error("No suitable SDK found, please install a JDK with version " + IntellijUtil.TARGET_SDK_VERSION + " or higher")
    }
}
