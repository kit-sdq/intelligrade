/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.highlighter;

import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.Icon;

import com.intellij.DynamicBundle;
import com.intellij.icons.AllIcons;
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
import com.intellij.ui.JBColor;
import edu.kit.kastel.extensions.guis.AnnotationsListPanel;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.state.PluginState;
import edu.kit.kastel.utils.EditorUtil;
import org.jetbrains.annotations.NotNull;

public class HighlighterManager {
    private static final Map<Editor, List<HighlighterWithAnnotations>> highlightersPerEditor = new IdentityHashMap<>();

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
                var editor = source.getSelectedTextEditor();
                if (editor == null) {
                    return;
                }

                clearHighlightersForEditor(editor);
            }
        });

        PluginState.getInstance().registerAssessmentStartedListener(assessment -> {
            assessment.registerAnnotationsUpdatedListener(annotations -> updateHighlightersForAllEditors());
        });

        // When an assessment is closed, clear everything
        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            highlightersPerEditor.clear();
            cancelLastPopup();
        });
    }

    public static void onMouseMovedInEditor(EditorMouseEvent e) {
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

    private static void createHighlighter(Editor editor, int startLine, List<Annotation> annotations) {
        var document = FileDocumentManager.getInstance().getDocument(editor.getVirtualFile());

        int startOffset = document.getLineStartOffset(startLine);
        int endOffset = document.getLineEndOffset(
                annotations.stream().mapToInt(Annotation::getEndLine).max().orElse(startLine));

        var annotationColor = ArtemisSettingsState.getInstance().getAnnotationColor();
        var attributes = new TextAttributes(
                null, new JBColor(annotationColor, annotationColor), null, EffectType.BOLD_LINE_UNDERSCORE, Font.PLAIN);

        var highlighter = editor.getMarkupModel()
                .addRangeHighlighter(
                        startOffset,
                        endOffset,
                        HighlighterLayer.SELECTION - 1,
                        attributes,
                        HighlighterTargetArea.LINES_IN_RANGE);

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
            public @NotNull Icon getIcon() {
                return AllIcons.Actions.Edit;
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
        highlightersPerEditor.get(editor).add(new HighlighterWithAnnotations(highlighter, annotations));
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

        for (var editor :
                FileEditorManager.getInstance(EditorUtil.getActiveProject()).getAllEditors()) {
            updateHighlightersForEditor(((TextEditor) editor).getEditor());
        }

        cancelLastPopup();
    }

    private static void updateHighlightersForEditor(Editor editor) {
        clearHighlightersForEditor(editor);

        var filePath = Path.of(editor.getVirtualFile().getPath());
        var state = PluginState.getInstance();
        var assessment = state.getActiveAssessment().orElseThrow().getAssessment();
        var annotationsByLine = assessment.getAnnotations().stream()
                .filter(a -> EditorUtil.getAnnotationPath(a).equals(filePath))
                .collect(Collectors.groupingBy(Annotation::getStartLine));
        for (var entry : annotationsByLine.entrySet()) {
            createHighlighter(editor, entry.getKey(), entry.getValue());
        }
    }

    private static void clearHighlightersForEditor(Editor editor) {
        highlightersPerEditor.remove(editor);
        editor.getMarkupModel().removeAllHighlighters();
    }

    private static ActionGroup getGutterPopupActions(List<Annotation> annotations) {
        var group = new DefaultActionGroup();
        annotations.forEach(annotation -> {
            String text = annotation.getMistakeType().getButtonText().translateTo(DynamicBundle.getLocale());
            if (annotation.getCustomMessage().isPresent()) {
                text += ": "
                        + StringUtil.shortenPathWithEllipsis(
                                annotation.getCustomMessage().get(), 80);
            }

            group.addAction(new AnActionButton(text) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                    AnnotationsListPanel.getPanel().selectAnnotation(annotation);
                }

                @Override
                public boolean isDumbAware() {
                    return true;
                }

                @Override
                public @NotNull ActionUpdateThread getActionUpdateThread() {
                    return ActionUpdateThread.EDT;
                }
            });
        });
        return group;
    }

    private record HighlighterWithAnnotations(RangeHighlighter highlighter, List<Annotation> annotation) {}
}
