/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import javax.swing.JButton;
import javax.swing.JPanel;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.intelligrade.AssessmentTracker;
import edu.kit.kastel.sdq.intelligrade.SubmitAction;
import edu.kit.kastel.sdq.intelligrade.listeners.AssessmentStateListener;
import edu.kit.kastel.sdq.intelligrade.listeners.ExerciseListener;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import edu.kit.kastel.sdq.intelligrade.state.ProjectState;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtils;
import edu.kit.kastel.sdq.intelligrade.widgets.FlowWrapLayout;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.Nullable;

/**
 * This panel contains the actions that can be performed while an assessment is in progress
 * like submitting, cancelling or saving the assessment.
 */
class AssessmentActionsPanel extends JBPanel<AssessmentActionsPanel> {
    private final JPanel assessmentPanel;
    private final Project project;
    private JButton submitAssessmentButton;
    private JButton cancelAssessmentButton;
    private JButton saveAssessmentButton;
    private JButton closeAssessmentButton;
    private JButton reRunAutograderButton;

    private final JPanel reviewPanel;
    private JButton submitReviewButton;
    private JButton cancelReviewButton;
    private @Nullable ActiveAssessment activeAssessment;
    private boolean reviewMode;

    AssessmentActionsPanel(Disposable parentDisposable, Project project) {
        super(new MigLayout("wrap 1, fillx", "[grow]"));

        this.project = project;
        this.assessmentPanel = createAssessmentPanel();
        this.reviewPanel = createReviewPanel();

        AssessmentTracker.getInstance(project).subscribe(parentDisposable, new AssessmentStateListener() {
            @Override
            public void activeAssessmentChanged(@Nullable ActiveAssessment assessment) {
                activeAssessment = assessment;
                if (assessment == null) {
                    // Select assessment buttons based on the grading config
                    AssessmentActionsPanel.this.showInactive(reviewMode);
                    return;
                }

                // Select assessment buttons based on the active assessment and enable/disable buttons
                if (assessment.isReview()) {
                    AssessmentActionsPanel.this.showActiveReview();
                } else {
                    AssessmentActionsPanel.this.showActiveAssessment(
                            !assessment.getAssessment().isSubmitted());
                }
            }
        });

        ProjectState.getInstance(project).subscribe(parentDisposable, new ExerciseListener() {
            @Override
            public void configChanged(GradingConfig.@Nullable GradingConfigDTO config) {
                reviewMode = config != null && config.review();
                if (activeAssessment == null) {
                    showInactive(reviewMode);
                }
            }
        });
    }

    private void submitAction(SubmitAction action) {
        ProjectState.getInstance(project).updateAssessmentState(action);
    }

    private JPanel createAssessmentPanel() {
        var panel = new JBPanel<>(new FlowWrapLayout(2));

        submitAssessmentButton = IntellijUtils.createWrappingButton("Submit Assessment");
        submitAssessmentButton.setForeground(JBColor.GREEN);
        submitAssessmentButton.addActionListener(_ -> submitAction(SubmitAction.SUBMIT));
        panel.add(submitAssessmentButton, "grow");

        cancelAssessmentButton = IntellijUtils.createWrappingButton("Cancel Assessment");
        cancelAssessmentButton.setEnabled(false);
        cancelAssessmentButton.addActionListener(_ -> {
            var confirmed = MessageDialogBuilder.okCancel(
                            "Cancel Assessment?", "Your assessment will be discarded, and the lock will be freed.")
                    .guessWindowAndAsk();

            if (confirmed) {
                submitAction(SubmitAction.CANCEL);
            }
        });
        panel.add(cancelAssessmentButton, "grow");

        saveAssessmentButton = IntellijUtils.createWrappingButton("Save Assessment");
        saveAssessmentButton.addActionListener(_ -> submitAction(SubmitAction.SAVE));
        panel.add(saveAssessmentButton, "grow");

        closeAssessmentButton = IntellijUtils.createWrappingButton("Close Assessment");
        closeAssessmentButton.addActionListener(_ -> {
            var confirmed = MessageDialogBuilder.okCancel(
                            "Close Assessment?", "Your will loose any unsaved progress, but you will keep the lock.")
                    .guessWindowAndAsk();

            if (confirmed) {
                submitAction(SubmitAction.CLOSE);
            }
        });
        panel.add(closeAssessmentButton, "grow");

        reRunAutograderButton = IntellijUtils.createWrappingButton("Re-run Autograder");
        reRunAutograderButton.addActionListener(_ -> {
            var confirmed = MessageDialogBuilder.okCancel(
                            "Re-Run Autograder?", "This may create duplicate annotations!")
                    .guessWindowAndAsk();

            if (confirmed) {
                ProjectState.getInstance(project)
                        .getActiveAssessment()
                        .orElseThrow()
                        .runAutograder();
            }
        });
        panel.add(reRunAutograderButton, "spanx 2, grow");

        return panel;
    }

    private JPanel createReviewPanel() {
        var panel = new JBPanel<>(new MigLayout("wrap 1", "[grow]"));

        submitReviewButton = IntellijUtils.createWrappingButton("Submit Review");
        submitReviewButton.setForeground(JBColor.GREEN);
        submitReviewButton.addActionListener(_ -> submitAction(SubmitAction.SUBMIT));
        panel.add(submitReviewButton, "grow");

        cancelReviewButton = IntellijUtils.createWrappingButton("Cancel Review");
        cancelReviewButton.addActionListener(_ -> {
            var confirmed = MessageDialogBuilder.okCancel("Cancel Review?", "Your review will be discarded.")
                    .guessWindowAndAsk();

            if (confirmed) {
                submitAction(SubmitAction.CANCEL);
            }
        });
        panel.add(cancelReviewButton, "grow");

        return panel;
    }

    @RequiresEdt
    private void showActiveAssessment(boolean canCancelAssessment) {
        removeAll();
        add(assessmentPanel, "growx, pad 0");

        assessmentPanel.setEnabled(true);
        submitAssessmentButton.setEnabled(true);
        cancelAssessmentButton.setEnabled(canCancelAssessment);
        saveAssessmentButton.setEnabled(true);
        closeAssessmentButton.setEnabled(true);
        reRunAutograderButton.setEnabled(true);
    }

    @RequiresEdt
    private void showActiveReview() {
        removeAll();
        add(reviewPanel, "growx, pad 0");

        reviewPanel.setEnabled(true);
        submitReviewButton.setEnabled(true);
        cancelReviewButton.setEnabled(true);
    }

    @RequiresEdt
    private void showInactive(boolean reviewMode) {
        removeAll();
        add(reviewMode ? reviewPanel : assessmentPanel, "growx, pad 0");

        assessmentPanel.setEnabled(false);
        reviewPanel.setEnabled(false);
        submitAssessmentButton.setEnabled(false);
        cancelAssessmentButton.setEnabled(false);
        saveAssessmentButton.setEnabled(false);
        closeAssessmentButton.setEnabled(false);
        reRunAutograderButton.setEnabled(false);
        submitReviewButton.setEnabled(false);
        cancelReviewButton.setEnabled(false);
    }
}
