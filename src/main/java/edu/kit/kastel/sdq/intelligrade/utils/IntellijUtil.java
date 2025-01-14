/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.utils;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.ui.JBColor;
import com.intellij.util.lang.JavaVersion;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public final class IntellijUtil {
    private static final Logger LOG = Logger.getInstance(IntellijUtil.class);
    private static final String JAVA_SDK_TYPE_NAME = "JavaSDK";
    private static final int TARGET_SDK_VERSION = 17;

    private IntellijUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static Project getActiveProject() {
        return ProjectManager.getInstance().getOpenProjects()[0];
    }

    public static Editor getActiveEditor() {
        return FileEditorManager.getInstance(getActiveProject()).getSelectedTextEditor();
    }

    public static Path getProjectRootDirectory() {
        return Path.of(getActiveProject().getBasePath());
    }

    public static MavenProjectsManager getMavenManager() {
        return MavenProjectsManager.getInstance(getActiveProject());
    }

    public static void forceFilesSync(Runnable afterSyncAction) {
        var rootVirtualFile = Objects.requireNonNull(
                VfsUtil.findFileByIoFile(getProjectRootDirectory().toFile(), true));
        var session = RefreshQueue.getInstance().createSession(true, true, afterSyncAction);
        session.addFile(rootVirtualFile);
        session.launch();
    }

    public static ProjectLevelVcsManagerImpl getVcsManager() {
        return ProjectLevelVcsManagerImpl.getInstanceImpl(IntellijUtil.getActiveProject());
    }

    /**
     * Checks if the active project has a JDK set and if not, it will set a JDK.
     */
    public static void updateProjectSDK() {
        // It is amazing how much is possible, the difficult part is finding the right classes
        // that do what you want... this was much more work than it looks like.
        var manager = ProjectRootManager.getInstance(getActiveProject());

        // check if SDK is already set
        var projectSdk = manager.getProjectSdk();
        if (projectSdk != null) {
            LOG.debug("SDK already set: " + projectSdk.getVersionString());
            return;
        }

        // if not, set the SDK.

        var jdkTable = ProjectJdkTable.getInstance();

        SdkTypeId javaSdkTypeId = jdkTable.getSdkTypeByName(JAVA_SDK_TYPE_NAME);

        var availableSdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdkTypeId);
        if (availableSdks.isEmpty()) {
            LOG.error("No SDK found, please install a JDK");
            return;
        }

        // they are sorted starting from the latest version
        List<Sdk> sortedSdks = availableSdks.stream()
                .filter(sdk -> sdk.getVersionString() != null && JavaVersion.tryParse(sdk.getVersionString()) != null)
                .sorted(Comparator.comparing((Sdk sdk) -> JavaVersion.parse(sdk.getVersionString())))
                .toList();

        LOG.debug("Available SDKs: " + sortedSdks);
        for (var availableSdk : availableSdks) {
            if (availableSdk.getVersionString() == null) {
                continue;
            }

            if (JavaVersion.parse(availableSdk.getVersionString()).isAtLeast(TARGET_SDK_VERSION)) {
                // update the project sdk, this has to be done in a dedicated thread to prevent crashes
                ApplicationManager.getApplication()
                        .invokeLater(() -> WriteAction.run(() -> manager.setProjectSdk(availableSdk)));
                return;
            }
        }

        LOG.error("No suitable SDK found, please install a JDK with version " + TARGET_SDK_VERSION + " or higher");
    }

    public static Path getAnnotationPath(Annotation annotation) {
        return IntellijUtil.getProjectRootDirectory()
                .resolve(ActiveAssessment.ASSIGNMENT_SUB_PATH)
                .resolve(annotation.getFilePath().replace("\\", "/"));
    }

    public static VirtualFile getAnnotationFile(Annotation annotation) {
        var path = getAnnotationPath(annotation);
        var file = VfsUtil.findFile(path, true);
        if (file == null) {
            throw new IllegalStateException("File not found: " + path);
        }
        return file;
    }

    public static String colorToCSS(JBColor color) {
        return "rgb(%d, %d, %d)".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }
}
