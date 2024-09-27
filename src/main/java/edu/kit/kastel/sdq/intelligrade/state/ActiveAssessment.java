/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.state;

import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.ClonedProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.sdq.intelligrade.autograder.AutograderTask;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.AutograderOption;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import edu.kit.kastel.sdq.intelligrade.utils.CodeSelection;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import net.miginfocom.swing.MigLayout;

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
        listener.accept(assessment.getAnnotations());
    }

    public GradingConfig getGradingConfig() {
        return assessment.getConfig();
    }

    public void addAnnotationAtCaret(MistakeType mistakeType, boolean withCustomMessage) {
        if (assessment == null) {
            throw new IllegalStateException("No active assessment");
        }

        var selection = CodeSelection.fromCaret();
        if (selection.isEmpty()) {
            ArtemisUtils.displayGenericErrorBalloon(
                    "No code selected", "Cannot create annotation without code selection");
            return;
        }

        var editor = IntellijUtil.getActiveEditor();
        int startLine = editor.getDocument().getLineNumber(selection.get().startOffset());
        int endLine = editor.getDocument().getLineNumber(selection.get().endOffset());
        String path = Path.of(IntellijUtil.getActiveProject().getBasePath())
                .resolve(ASSIGNMENT_SUB_PATH)
                .relativize(selection.get().path())
                .toString();

        if (mistakeType.isCustomAnnotation()) {
            addCustomAnnotation(mistakeType, startLine, endLine, path);
        } else {
            if (withCustomMessage) {
                addPredefinedAnnotationWithCustomMessage(mistakeType, startLine, endLine, path);
            } else {
                assessment.addPredefinedAnnotation(mistakeType, path, startLine, endLine, null);
                this.notifyListeners();
            }
        }
    }

    public void deleteAnnotation(Annotation annotation) {
        this.assessment.removeAnnotation(annotation);
        this.notifyListeners();
    }

    public void runAutograder() {
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

    private void addPredefinedAnnotationWithCustomMessage(
            MistakeType mistakeType, int startLine, int endLine, String path) {
        showCustomMessageDialog("", customMessage -> {
            this.assessment.addPredefinedAnnotation(mistakeType, path, startLine, endLine, customMessage);
            this.notifyListeners();
        });
    }

    private void addCustomAnnotation(MistakeType mistakeType, int startLine, int endLine, String path) {
        showCustomAnnotationDialog(mistakeType, "", 0.0, messageWithPoints -> {
            this.assessment.addCustomAnnotation(
                    mistakeType, path, startLine, endLine, messageWithPoints.message(), messageWithPoints.points());
            this.notifyListeners();
        });
    }

    private void notifyListeners() {
        for (Consumer<List<Annotation>> listener : this.annotationsUpdatedListener) {
            listener.accept(this.assessment.getAnnotations());
        }
    }

    private void showCustomMessageDialog(String initialMessage, Consumer<String> onOk) {
        var panel = new JBPanel<>(new MigLayout("wrap 1", "[250lp]"));

        var customMessage = new JBTextArea(initialMessage);
        customMessage.setFont(JBFont.regular());
        customMessage.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        panel.add(ScrollPaneFactory.createScrollPane(customMessage), "grow, height 100lp");

        var popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, customMessage)
                .setTitle("Custom Message")
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(true)
                .setNormalWindowLevel(true)
                .setOkHandler(() -> onOk.accept(customMessage.getText().trim()))
                .createPopup();

        customMessage.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isControlDown()) {
                        customMessage.insert("\n", customMessage.getCaretPosition());
                    } else {
                        popup.closeOk((InputEvent) EventQueue.getCurrentEvent());
                    }
                }
            }
        });

        popup.showCenteredInCurrentWindow(IntellijUtil.getActiveProject());
    }

    private void showCustomAnnotationDialog(
            MistakeType mistakeType, String initialMessage, double initialPoints, Consumer<MessageWithPoints> onOk) {
        var panel = new JBPanel<>(new MigLayout("wrap 2", "[200lp] []"));

        var customMessage = new JBTextArea(initialMessage);
        customMessage.setFont(JBFont.regular());
        customMessage.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        panel.add(ScrollPaneFactory.createScrollPane(customMessage), "span 2, grow, height 100lp");

        double maxValue = this.assessment.getConfig().isPositiveFeedbackAllowed() ? Double.MAX_VALUE : 0.0;
        double minValue = mistakeType.getRatingGroup().getMinPenalty();
        var customScore = new JSpinner(new SpinnerNumberModel(initialPoints, minValue, maxValue, 0.5));
        panel.add(customScore, "spanx 2, growx");

        var okButton = new JButton("Ok");
        panel.add(okButton, "skip 1, tag ok");

        var popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, customMessage)
                .setTitle("Custom Comment")
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(true)
                .setNormalWindowLevel(true)
                .setOkHandler(() -> onOk.accept(
                        new MessageWithPoints(customMessage.getText().trim(), (double) customScore.getValue())))
                .createPopup();

        okButton.addActionListener(a -> popup.closeOk((InputEvent) EventQueue.getCurrentEvent()));
        customMessage.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isControlDown()) {
                    popup.closeOk((InputEvent) EventQueue.getCurrentEvent());
                }
            }
        });

        popup.showCenteredInCurrentWindow(IntellijUtil.getActiveProject());
    }

    private record MessageWithPoints(String message, double points) {}
}
