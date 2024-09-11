/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.state;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.intellij.DynamicBundle;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.ClonedProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.CodeSelection;
import edu.kit.kastel.utils.EditorUtil;
import edu.kit.kastel.wrappers.AnnotationWithTextSelection;

public class ActiveAssessment {
    private final List<Consumer<List<AnnotationWithTextSelection>>> annotationsUpdatedListener = new ArrayList<>();

    private final List<AnnotationWithTextSelection> annotations;
    private final Assessment assessment;
    private final ClonedProgrammingSubmission clonedSubmission;

    public ActiveAssessment(Assessment assessment, ClonedProgrammingSubmission clonedSubmission)
            throws IOException, ArtemisClientException {
        this.assessment = assessment;
        this.clonedSubmission = clonedSubmission;
        this.annotations = new ArrayList<>(assessment.getAnnotations().stream()
                .map(annotation -> new AnnotationWithTextSelection(annotation, createHighlighter(annotation)))
                .toList());
    }

    public void registerAnnotationsUpdatedListener(Consumer<List<AnnotationWithTextSelection>> listener) {
        annotationsUpdatedListener.add(listener);
    }

    public GradingConfig getGradingConfig() {
        return assessment.getConfig();
    }

    public ClonedProgrammingSubmission getClonedSubmission() {
        return clonedSubmission;
    }

    public void addAnnotationAtCaret(MistakeType mistakeType, boolean withCustomMessage) {
        if (assessment == null) {
            throw new IllegalStateException("No active assessment");
        }

        var selection = CodeSelection.fromCaret();
        if (selection.isEmpty()) {
            ArtemisUtils.displayGenericErrorBalloon("No code selected");
            return;
        }
        var selectedText = selection.get().text();

        var editor = EditorUtil.getActiveEditor();
        int startLine = editor.getDocument().getLineNumber(selectedText.getStartOffset());
        int endLine = editor.getDocument().getLineNumber(selectedText.getEndOffset());
        String path = selection.get().projectRelativePath().toString();

        Annotation annotation;
        if (mistakeType.isCustomAnnotation()) {
            double customScore = -1.0;
            String customMessage = "custom message for custom score";
            annotation =
                    assessment.addCustomAnnotation(mistakeType, path, startLine, endLine, customMessage, customScore);
        } else {
            String customMessage = null;
            if (withCustomMessage) {
                customMessage = "custom message";
            }
            annotation = assessment.addPredefinedAnnotation(mistakeType, path, startLine, endLine, customMessage);
        }

        var highlighter = createHighlighter(annotation);

        this.annotations.add(new AnnotationWithTextSelection(annotation, highlighter));
        this.annotationsUpdatedListener.forEach(listener -> listener.accept(this.annotations));
    }

    public void deleteAnnotation(AnnotationWithTextSelection annotation) {
        EditorUtil.getActiveEditor().getMarkupModel().removeHighlighter(annotation.mistakeHighlighter());
        this.annotations.remove(annotation);
        this.assessment.removeAnnotation(annotation.annotation());
        this.annotationsUpdatedListener.forEach(listener -> listener.accept(this.annotations));
    }

    public Assessment getAssessment() {
        return this.assessment;
    }

    private RangeHighlighter createHighlighter(Annotation annotation) {
        var editor = EditorUtil.getActiveEditor();

        int startOffset = editor.getDocument().getLineStartOffset(annotation.getStartLine());
        int endOffset = editor.getDocument().getLineEndOffset(annotation.getEndLine());

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

        return highlighter;
    }
}
