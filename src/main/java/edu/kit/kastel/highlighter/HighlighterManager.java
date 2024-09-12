package edu.kit.kastel.highlighter;

import com.intellij.DynamicBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
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
    private static final Map<Editor, List<RangeHighlighter>> highlightersPerEditor = new IdentityHashMap<>();
    private static final Map<Annotation, List<RangeHighlighter>> highlightersPerAnnotation = new HashMap<>();

    public static void initialize() {
        var messageBus = EditorUtil.getActiveProject().getMessageBus();
        messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                FileEditorManagerListener.super.fileOpened(source, file);

                var editor = source.getSelectedTextEditor();
                var filePath = EditorUtil.getProjectRootDirectory().relativize(Path.of(file.getPath())).toString();

                var state = PluginState.getInstance();
                if (state.isAssessing()) {
                    for (var annotation : state.getActiveAssessment().get().getAssessment().getAnnotations()) {
                        if (annotation.getFilePath().equals(filePath)) {
                            createHighlighter(editor, annotation);
                        }
                    }
                }
            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                FileEditorManagerListener.super.fileClosed(source, file);

                var editor = source.getSelectedTextEditor();
                var highlighters = highlightersPerEditor.get(editor);
                if (highlighters != null) {
                    for (var highlighter : highlighters) {
                        for (var entry: highlightersPerAnnotation.entrySet()) {
                            entry.getValue().remove(highlighter);
                        }
                    }
                }
            }
        });
    }

    public static void createHighlighter(Annotation annotation) {
        var file = getAnnotationFile(annotation);

        // The file may be open in multiple editors
        FileEditorManager.getInstance(EditorUtil.getActiveProject())
                .getAllEditorList(file)
                .forEach(editor -> createHighlighter(((TextEditor) editor).getEditor(), annotation));
    }

    public static void deleteHighlighter(Annotation annotation) {
        var highlighters = highlightersPerAnnotation.get(annotation);
        if (highlighters != null) {
            for (var highlighter : highlighters) {
                for (var entry : highlightersPerEditor.entrySet()) {
                    if (entry.getValue().contains(highlighter)) {
                        entry.getValue().remove(highlighter);
                        entry.getKey().getMarkupModel().removeHighlighter(highlighter);
                    }
                }
            }
        }
    }

    private static void createHighlighter(Editor editor, Annotation annotation) {
        var document = FileDocumentManager.getInstance().getDocument(getAnnotationFile(annotation));
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
        highlightersPerEditor.get(editor).add(highlighter);

        highlightersPerAnnotation.computeIfAbsent(annotation, a -> new ArrayList<>());
        highlightersPerAnnotation.get(annotation).add(highlighter);
    }

    private static VirtualFile getAnnotationFile(Annotation annotation) {
        var path = EditorUtil.getProjectRootDirectory().resolve(annotation.getFilePath());
        var file = VfsUtil.findFile(path, true);
        if (file == null) {
            throw new IllegalStateException("File not found: " + path);
        }
        return file;
    }
}
