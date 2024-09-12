/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.utils;

import java.nio.file.Path;
import java.util.Objects;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class EditorUtil {
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
        var rootVirtualFile = Objects.requireNonNull(VfsUtil.findFileByIoFile(getProjectRootDirectory().toFile(), true));
        var session = RefreshQueue.getInstance().createSession(true, true, afterSyncAction);
        session.addFile(rootVirtualFile);
        session.launch();
    }

    public static ProjectLevelVcsManagerImpl getVcsManager() {
        return ProjectLevelVcsManagerImpl.getInstanceImpl(EditorUtil.getActiveProject());
    }

    public static int convertPositionToLine(int position) {
        return getActiveEditor().getDocument().getLineNumber(position);
    }
}
