/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.tool_windows;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import edu.kit.kastel.extensions.guis.AnnotationsListPanel;
import org.jetbrains.annotations.NotNull;

/**
 * This class generates the tool Window for annotations in the bottom.
 */
public class AnnotationsToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        var content = ContentFactory.getInstance().createContent(new AnnotationsListPanel(), null, false);
        toolWindow.getContentManager().addContent(content);
    }
}
