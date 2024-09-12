/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.actions.edu.kit.kastel.actions;

import java.util.Locale;

import com.intellij.DynamicBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.JBLabel;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.state.PluginState;
import org.jetbrains.annotations.NotNull;

public class AddAnnotationPopupAction extends AnAction {
    private static final Locale LOCALE = DynamicBundle.getLocale();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Caret caret = e.getData(CommonDataKeys.CARET);

        // if no exercise config is loaded, we cannot make annotations
        // if there is no caret we can not sensibly display a popup
        e.getPresentation()
                .setEnabledAndVisible(caret != null && PluginState.getInstance().isAssessing());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Caret caret = e.getData(CommonDataKeys.CARET);

        // if no exercise config is loaded, we cannot make annotations
        // if there is no caret we can not sensibly display a popup
        if (caret == null || !PluginState.getInstance().isAssessing()) {
            return;
        }

        // collect all mistake types that can be annotated
        var mistakeTypes = PluginState.getInstance()
                .getActiveAssessment()
                .orElseThrow()
                .getGradingConfig()
                .getMistakeTypes();

        // create a popup with all possible mistakes
        JBPopupFactory.getInstance()
                .createPopupChooserBuilder(mistakeTypes)
                .setRenderer((list, mistakeType, index, isSelected, cellHasFocus) ->
                        new JBLabel(mistakeType.getButtonText().translateTo(LOCALE)))
                .setItemChosenCallback(this::addQuickAnnotation)
                .createPopup()
                .showInBestPositionFor(caret.getEditor());
    }

    private void addQuickAnnotation(@NotNull MistakeType mistakeType) {
        PluginState.getInstance().getActiveAssessment().orElseThrow().addAnnotationAtCaret(mistakeType, false);
    }
}
