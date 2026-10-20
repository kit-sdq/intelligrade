/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.highlighter;

import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.Icon;

import com.intellij.DynamicBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.util.concurrency.AppExecutorUtil;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.sdq.intelligrade.AssessmentTracker;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.icons.ArtemisIcons;
import edu.kit.kastel.sdq.intelligrade.listeners.AssessmentStateListener;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import edu.kit.kastel.sdq.intelligrade.state.AnnotationSelectionService;
import edu.kit.kastel.sdq.intelligrade.state.ProjectState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This class manages the highlights (the colored lines that indicate an annotation) in the editor.
 */
@Service(Service.Level.PROJECT)
public final class HighlighterManager implements Disposable {
    private static final Logger LOG = Logger.getInstance(HighlighterManager.class);

    private final Project project;
    private final Map<Editor, List<HighlighterWithAnnotations>> highlightersPerEditor = new IdentityHashMap<>();
    private final Map<Document, Boolean> originalReadOnlyStateByDocument = new IdentityHashMap<>();
    private boolean initialized;

    // private static int lastPopupLine;
    // private static Editor lastPopupEditor;
    private JBPopup lastPopup;

    public HighlighterManager(Project project) {
        this.project = project;
    }

    public static HighlighterManager initialize(@NonNull Project project) {
        var manager = project.getService(HighlighterManager.class);
        manager.initialize();
        return manager;
    }

    public static void onMouseMovedInEditor(EditorMouseEvent event) {
        var project = event.getEditor().getProject();
        if (project == null) {
            return;
        }

        project.getService(HighlighterManager.class).onMouseMoved();
    }

    private void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        var messageBus = project.getMessageBus();
        messageBus
                .connect(this)
                .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(@NonNull FileEditorManager source, @NonNull VirtualFile file) {
                        var editor = source.getSelectedTextEditor();

                        if (ProjectState.getInstance(project).isAssessing() && editor != null) {
                            makeDocumentReadOnly(editor.getDocument());
                            updateHighlightersForEditor(editor);
                        }
                    }

                    @Override
                    public void fileClosed(@NonNull FileEditorManager source, @NonNull VirtualFile file) {
                        clearHighlightersForFile(file);
                    }
                });

        AssessmentTracker.getInstance(project).subscribe(this, new AssessmentStateListener() {
            @Override
            public void activeAssessmentChanged(@Nullable ActiveAssessment assessment) {
                if (assessment == null) {
                    // When an assessment is closed, clear everything
                    clearAllHighlighters();
                    restoreDocumentReadOnlyStates();
                    cancelLastPopup();
                } else {
                    updateHighlightersForAllEditors();
                }
            }

            @Override
            public void annotationsChanged(
                    @NonNull ActiveAssessment assessment, @NonNull List<Annotation> annotations) {
                updateHighlightersForAllEditors();
            }
        });
    }

    @Override
    public void dispose() {
        clearAllHighlighters();
        restoreDocumentReadOnlyStates();
        cancelLastPopup();
    }

    private void onMouseMoved() {
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
    private void createHighlighter(Editor editor, List<Annotation> annotations) {
        if (annotations.isEmpty()) {
            return;
        }

        var highlighterRequests = ReadAction.computeBlocking(() -> createHighlighterRequests(editor, annotations));
        if (highlighterRequests.isEmpty()) {
            return;
        }

        List<RangeHighlighter> highlighters = new ArrayList<>();
        List<Annotation> highlightedAnnotations = new ArrayList<>();
        for (HighlighterRequest request : highlighterRequests) {
            highlighters.add(editor.getMarkupModel()
                    .addRangeHighlighter(
                            request.offsetRange().startOffset(),
                            request.offsetRange().endOffset(),
                            HighlighterLayer.SELECTION - 1,
                            request.attributes(),
                            request.targetArea()));
            highlightedAnnotations.add(request.annotation());
        }

        // use the first highlighter for the gutter icon
        var highlighter = highlighters.getFirst();

        String gutterTooltip = highlightedAnnotations.stream()
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

        highlighter.setGutterIconRenderer(new AnnotationGutterIconRenderer(
                highlightedAnnotations, gutterTooltip, getGutterPopupActions(highlightedAnnotations, this.project)));

        highlightersPerEditor.computeIfAbsent(editor, e -> new ArrayList<>());
        for (int i = 0; i < highlighters.size(); i++) {
            highlightersPerEditor
                    .get(editor)
                    .add(new HighlighterWithAnnotations(
                            highlighters.get(i),
                            List.of(highlightedAnnotations.get(i)),
                            highlighterRequests.get(i).file()));
        }
    }

    private List<HighlighterRequest> createHighlighterRequests(Editor editor, List<Annotation> annotations) {
        var file = getEditorFile(editor);
        if (file.isEmpty()) {
            return List.of();
        }

        var document = editor.getDocument();
        var annotationColor = ArtemisSettingsState.getInstance().getAnnotationColor();
        List<HighlighterRequest> requests = new ArrayList<>();
        for (Annotation annotation : annotations) {
            var offsetRange = getOffsetRange(document, annotation, file.get());
            if (offsetRange.isEmpty()) {
                continue;
            }

            // Lines that have NONE as highlight, should still be highlighted, but invisible to the user.
            // This is necessary for the gutter icon.
            var attributes = new TextAttributes(
                    null, annotationColor.toJBColor(), null, EffectType.BOLD_LINE_UNDERSCORE, Font.PLAIN);

            if (annotation.getMistakeType().getHighlight() == MistakeType.Highlight.NONE) {
                attributes = new TextAttributes();
            }

            var range = offsetRange.get().isEmptyOrSingleCharacter()
                    ? HighlighterTargetArea.LINES_IN_RANGE
                    : HighlighterTargetArea.EXACT_RANGE;
            requests.add(new HighlighterRequest(annotation, file.get(), offsetRange.get(), attributes, range));
        }

        return requests;
    }

    private void cancelLastPopup() {
        // if (lastPopup != null) {
        //     if (!lastPopup.isDisposed()) {
        //         lastPopup.cancel();
        //     }
        //     lastPopup = null;
        //     lastPopupLine = -1;
        //     lastPopupEditor = null;
        // }
    }

    private void updateHighlightersForAllEditors() {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication()
                    .invokeLater(this::updateHighlightersForAllEditors, ModalityState.defaultModalityState());
            return;
        }

        if (!ProjectState.getInstance(project).isAssessing()) {
            return;
        }

        var editors = FileEditorManager.getInstance(project).getAllEditors();
        for (var editor : editors) {
            if (editor instanceof TextEditor textEditor) {
                makeDocumentReadOnly(textEditor.getEditor().getDocument());
                updateHighlightersForEditor(textEditor.getEditor());
            }
        }

        cancelLastPopup();
    }

    private void updateHighlightersForEditor(Editor editor) {
        var filePath = ReadAction.computeBlocking(() -> getLocalEditorFilePath(editor));
        if (filePath.isEmpty()) {
            return;
        }

        clearHighlightersForEditor(editor);

        var state = ProjectState.getInstance(project);
        ReadAction.nonBlocking(() -> {
                    var assessment = state.getActiveAssessment().orElseThrow().getAssessment();
                    return assessment
                            .streamAllAnnotations(false)
                            .filter(a -> getAnnotationPath(a)
                                    .map(filePath.get()::equals)
                                    .orElse(false))
                            .collect(Collectors.groupingBy(Annotation::getStartLine));
                })
                .expireWhen(() -> editor.isDisposed() || project.isDisposed() || !state.isAssessing())
                .finishOnUiThread(ModalityState.defaultModalityState(), annotationsByLine -> {
                    if (!state.isAssessing() || editor.isDisposed()) {
                        return;
                    }

                    for (var annotations : annotationsByLine.values()) {
                        createHighlighter(editor, annotations);
                    }
                })
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private void clearHighlightersForEditor(Editor editor) {
        var highlighters = highlightersPerEditor.remove(editor);
        if (highlighters == null || editor.isDisposed()) {
            return;
        }

        for (var highlighter : highlighters) {
            editor.getMarkupModel().removeHighlighter(highlighter.highlighter());
        }
    }

    private void clearHighlightersForFile(VirtualFile file) {
        var editors = new ArrayList<>(highlightersPerEditor.keySet());
        for (var editor : editors) {
            var highlighters = highlightersPerEditor.get(editor);
            if (highlighters == null) {
                continue;
            }

            var matchingHighlighters = highlighters.stream()
                    .filter(highlighter -> highlighter.file().equals(file))
                    .toList();
            if (matchingHighlighters.isEmpty()) {
                continue;
            }

            if (!editor.isDisposed()) {
                for (var highlighter : matchingHighlighters) {
                    editor.getMarkupModel().removeHighlighter(highlighter.highlighter());
                }
            }

            highlighters.removeAll(matchingHighlighters);
            if (highlighters.isEmpty()) {
                highlightersPerEditor.remove(editor);
            }
        }
    }

    private void clearAllHighlighters() {
        var editors = new ArrayList<>(highlightersPerEditor.keySet());
        for (var editor : editors) {
            clearHighlightersForEditor(editor);
        }
        highlightersPerEditor.clear();
    }

    private void makeDocumentReadOnly(Document document) {
        if (originalReadOnlyStateByDocument.containsKey(document)) {
            return;
        }

        originalReadOnlyStateByDocument.put(document, !document.isWritable());
        if (document.isWritable()) {
            WriteAction.run(() -> document.setReadOnly(true));
        }
    }

    private void restoreDocumentReadOnlyStates() {
        if (originalReadOnlyStateByDocument.isEmpty()) {
            return;
        }

        WriteAction.run(() -> originalReadOnlyStateByDocument.forEach(Document::setReadOnly));
        originalReadOnlyStateByDocument.clear();
    }

    private static Optional<VirtualFile> getEditorFile(Editor editor) {
        return Optional.ofNullable(FileDocumentManager.getInstance().getFile(editor.getDocument()));
    }

    private static Optional<Path> getLocalEditorFilePath(Editor editor) {
        var file = getEditorFile(editor);
        if (file.isEmpty() || !file.get().isInLocalFileSystem()) {
            // E.g. decompiled classes are not in the local file system.
            // Since they are never part of an assessment, ignore them.
            return Optional.empty();
        }

        return Optional.of(file.get().toNioPath());
    }

    private Optional<OffsetRange> getOffsetRange(Document document, Annotation annotation, VirtualFile file) {
        var location = annotation.getLocation();
        var startLine = location.start().line();
        var endLine = location.end().line();

        if (!isValidLine(document, startLine) || !isValidLine(document, endLine) || startLine > endLine) {
            LOG.warn("Skipping annotation with invalid line range in %s: %s".formatted(file.getPath(), location));
            return Optional.empty();
        }

        var startOffset =
                getLineOffset(document, startLine, location.start().column().orElse(0), false);
        int endOffset = location.end()
                .column()
                .map(endColumn -> getLineOffset(document, endLine, endColumn, true))
                .orElseGet(() -> document.getLineEndOffset(endLine));

        if (endOffset < startOffset) {
            LOG.warn("Skipping annotation with invalid offset range in %s: %s".formatted(file.getPath(), location));
            return Optional.empty();
        }

        return Optional.of(new OffsetRange(startOffset, endOffset));
    }

    private static boolean isValidLine(Document document, int line) {
        return line >= 0 && line < document.getLineCount();
    }

    private static int getLineOffset(Document document, int line, int column, boolean endColumn) {
        var lineStartOffset = document.getLineStartOffset(line);
        var lineLength = document.getLineEndOffset(line) - lineStartOffset;
        var exclusiveColumn = endColumn ? column + 1 : column;

        return lineStartOffset + Math.clamp(exclusiveColumn, 0, lineLength);
    }

    private Optional<Path> getAnnotationPath(Annotation annotation) {
        var basePath = project.getBasePath();
        if (basePath == null) {
            return Optional.empty();
        }

        return Optional.of(Path.of(basePath)
                .resolve(ActiveAssessment.ASSIGNMENT_SUB_PATH)
                .resolve(annotation.getFilePath().replace("\\", "/")));
    }

    private static ActionGroup getGutterPopupActions(List<Annotation> annotations, Project project) {
        var group = new DefaultActionGroup();
        for (Annotation annotation : annotations) {
            String text = annotation.getMistakeType().getButtonText().translateTo(DynamicBundle.getLocale());
            var customMessageOptional = annotation.getCustomMessage();
            if (customMessageOptional.isPresent()) {
                String displayMsg = shortenAndEscape(customMessageOptional.get());
                text += ": " + displayMsg;
            }

            group.addAction(new AnActionButton(text) {
                @Override
                public void actionPerformed(@NonNull AnActionEvent anActionEvent) {
                    AnnotationSelectionService.getInstance(project).selectAnnotation(annotation);
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

    private record OffsetRange(int startOffset, int endOffset) {
        private boolean isEmptyOrSingleCharacter() {
            return startOffset == endOffset || startOffset + 1 == endOffset;
        }
    }

    private record HighlighterRequest(
            Annotation annotation,
            VirtualFile file,
            OffsetRange offsetRange,
            TextAttributes attributes,
            HighlighterTargetArea targetArea) {}

    private static final class AnnotationGutterIconRenderer extends GutterIconRenderer {
        private final int annotationCount;
        private final String tooltipText;
        private final ActionGroup popupActions;
        private final String annotationSignature;

        private AnnotationGutterIconRenderer(
                List<Annotation> annotations, String tooltipText, ActionGroup popupActions) {
            this.annotationCount = annotations.size();
            this.tooltipText = tooltipText;
            this.popupActions = popupActions;
            this.annotationSignature = getAnnotationSignature(annotations);
        }

        private static String getAnnotationSignature(List<Annotation> annotations) {
            return annotations.stream()
                    .map(annotation -> "%s:%s:%s:%s:%s"
                            .formatted(
                                    annotation.getFilePath(),
                                    annotation.getLocation(),
                                    annotation.getMistakeType(),
                                    annotation.getCustomMessage().orElse(""),
                                    annotation
                                            .getCustomScore()
                                            .map(String::valueOf)
                                            .orElse("")))
                    .collect(Collectors.joining("|"));
        }

        @Override
        public @NonNull Icon getIcon() {
            return switch (annotationCount) {
                case 1 -> ArtemisIcons.AnnotationsGutter1;
                case 2 -> ArtemisIcons.AnnotationsGutter2;
                case 3 -> ArtemisIcons.AnnotationsGutter3;
                default -> ArtemisIcons.AnnotationsGutter4;
            };
        }

        @Override
        public String getTooltipText() {
            return tooltipText;
        }

        @Override
        public ActionGroup getPopupMenuActions() {
            return popupActions;
        }

        @Override
        public boolean isDumbAware() {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof AnnotationGutterIconRenderer renderer)) {
                return false;
            }

            return annotationCount == renderer.annotationCount
                    && Objects.equals(annotationSignature, renderer.annotationSignature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(annotationCount, annotationSignature);
        }
    }

    private record HighlighterWithAnnotations(
            RangeHighlighter highlighter, List<Annotation> annotation, VirtualFile file) {}
}
