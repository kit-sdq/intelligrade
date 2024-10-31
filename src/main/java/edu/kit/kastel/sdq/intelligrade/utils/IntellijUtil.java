/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.utils;

import java.nio.file.Path;
import java.util.Objects;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public final class IntellijUtil {
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
}
