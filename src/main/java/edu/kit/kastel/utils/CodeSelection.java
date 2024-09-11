/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.utils;

import java.nio.file.Path;
import java.util.Optional;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;

public record CodeSelection(TextRange text, PsiElement element) {

    public static Optional<CodeSelection> fromCaret() {
        var editor = EditorUtil.getActiveEditor();
        if (editor == null || !editor.getSelectionModel().hasSelection()) {
            // no editor open or no selection made
            return Optional.empty();
        }

        // get editor Selection
        TextRange selectedText = editor.getCaretModel().getPrimaryCaret().getSelectionRange();

        // only annotate if a selection has been made
        // get the currently selected element and the containing file
        PsiElement selectedElement = PsiDocumentManager.getInstance(EditorUtil.getActiveProject())
                .getPsiFile(editor.getDocument())
                .findElementAt(editor.getCaretModel().getOffset())
                .getContext();

        return Optional.of(new CodeSelection(selectedText, selectedElement));
    }

    public Path path() {
        return Path.of(element.getContainingFile().getVirtualFile().getPath());
    }

    public Path projectRelativePath() {
        return Path.of(element.getProject().getBasePath()).relativize(path());
    }
}
