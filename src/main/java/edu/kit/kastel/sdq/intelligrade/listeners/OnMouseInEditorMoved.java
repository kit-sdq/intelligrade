/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.listeners;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import edu.kit.kastel.sdq.intelligrade.highlighter.HighlighterManager;
import org.jspecify.annotations.NonNull;

public class OnMouseInEditorMoved implements EditorMouseMotionListener {
    @Override
    public void mouseMoved(@NonNull EditorMouseEvent e) {
        HighlighterManager.onMouseMovedInEditor(e);
    }
}
