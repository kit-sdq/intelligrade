/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.listeners;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import edu.kit.kastel.highlighter.HighlighterManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OnStartupCompleted implements ProjectActivity, DumbAware {
    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        HighlighterManager.initialize();
        return null;
    }
}
