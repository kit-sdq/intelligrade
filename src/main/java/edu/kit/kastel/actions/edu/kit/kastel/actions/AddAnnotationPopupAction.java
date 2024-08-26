/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.actions.edu.kit.kastel.actions;

import java.util.List;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import edu.kit.kastel.sdq.artemis4j.api.grading.IMistakeType;
import edu.kit.kastel.state.AssessmentModeHandler;
import edu.kit.kastel.utils.AnnotationUtils;
import edu.kit.kastel.wrappers.DisplayableMistakeType;
import org.jetbrains.annotations.NotNull;

public class AddAnnotationPopupAction extends AnAction {

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
                .setEnabledAndVisible(caret != null
                        && AssessmentModeHandler.getInstance()
                                .getCurrentExerciseConfig()
                                .isPresent());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        Caret caret = e.getData(CommonDataKeys.CARET);

        // if no exercise config is loaded, we cannot make annotations
        // if there is no caret we can not sensibly display a popup
        if (caret == null
                || AssessmentModeHandler.getInstance()
                        .getCurrentExerciseConfig()
                        .isEmpty()) {
            return;
        }

        // collect all mistake types that can be annotated
        List<IMistakeType> mistakeTypes =
                AssessmentModeHandler.getInstance().getCurrentExerciseConfig().get().getRatingGroups().stream()
                        .flatMap(ratingGroup -> ratingGroup.getMistakeTypes().stream())
                        .toList();

        // create a popup with all possible mistakes
        JBPopupFactory.getInstance()
                .createPopupChooserBuilder(
                        mistakeTypes.stream().map(DisplayableMistakeType::new).toList())
                .setItemChosenCallback(this::addQuickAnnotation)
                .createPopup()
                .showInBestPositionFor(caret.getEditor());
    }

    private void addQuickAnnotation(@NotNull DisplayableMistakeType mistakeType) {
        AnnotationUtils.addAnnotationByMistakeType(mistakeType.getWrappedValue());
    }
}
