/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.listeners;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.wm.ToolWindowManager;
import edu.kit.kastel.sdq.intelligrade.highlighter.HighlighterManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OnStartupCompleted implements ProjectActivity, DumbAware {
    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        HighlighterManager.initialize();

        project.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, FileOpener.getInstance());

        // Open the Artemis tool window
        ApplicationManager.getApplication().invokeLater(() -> ToolWindowManager.getInstance(project)
                .getToolWindow("Artemis")
                .show());

        return null;
    }
}
