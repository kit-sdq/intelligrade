/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.utils;

import java.nio.file.Path;
import java.util.Optional;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.TextRange;

public record CodeSelection(Path path, int startLine, int endLine) {
    public static Optional<CodeSelection> fromCaret() {
        var editor = IntellijUtil.getActiveEditor();
        if (editor == null) {
            // no editor open or no selection made
            return Optional.empty();
        }

        var caret = editor.getCaretModel().getPrimaryCaret();

        int startOffset;
        int endOffset;
        if (caret.hasSelection()) {
            TextRange textRange = ReadAction.compute(caret::getSelectionRange);

            startOffset = textRange.getStartOffset();
            endOffset = textRange.getEndOffset();
        } else {
            int offset = ReadAction.compute(caret::getOffset);
            startOffset = offset;
            endOffset = offset + 1;
        }

        var path = editor.getVirtualFile().toNioPath();

        int startLine = editor.getDocument().getLineNumber(startOffset);
        // the end is not inclusive, therefore 1 is subtracted to get the correct line number
        int endLine = editor.getDocument().getLineNumber(endOffset - 1);

        return Optional.of(new CodeSelection(path, startLine, endLine));
    }
}
