/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.utils;

import java.nio.file.Path;
import java.util.Optional;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.TextRange;
import edu.kit.kastel.sdq.artemis4j.grading.location.LineColumn;

public record CodeSelection(Path path, LineColumn start, LineColumn end) {
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
            // highlight the entire line if no selection is made:
            int offset = ReadAction.compute(caret::getOffset);
            int lineNumber = editor.getDocument().getLineNumber(offset);
            startOffset = editor.getDocument().getLineStartOffset(lineNumber);
            endOffset = editor.getDocument().getLineEndOffset(lineNumber);
        }

        var path = editor.getVirtualFile().toNioPath();

        int startLine = editor.getDocument().getLineNumber(startOffset);
        // The column is the offset in the line (0-based), sometimes only parts of a line are highlighted
        int startColumn = startOffset - editor.getDocument().getLineStartOffset(startLine);

        // the end is not inclusive, therefore 1 is subtracted to get the correct line number
        int endLine = editor.getDocument().getLineNumber(endOffset - 1);
        int endColumn = endOffset - editor.getDocument().getLineStartOffset(endLine);
        // when the start and end offset are the same, the subtraction of 1 would lead to an end offset
        // that is before the start offset
        if (startOffset == endOffset) {
            endLine = startLine;
            endColumn = startColumn;
        }

        return Optional.of(
                new CodeSelection(path, new LineColumn(startLine, startColumn), new LineColumn(endLine, endColumn)));
    }
}
