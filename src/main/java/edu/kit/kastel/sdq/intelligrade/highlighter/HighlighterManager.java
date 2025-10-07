/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.highlighter;

import java.awt.Font;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.Icon;

import com.intellij.DynamicBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.AnnotationsListPanel;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.icons.ArtemisIcons;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import org.jspecify.annotations.NonNull;

/**
 * This class manages the highlights (the colored lines that indicate an annotation) in the editor.
 */
public class HighlighterManager {
    private static final Map<Editor, List<HighlighterWithAnnotations>> highlightersPerEditor = new IdentityHashMap<>();

    // private static int lastPopupLine;
    // private static Editor lastPopupEditor;
    private static JBPopup lastPopup;

    public static void initialize() {
        var messageBus = IntellijUtil.getActiveProject().getMessageBus();
        messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NonNull FileEditorManager source, @NonNull VirtualFile file) {
                var editor = source.getSelectedTextEditor();

                if (PluginState.getInstance().isAssessing() && editor != null) {
                    editor.getDocument().setReadOnly(true);
                    updateHighlightersForEditor(editor);
                }
            }

            @Override
            public void fileClosed(@NonNull FileEditorManager source, @NonNull VirtualFile file) {
                var editor = source.getSelectedTextEditor();
                if (editor == null) {
                    return;
                }

                clearHighlightersForEditor(editor);
            }
        });

        PluginState.getInstance()
                .registerAssessmentStartedListener(assessment -> assessment.registerAnnotationsUpdatedListener(
                        annotations -> updateHighlightersForAllEditors()));

        // When an assessment is closed, clear everything
        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            highlightersPerEditor.clear();
            cancelLastPopup();
        });
    }

    public static void onMouseMovedInEditor(EditorMouseEvent e) {
        // TODO Later implement feature
        // var highlighters = highlightersPerEditor.get(e.getEditor());
        // if (highlighters == null) {
        //     return;
        // }
        //
        // int line = e.getLogicalPosition().getLine();
        //
        // // If the cursor is still in the same line, nothing has to change
        // if (line == lastPopupLine && e.getEditor() == lastPopupEditor) {
        //     return;
        // }
        //
        // var annotations = highlighters.stream().filter(h -> h.annotation().getStartLine() <= line &&
        // h.annotation().getEndLine() >= line)
        //         .map(HighlighterWithAnnotation::annotation)
        //         .toList();
        //
        // if (!annotations.isEmpty()) {
        //     lastPopupLine = line;
        //     lastPopupEditor = e.getEditor();
        //
        //     // First finish the current event, then show the popup
        //     // Otherwise, the event may be cancelled, and e.g. the caret not moved
        //     ApplicationManager.getApplication().invokeLater(() -> {
        //         lastPopup = JBPopupFactory.getInstance()
        //                 .createPopupChooserBuilder(annotations)
        //                 .setRenderer((list, annotation, index, isSelected, cellHasFocus) ->
        //                         new
        // JBLabel(annotation.getMistakeType().getButtonText().translateTo(DynamicBundle.getLocale())))
        //                 .setModalContext(false)
        //                 .setResizable(true)
        //                 .setRequestFocus(false)
        //                 .setCancelOnClickOutside(false)
        //                 .createPopup();
        //
        //         var point = e.getMouseEvent().getPoint();
        //         // point.translate(30, 10);
        //         lastPopup.show(new RelativePoint(e.getMouseEvent().getComponent(), point));
        //     }, x -> lastPopupLine != line || lastPopupEditor != e.getEditor() || lastPopup != null);
        // } else {
        //     cancelLastPopup();
        // }
    }

    /**
     * Creates a highlighter for all the provided annotations that start on that line.
     *
     * @param editor the editor on which the highlighter should be created
     * @param annotations the annotations to be highlighted
     */
    private static void createHighlighter(Editor editor, List<Annotation> annotations) {
        var document = FileDocumentManager.getInstance().getDocument(editor.getVirtualFile());

        if (document == null) {
            return;
        }

        var annotationColor = ArtemisSettingsState.getInstance().getAnnotationColor();

        List<RangeHighlighter> highlighters = new ArrayList<>();
        for (Annotation annotation : annotations) {
            // Lines that have NONE as highlight, should still be highlighted, but invisible to the user.
            // This is necessary for the gutter icon.
            var attributes = new TextAttributes(
                    null, annotationColor.toJBColor(), null, EffectType.BOLD_LINE_UNDERSCORE, Font.PLAIN);

            if (annotation.getMistakeType().getHighlight() == MistakeType.Highlight.NONE) {
                attributes = new TextAttributes();
            }

            var location = annotation.getLocation();

            // resolve the start of the annotation (the start offset of the line + column)
            int startOffset = document.getLineStartOffset(location.start().line())
                    + location.start().column().orElse(0);
            // if the column is present, it has to be added to the start offset of the last line
            // otherwise the end offset is the end of the line
            //
            // The endOffset seems to be exclusive. The getLineEndOffset will return the correct offset,
            // but for our calculated column through the start offset, we have to add 1 to obtain the correct end
            // offset.
            int endOffset = location.end()
                    .column()
                    .map(endColumn -> document.getLineStartOffset(location.end().line()) + endColumn + 1)
                    .orElseGet(() -> document.getLineEndOffset(location.end().line()));

            var range = HighlighterTargetArea.EXACT_RANGE;
            if (startOffset == endOffset || startOffset + 1 == endOffset) {
                // if the start and end offset are the same, we highlight the entire line
                range = HighlighterTargetArea.LINES_IN_RANGE;
            }

            highlighters.add(editor.getMarkupModel()
                    .addRangeHighlighter(startOffset, endOffset, HighlighterLayer.SELECTION - 1, attributes, range));
        }

        // use the first highlighter for the gutter icon
        var highlighter = highlighters.getFirst();

        String gutterTooltip = annotations.stream()
                .map(a -> {
                    String text = "<strong>"
                            + a.getMistakeType().getButtonText().translateTo(DynamicBundle.getLocale()) + "</strong>";
                    if (a.getCustomMessage().isPresent()) {
                        text += " " + a.getCustomMessage().get();
                    }

                    if (a.getCustomScore().isPresent()) {
                        text += " <strong>(" + a.getCustomScore().get() + ")</strong>";
                    }

                    return text;
                })
                .collect(Collectors.joining("<br><br>"));

        var popupActions = getGutterPopupActions(annotations);

        highlighter.setGutterIconRenderer(new GutterIconRenderer() {
            @Override
            public boolean equals(Object o) {
                // TODO implement some actually useful equals method
                return false;
            }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public @NonNull Icon getIcon() {
                return switch (annotations.size()) {
                    case 1 -> ArtemisIcons.AnnotationsGutter1;
                    case 2 -> ArtemisIcons.AnnotationsGutter2;
                    case 3 -> ArtemisIcons.AnnotationsGutter3;
                    default -> ArtemisIcons.AnnotationsGutter4;
                };
            }

            @Override
            public String getTooltipText() {
                return gutterTooltip;
            }

            @Override
            public ActionGroup getPopupMenuActions() {
                return popupActions;
            }

            @Override
            public boolean isDumbAware() {
                return true;
            }
        });

        highlightersPerEditor.computeIfAbsent(editor, e -> new ArrayList<>());
        for (int i = 0; i < highlighters.size(); i++) {
            highlightersPerEditor
                    .get(editor)
                    .add(new HighlighterWithAnnotations(highlighters.get(i), List.of(annotations.get(i))));
        }
    }

    private static void cancelLastPopup() {
        // if (lastPopup != null) {
        //     if (!lastPopup.isDisposed()) {
        //         lastPopup.cancel();
        //     }
        //     lastPopup = null;
        //     lastPopupLine = -1;
        //     lastPopupEditor = null;
        // }
    }

    private static void updateHighlightersForAllEditors() {
        if (!PluginState.getInstance().isAssessing()) {
            return;
        }

        var editors =
                FileEditorManager.getInstance(IntellijUtil.getActiveProject()).getAllEditors();
        for (var editor : editors) {
            updateHighlightersForEditor(((TextEditor) editor).getEditor());
        }

        cancelLastPopup();
    }

    private static void updateHighlightersForEditor(Editor editor) {
        // E.g. decompiled classes are not in the local file system
        // Since they are never part of an assessment, ignore them
        if (!editor.getVirtualFile().isInLocalFileSystem()) {
            return;
        }

        clearHighlightersForEditor(editor);

        var filePath = editor.getVirtualFile().toNioPath();
        var state = PluginState.getInstance();
        var assessment = state.getActiveAssessment().orElseThrow().getAssessment();
        var annotationsByLine = assessment
                .streamAllAnnotations(false)
                .filter(a -> IntellijUtil.getAnnotationPath(a).equals(filePath))
                .collect(Collectors.groupingBy(Annotation::getStartLine));
        for (var annotations : annotationsByLine.values()) {
            createHighlighter(editor, annotations);
        }
    }

    private static void clearHighlightersForEditor(Editor editor) {
        highlightersPerEditor.remove(editor);
        editor.getMarkupModel().removeAllHighlighters();
    }

    private static ActionGroup getGutterPopupActions(List<Annotation> annotations) {
        var group = new DefaultActionGroup();
        for (Annotation annotation : annotations) {
            String text = annotation.getMistakeType().getButtonText().translateTo(DynamicBundle.getLocale());
            if (annotation.getCustomMessage().isPresent()) {
                String displayMsg =
                        shortenAndEscape(annotation.getCustomMessage().get());
                text += ": " + displayMsg;
            }

            group.addAction(new AnActionButton(text) {
                @Override
                public void actionPerformed(@NonNull AnActionEvent anActionEvent) {
                    AnnotationsListPanel.getPanel().selectAnnotation(annotation);
                }

                @Override
                public boolean isDumbAware() {
                    return true;
                }

                @Override
                public @NonNull ActionUpdateThread getActionUpdateThread() {
                    return ActionUpdateThread.EDT;
                }
            });
        }
        return group;
    }

    private static String shortenAndEscape(String text) {
        return StringUtil.escapeMnemonics(StringUtil.shortenTextWithEllipsis(text, 80, 0));
    }

    private record HighlighterWithAnnotations(RangeHighlighter highlighter, List<Annotation> annotation) {}
}
