package edu.kit.kastel.listeners;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import edu.kit.kastel.highlighter.HighlighterManager;
import org.jetbrains.annotations.NotNull;

public class OnMouseInEditorMoved implements EditorMouseMotionListener {
    @Override
    public void mouseMoved(@NotNull EditorMouseEvent e) {
        HighlighterManager.onMouseMovedInEditor(e);
    }
}
