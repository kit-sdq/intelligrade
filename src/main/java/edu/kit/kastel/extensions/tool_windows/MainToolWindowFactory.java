/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.tool_windows;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import edu.kit.kastel.extensions.guis.AssessmentPanel;
import edu.kit.kastel.extensions.guis.ExercisePanel;
import edu.kit.kastel.extensions.guis.TestCasePanel;
import org.jetbrains.annotations.NotNull;

/**
 * This class handles all logic for the main grading UI.
 * It does not handle any other logic, that should be factored out.
 */
public class MainToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        toolWindow
                .getContentManager()
                .addContent(ContentFactory.getInstance().createContent(new ExercisePanel(), "Exercise", false));
        toolWindow
                .getContentManager()
                .addContent(ContentFactory.getInstance().createContent(new AssessmentPanel(), "Grading", false));
        toolWindow
                .getContentManager()
                .addContent(ContentFactory.getInstance().createContent(new TestCasePanel(), "Test Results", false));
    }
}
