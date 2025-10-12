/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.state;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.ClonedProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound;
import edu.kit.kastel.sdq.artemis4j.grading.location.LineColumn;
import edu.kit.kastel.sdq.artemis4j.grading.location.Location;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.sdq.intelligrade.autograder.AutograderTask;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.AutograderOption;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;

public class ActiveAssessment {
    private static final Logger LOG = Logger.getInstance(ActiveAssessment.class);

    public static final Path ASSIGNMENT_SUB_PATH = Path.of("assignment");

    private final List<Consumer<List<Annotation>>> annotationsUpdatedListener = new ArrayList<>();

    private final Assessment assessment;
    private final ClonedProgrammingSubmission clonedSubmission;

    public ActiveAssessment(Assessment assessment, ClonedProgrammingSubmission clonedSubmission) {
        this.assessment = assessment;
        this.clonedSubmission = clonedSubmission;
    }

    public void registerAnnotationsUpdatedListener(Consumer<List<Annotation>> listener) {
        annotationsUpdatedListener.add(listener);
        listener.accept(assessment.getAnnotations(true));
    }

    public GradingConfig getGradingConfig() {
        return assessment.getConfig();
    }

    public boolean isReview() {
        return assessment.getCorrectionRound() == CorrectionRound.REVIEW;
    }

    private static LineColumn translateToLineColumn(Document document, int offset) {
        // The line number in the document is 0-based, and LineColumn expects 0-based as well.
        int line = document.getLineNumber(offset);
        // The column is the offset in the line (0-based), it should satisfy:
        // lineStartOffset + column = offset
        // <-> column = offset - lineStartOffset
        int column = offset - document.getLineStartOffset(line);

        return new LineColumn(line, column);
    }

    private static Location createLocationFromSelection(Editor editor, String path) {
        var caret = editor.getCaretModel().getPrimaryCaret();

        if (!caret.hasSelection()) {
            // highlight the entire line if no selection was made:
            int offset = ReadAction.compute(caret::getOffset);
            int lineNumber = editor.getDocument().getLineNumber(offset);
            return new Location(path, lineNumber, lineNumber);
        }

        TextRange textRange = ReadAction.compute(caret::getSelectionRange);

        int startOffset = textRange.getStartOffset();
        int endOffset = textRange.getEndOffset();

        if (startOffset == endOffset) {
            // no selection made -> highlight the entire line
            int lineNumber = editor.getDocument().getLineNumber(startOffset);
            return new Location(path, lineNumber, lineNumber);
        }

        var start = translateToLineColumn(editor.getDocument(), startOffset);
        // The end offset provided by the text range is exclusive (last character is not included),
        // therefore 1 is subtracted to get the correct end position:
        var end = translateToLineColumn(editor.getDocument(), endOffset - 1);

        return new Location(path, start, end);
    }

    public void addAnnotationAtCaret(MistakeType mistakeType, boolean withCustomMessage) {
        if (assessment == null) {
            throw new IllegalStateException("No active assessment");
        }

        var editor = IntellijUtil.getActiveEditor();
        if (editor == null) {
            // no editor open or no selection made
            ArtemisUtils.displayGenericErrorBalloon(
                    "No code selected", "Cannot create annotation without code selection");
            return;
        }

        var path = Path.of(IntellijUtil.getActiveProject().getBasePath())
                .resolve(ASSIGNMENT_SUB_PATH)
                .relativize(editor.getVirtualFile().toNioPath())
                .toString()
                .replace("\\", "/");
        var location = createLocationFromSelection(editor, path);

        if (mistakeType.isCustomAnnotation()) {
            addCustomAnnotation(mistakeType, location);
        } else if (withCustomMessage) {
            addPredefinedAnnotationWithCustomMessage(mistakeType, location);
        } else {
            assessment.addPredefinedAnnotation(mistakeType, location, null);
            this.notifyListeners();
        }
    }

    public void deleteAnnotation(Annotation annotation) {
        if (this.isReview()) {
            this.assessment.suppressAnnotation(annotation);
        } else {
            this.assessment.removeAnnotation(annotation);
        }
        this.notifyListeners();
    }

    public void restoreAnnotation(Annotation annotation) {
        if (this.isReview()) {
            this.assessment.unsuppressAnnotation(annotation);
        } else {
            ArtemisUtils.displayGenericWarningBalloon(
                    "Cannot restore annotation", "You can only restore annotations in review mode.");
            LOG.warn("Cannot restore annotation outside of review");
        }
        this.notifyListeners();
    }

    public void runAutograder() {
        if (this.isReview()) {
            return;
        }

        var settings = ArtemisSettingsState.getInstance();
        if (settings.getAutograderOption() == AutograderOption.SKIP) {
            return;
        }

        AutograderTask.execute(assessment, clonedSubmission, this::notifyListeners);
    }

    public Assessment getAssessment() {
        return this.assessment;
    }

    public void changeCustomMessage(Annotation annotation) {
        if (this.isReview()) {
            // Annotations can't be changed in review mode
            ArtemisUtils.displayInvalidReviewOperationBalloon();
            return;
        }

        if (annotation.getMistakeType().isCustomAnnotation()) {
            showCustomAnnotationDialog(
                    annotation.getMistakeType(),
                    annotation.getCustomMessage().orElseThrow(),
                    annotation.getCustomScore().orElseThrow(),
                    messageWithPoints -> {
                        annotation.setCustomMessage(messageWithPoints.message());
                        annotation.setCustomScore(messageWithPoints.points());
                        this.notifyListeners();
                    });
        } else {
            showCustomMessageDialog(annotation.getCustomMessage().orElse(""), customMessage -> {
                if (customMessage.isBlank()) {
                    annotation.setCustomMessage(null);
                } else {
                    annotation.setCustomMessage(customMessage);
                }
                this.notifyListeners();
            });
        }
    }

    private void addPredefinedAnnotationWithCustomMessage(MistakeType mistakeType, Location location) {
        showCustomMessageDialog("", customMessage -> {
            this.assessment.addPredefinedAnnotation(mistakeType, location, customMessage);
            this.notifyListeners();
        });
    }

    private void addCustomAnnotation(MistakeType mistakeType, Location location) {
        showCustomAnnotationDialog(mistakeType, "", 0.0, messageWithPoints -> {
            this.assessment.addCustomAnnotation(
                    mistakeType, location, messageWithPoints.message(), messageWithPoints.points());
            this.notifyListeners();
        });
    }

    private void notifyListeners() {
        for (Consumer<List<Annotation>> listener : this.annotationsUpdatedListener) {
            listener.accept(this.assessment.getAnnotations(true));
        }
    }

    public static void showCustomMessageDialog(String initialMessage, Consumer<String> onOk) {
        CustomMessageDialogBuilder.create(initialMessage)
                .onSubmit(messageWithPoints -> onOk.accept(messageWithPoints.message()))
                .show();
    }

    private void showCustomAnnotationDialog(
            MistakeType mistakeType, String initialMessage, double initialPoints, Consumer<CustomMessageDialogBuilder.MessageWithPoints> onOk) {
        CustomMessageDialogBuilder.create(initialMessage)
                .onSubmit(onOk)
                .allowCustomScore(mistakeType, initialPoints)
                .showNotModal();
    }
}
