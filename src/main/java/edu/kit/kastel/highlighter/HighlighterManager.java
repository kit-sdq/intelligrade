package edu.kit.kastel.highlighter;

import com.intellij.DynamicBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.state.PluginState;
import edu.kit.kastel.utils.EditorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class HighlighterManager {
    private static final Map<Editor, List<HighlighterWithAnnotation>> highlightersPerEditor = new IdentityHashMap<>();
    private static final Map<Annotation, List<HighlighterWithEditor>> highlightersPerAnnotation = new HashMap<>();

    private static int lastPopupLine;
    private static Editor lastPopupEditor;
    private static JBPopup lastPopup;

    public static void initialize() {
        var messageBus = EditorUtil.getActiveProject().getMessageBus();
        messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                var editor = source.getSelectedTextEditor();
                if (PluginState.getInstance().isAssessing()) {
                    editor.getDocument().setReadOnly(true);
                    updateHighlightersForEditor(editor);
                }
            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                clearHighlightersForEditor(source.getSelectedTextEditor());
            }
        });

        PluginState.getInstance().registerAssessmentStartedListener(assessment -> {
            assessment.registerAnnotationsUpdatedListener(annotations -> updateHighlightersForAllEditors());
        });

        // When an assessment is closed, clear everything
        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            highlightersPerAnnotation.clear();
            highlightersPerEditor.clear();
            cancelLastPopup();
        });
    }

    public static void onMouseMovedInEditor(EditorMouseEvent e) {
        var highlighters = highlightersPerEditor.get(e.getEditor());
        if (highlighters == null) {
            return;
        }

        int line = e.getLogicalPosition().getLine();

        // If the cursor is still in the same line, nothing has to change
        if (line == lastPopupLine && e.getEditor() == lastPopupEditor) {
            return;
        }

        var annotations = highlighters.stream().filter(h -> h.annotation().getStartLine() <= line && h.annotation().getEndLine() >= line)
                .map(HighlighterWithAnnotation::annotation)
                .toList();

        if (!annotations.isEmpty()) {
            lastPopupLine = line;
            lastPopupEditor = e.getEditor();

            // First finish the current event, then show the popup
            // Otherwise, the event may be cancelled, and e.g. the caret not moved
            ApplicationManager.getApplication().invokeLater(() -> {
                lastPopup = JBPopupFactory.getInstance()
                        .createPopupChooserBuilder(annotations)
                        .setRenderer((list, annotation, index, isSelected, cellHasFocus) ->
                                new JBLabel(annotation.getMistakeType().getButtonText().translateTo(DynamicBundle.getLocale())))
                        .setModalContext(false)
                        .setResizable(true)
                        .setRequestFocus(false)
                        .setCancelOnClickOutside(false)
                        .createPopup();

                var point = e.getMouseEvent().getPoint();
                // point.translate(30, 10);
                lastPopup.show(new RelativePoint(e.getMouseEvent().getComponent(), point));
            }, x -> lastPopupLine != line || lastPopupEditor != e.getEditor() || lastPopup != null);
        } else {
            cancelLastPopup();
        }
    }

    private static void createHighlighter(Editor editor, Annotation annotation) {
        var document = FileDocumentManager.getInstance().getDocument(EditorUtil.getAnnotationFile(annotation));
        int startOffset = document.getLineStartOffset(annotation.getStartLine());
        int endOffset = document.getLineEndOffset(annotation.getEndLine());

        Color annotationColor = ArtemisSettingsState.getInstance().getAnnotationColor();
        var attributes = new TextAttributes(
                null, new JBColor(annotationColor, annotationColor), null, EffectType.BOLD_LINE_UNDERSCORE, Font.PLAIN);

        var highlighter = editor.getMarkupModel()
                .addRangeHighlighter(
                        startOffset,
                        endOffset,
                        HighlighterLayer.SELECTION - 1,
                        attributes,
                        HighlighterTargetArea.LINES_IN_RANGE);
        highlighter.setErrorStripeMarkColor(JBColor.CYAN);
        highlighter.setErrorStripeTooltip(
                annotation.getMistakeType().getButtonText().translateTo(DynamicBundle.getLocale()));
        highlighter.setThinErrorStripeMark(true);
        highlighter.setErrorStripeTooltip(
                annotation.getMistakeType().getButtonText().translateTo(DynamicBundle.getLocale()));

        highlightersPerEditor.computeIfAbsent(editor, e -> new ArrayList<>());
        highlightersPerEditor.get(editor).add(new HighlighterWithAnnotation(highlighter, annotation));

        highlightersPerAnnotation.computeIfAbsent(annotation, a -> new ArrayList<>());
        highlightersPerAnnotation.get(annotation).add(new HighlighterWithEditor(highlighter, editor));
    }

    private static void cancelLastPopup() {
        if (lastPopup != null) {
            if (!lastPopup.isDisposed()) {
                lastPopup.cancel();
            }
            lastPopup = null;
            lastPopupLine = -1;
            lastPopupEditor = null;
        }
    }

    private static void updateHighlightersForAllEditors() {
        if (!PluginState.getInstance().isAssessing()) {
            return;
        }

        for (var editor : FileEditorManager.getInstance(EditorUtil.getActiveProject()).getAllEditors()) {
            updateHighlightersForEditor(((TextEditor) editor).getEditor());
        }

        cancelLastPopup();
    }

    private static void updateHighlightersForEditor(Editor editor) {
        clearHighlightersForEditor(editor);

        var filePath = Path.of(editor.getVirtualFile().getPath());
        var state = PluginState.getInstance();
        for (var annotation : state.getActiveAssessment().get().getAssessment().getAnnotations()) {
            if (EditorUtil.getAnnotationPath(annotation).equals(filePath)) {
                createHighlighter(editor, annotation);
            }
        }
    }

    private static void clearHighlightersForEditor(Editor editor) {
        var highlighters = highlightersPerEditor.remove(editor);
        if (highlighters == null) {
            return;
        }

        for (var highlighter : highlighters) {
            var highlightersForAnnotation = highlightersPerAnnotation.get(highlighter.annotation());
            if (highlightersForAnnotation != null) {
                highlightersForAnnotation.removeIf(h -> h.highlighter().equals(highlighter.highlighter()));
            }
        }

        editor.getMarkupModel().removeAllHighlighters();
    }

    private record HighlighterWithAnnotation(RangeHighlighter highlighter, Annotation annotation) {
    }

    private record HighlighterWithEditor(RangeHighlighter highlighter, Editor editor) {
    }
}
