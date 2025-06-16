/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;

public final class IntellijUtil {
    public static final String JAVA_SDK_TYPE_NAME = "JavaSDK";
    public static final int TARGET_SDK_VERSION = 21;

    private IntellijUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static Project getActiveProject() {
        return ProjectManager.getInstance().getOpenProjects()[0];
    }

    public static Editor getActiveEditor() {
        return FileEditorManager.getInstance(getActiveProject()).getSelectedTextEditor();
    }

    /**
     * Returns the root directory of the currently active project.
     * This is the directory under which the project configuration files (like .idea) are stored.
     * There are some caveats, see {@link Project#getBasePath()}.
     *
     * @return the path to the root directory of the active project
     */
    public static Path getProjectRootDirectory() {
        return Path.of(getActiveProject().getBasePath());
    }

    public static ProjectLevelVcsManagerImpl getVcsManager() {
        return ProjectLevelVcsManagerImpl.getInstanceImpl(IntellijUtil.getActiveProject());
    }

    private static String loadInspectionsProfile() throws IOException {
        try (var in = IntellijUtil.class.getResourceAsStream("/Project_Default.xml")) {
            if (in == null) {
                throw new IllegalStateException("Default inspections profile not found in resources");
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void setupProjectProfile() {
        var project = getActiveProject();

        Path path = Path.of(
                project.getBasePath(), Project.DIRECTORY_STORE_FOLDER, "inspectionProfiles", "Project_Default.xml");

        try {
            // create the directory if it does not exist
            Files.createDirectories(path.getParent());
            // write the default profile to the file
            Files.writeString(path, loadInspectionsProfile());
        } catch (IOException ioException) {
            throw new IllegalStateException("Could not write default profile", ioException);
        }
    }

    public static Path getAnnotationPath(Annotation annotation) {
        return IntellijUtil.getProjectRootDirectory()
                .resolve(ActiveAssessment.ASSIGNMENT_SUB_PATH)
                .resolve(annotation.getFilePath().replace("\\", "/"));
    }

    public static VirtualFile getAnnotationFile(Annotation annotation) {
        var path = getAnnotationPath(annotation);
        var file = ReadAction.compute(() -> VfsUtil.findFile(path, true));
        if (file == null) {
            throw new IllegalStateException("File not found: " + path);
        }
        return file;
    }

    public static String colorToCSS(JBColor color) {
        return "rgb(%d, %d, %d)".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }
}
