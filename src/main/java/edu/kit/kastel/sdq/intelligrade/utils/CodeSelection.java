/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.utils;

import java.nio.file.Path;
import java.util.Optional;

public record CodeSelection(int startOffset, int endOffset, Path path) {

    public static Optional<CodeSelection> fromCaret() {
        var editor = EditorUtil.getActiveEditor();
        if (editor == null) {
            // no editor open or no selection made
            return Optional.empty();
        }

        var caret = editor.getCaretModel().getPrimaryCaret();

        int startOffset;
        int endOffset;
        if (caret.hasSelection()) {
            startOffset = caret.getSelectionRange().getStartOffset();
            endOffset = caret.getSelectionRange().getEndOffset();
        } else {
            startOffset = caret.getOffset();
            endOffset = caret.getOffset();
        }

        var path = editor.getVirtualFile().toNioPath();
        return Optional.of(new CodeSelection(startOffset, endOffset, path));
    }
}
