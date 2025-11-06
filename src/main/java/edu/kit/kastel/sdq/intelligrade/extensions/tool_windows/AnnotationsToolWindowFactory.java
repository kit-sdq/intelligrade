/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.tool_windows;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.AnnotationsListPanel;
import org.jspecify.annotations.NonNull;

/**
 * This class generates the tool Window for annotations in the bottom.
 */
public class AnnotationsToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NonNull Project project, @NonNull ToolWindow toolWindow) {
        var content = ContentFactory.getInstance().createContent(new AnnotationsListPanel(), null, false);
        toolWindow.show();
        toolWindow.getContentManager().addContent(content);
    }
}
