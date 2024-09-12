/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.state;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import edu.kit.kastel.highlighter.HighlighterManager;
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.ClonedProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.CodeSelection;
import edu.kit.kastel.utils.EditorUtil;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ActiveAssessment {
    private final List<Consumer<List<Annotation>>> annotationsUpdatedListener = new ArrayList<>();

    private final Assessment assessment;
    private final ClonedProgrammingSubmission clonedSubmission;

    public ActiveAssessment(Assessment assessment, ClonedProgrammingSubmission clonedSubmission)
            throws IOException, ArtemisClientException {
        this.assessment = assessment;
        this.clonedSubmission = clonedSubmission;
    }

    public void registerAnnotationsUpdatedListener(Consumer<List<Annotation>> listener) {
        annotationsUpdatedListener.add(listener);
        listener.accept(assessment.getAnnotations());
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

        if (mistakeType.isCustomAnnotation()) {
            addCustomAnnotation(mistakeType, startLine, endLine, path);
        } else {
            if (withCustomMessage) {
                addPredefinedAnnotationWithCustomMessage(mistakeType, startLine, endLine, path);
            } else {
                addAnnotation(assessment.addPredefinedAnnotation(mistakeType, path, startLine, endLine, null));
            }
        }
    }

    public void deleteAnnotation(Annotation annotation) {
        HighlighterManager.deleteHighlighter(annotation);
        this.assessment.removeAnnotation(annotation);
        this.annotationsUpdatedListener.forEach(listener -> listener.accept(this.assessment.getAnnotations()));
    }

    public Assessment getAssessment() {
        return this.assessment;
    }

    private void addPredefinedAnnotationWithCustomMessage(MistakeType mistakeType, int startLine, int endLine, String path) {
        var customMessage = new JBTextField();
        var popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(customMessage, customMessage)
                .setTitle("Custom Message")
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(true)
                .setNormalWindowLevel(true)
                .setOkHandler(() -> {
                    addAnnotation(this.assessment.addPredefinedAnnotation(mistakeType, path, startLine, endLine, customMessage.getText()));
                })
                .createPopup();
        customMessage.addActionListener(a -> popup.closeOk((InputEvent) EventQueue.getCurrentEvent()));
        popup.showCenteredInCurrentWindow(EditorUtil.getActiveProject());
    }

    private void addCustomAnnotation(MistakeType mistakeType, int startLine, int endLine, String path) {
        var panel = new JBPanel<>(new MigLayout("wrap 2", "[100lp] []"));

        var customMessage = new JBTextArea();
        customMessage.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        panel.add(ScrollPaneFactory.createScrollPane(customMessage), "span 2, grow, height 100lp");

        double maxValue = this.assessment.getConfig().isPositiveFeedbackAllowed() ? Double.MAX_VALUE : 0.0;
        double minValue = mistakeType.getRatingGroup().getMinPenalty();
        var customScore = new JSpinner(new SpinnerNumberModel(0.0, minValue, maxValue, 0.5));
        panel.add(customScore, "spanx 2, growx");

        panel.add(new JSeparator(), "growx");

        var okButton = new JButton("Create");
        panel.add(okButton, "tag ok");

        var popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, customMessage)
                .setTitle("Custom Message")
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(true)
                .setNormalWindowLevel(true)
                .setOkHandler(() -> addAnnotation(assessment.addCustomAnnotation(mistakeType, path, startLine, endLine, customMessage.getText(), (double) customScore.getValue())))
                .createPopup();

        okButton.addActionListener(a -> popup.closeOk((InputEvent) EventQueue.getCurrentEvent()));

        popup.showCenteredInCurrentWindow(EditorUtil.getActiveProject());
    }

    private void addAnnotation(Annotation annotation) {
        HighlighterManager.createHighlighter(annotation);
        this.annotationsUpdatedListener.forEach(listener -> listener.accept(this.assessment.getAnnotations()));
    }
}
