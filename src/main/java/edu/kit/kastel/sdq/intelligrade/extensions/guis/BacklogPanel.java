/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound;
import edu.kit.kastel.sdq.artemis4j.grading.PackedAssessment;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.intelligrade.AssessmentTracker;
import edu.kit.kastel.sdq.intelligrade.listeners.AssessmentStateListener;
import edu.kit.kastel.sdq.intelligrade.listeners.ExerciseListener;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import edu.kit.kastel.sdq.intelligrade.state.ProjectState;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import edu.kit.kastel.sdq.intelligrade.utils.LatestRequestRunner;
import edu.kit.kastel.sdq.intelligrade.widgets.FlowHideLayout;
import edu.kit.kastel.sdq.intelligrade.widgets.FlowWrapLayout;
import edu.kit.kastel.sdq.intelligrade.widgets.TextBuilder;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class BacklogPanel extends JPanel {
    private static final String SHOWN_SUBMISSIONS_TEXT = "Showing %d/%d";

    private final Project project;
    private final LatestRequestRunner requestRunner;

    private final SearchTextField searchField;
    private CorrectionRound selectedRound = CorrectionRound.FIRST;
    private final ButtonGroup buttonGroup;
    private final JTextComponent shownSubmissionsLabel;
    private final JPanel backlogList;

    private List<PackedAssessment> lastFetchedAssessments = new ArrayList<>();
    private boolean reviewMode;

    BacklogPanel(Disposable parentDisposable, Project project) {
        super(new MigLayout("wrap 1", "[grow]"));
        this.project = project;
        this.requestRunner = new LatestRequestRunner(project);

        // The text search field is supposed to grow
        var filterPanel = new JBPanel<>(new FlowWrapLayout(List.of(
                new FlowWrapLayout.MigConstraint(1, "", "[grow]"),
                new FlowWrapLayout.MigConstraint(2, "", "[][grow]"),
                new FlowWrapLayout.MigConstraint(3, "", "[][grow][]"),
                new FlowWrapLayout.MigConstraint(4, "", "[][grow][][]"))));
        this.add(filterPanel, "grow");

        // HACK: When a placeholder is set here that is smaller than what will be set with setText, it will not redo
        //       the layout, until the user resizes the panel. This results in the label being cut off by other
        //       components.
        //       As a workaround the placeholder is set to a text that roughly matches the length of the actual text.
        //       Can be fixed by someone in the future if they are bored.
        this.shownSubmissionsLabel = TextBuilder.immutable(SHOWN_SUBMISSIONS_TEXT.formatted(100, 100))
                .text();
        filterPanel.add(this.shownSubmissionsLabel, "grow");

        // Disabling history here so that in the exam review the next student can't see the previous student's id
        this.searchField = new SearchTextField(false);

        filterPanel.add(this.searchField, "grow");
        this.searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NonNull DocumentEvent documentEvent) {
                reloadBacklogUI();
            }
        });

        // The button group ensures that only one button can be selected at a time
        this.buttonGroup = new ButtonGroup();
        for (var correctionRound : List.of(CorrectionRound.FIRST, CorrectionRound.SECOND)) {
            var button = new JBRadioButton(getRoundName(correctionRound));
            // Select the first round by default
            if (correctionRound == CorrectionRound.FIRST) {
                button.setSelected(true);
            }
            button.addActionListener(_ -> {
                this.selectedRound = correctionRound;
                reloadBacklogUI();
            });
            this.buttonGroup.add(button);
            filterPanel.add(button);
        }

        this.backlogList = new JBPanel<>(new FlowHideLayout(Set.of(0, 4), "gapx 10", "[][][][][grow]"));
        this.add(this.backlogList, "grow");

        var refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.addActionListener(_ -> this.refresh());
        this.add(refreshButton, "alignx right");

        ProjectState.getInstance(project).subscribe(parentDisposable, new ExerciseListener() {
            // The backlog panel has to be updated after an exercise changes:
            @Override
            public void exerciseChanged(@Nullable ProgrammingExercise exercise) {
                refresh();
            }

            @Override
            public void configChanged(GradingConfig.@Nullable GradingConfigDTO config) {
                reviewMode = config != null && config.review();
                reloadBacklogUI();
            }
        });

        // If an assessment is started or closed, backlog has to be updated to reflect that:
        AssessmentTracker.getInstance(project).subscribe(parentDisposable, new AssessmentStateListener() {
            @Override
            public void activeAssessmentChanged(@Nullable ActiveAssessment assessment) {
                refresh();
            }
        });
    }

    private static int getRoundNumber(CorrectionRound round) {
        return switch (round) {
            case FIRST -> 1;
            case SECOND -> 2;
            case REVIEW -> 3;
        };
    }

    private static String getRoundName(CorrectionRound round) {
        return switch (round) {
            case REVIEW -> "Review";
            case FIRST, SECOND -> "Round %d".formatted(getRoundNumber(round));
        };
    }

    private void refresh() {
        this.requestRunner
                .fetchArtemis(() -> {
                    if (ProjectState.getInstance(project).getActiveExercise().isEmpty()) {
                        return new ArrayList<PackedAssessment>();
                    }

                    var exercise = ProjectState.getInstance(project)
                            .getActiveExercise()
                            .orElseThrow();

                    var fetchedAssessments = new ArrayList<>(exercise.fetchMyAssessments());
                    // Sort by submission date, which matches the ordering in the Artemis backlog
                    fetchedAssessments.sort(
                            Comparator.comparing(a -> a.submission().getSubmissionDate()));

                    return fetchedAssessments;
                })
                .withErrorNotification("Failed to fetch backlog")
                .onFailureInEdt(_ -> {
                    this.backlogList.removeAll();
                    this.updateUI();
                })
                .thenIf(
                        () -> ProjectState.getInstance(project)
                                .getActiveExercise()
                                .isPresent(),
                        assessments -> {
                            var exercise = ProjectState.getInstance(project)
                                    .getActiveExercise()
                                    .orElseThrow();

                            CorrectionRound roundToSelect = null;
                            if (!assessments.isEmpty()) {
                                // The first one will be the oldest date, and the last one the newest date.
                                //
                                // Find the last assessment that has been submitted:
                                var latestSubmission = assessments.stream()
                                        // If the assessment has not been submitted, it has no completion date -> skip
                                        // these
                                        .filter(PackedAssessment::isSubmitted)
                                        // This shouldn't be necessary, but just to be safe:
                                        .filter(packedAssessment ->
                                                packedAssessment.result().completionDate() != null)
                                        // The dates are sorted from the oldest (smallest) to the newest (largest),
                                        // thus the max is the latest date
                                        .max(Comparator.comparing(packedAssessment ->
                                                packedAssessment.result().completionDate()))
                                        // This can happen if no assessment has been submitted yet
                                        .orElse(assessments.getFirst());
                                roundToSelect = latestSubmission.round();
                            }

                            this.lastFetchedAssessments = assessments;
                            selectRound(roundToSelect);

                            // Notify anyone interested that the assessments in the backlog have changed
                            project.getMessageBus()
                                    .syncPublisher(ExerciseListener.TOPIC)
                                    .assessmentsChanged(
                                            exercise, Collections.unmodifiableList(this.lastFetchedAssessments));

                            this.reloadBacklogUI();

                            // Tell the user that we've done something
                            ToolWindowManager.getInstance(project)
                                    .notifyByBalloon("Artemis", MessageType.INFO, "Backlog updated");
                        });
    }

    private void selectRound(@Nullable CorrectionRound round) {
        if (round == null) {
            return;
        }

        int number = getRoundNumber(round);
        this.buttonGroup.clearSelection();

        int i = 1;
        for (var elements = this.buttonGroup.getElements(); elements.hasMoreElements(); i += 1) {
            var button = elements.nextElement();
            if (i == number) {
                button.setSelected(true);
                this.selectedRound = round;
                break;
            }
        }
    }

    @RequiresEdt
    private void reloadBacklogUI() {
        this.backlogList.removeAll();

        if (this.reviewMode) {
            this.shownSubmissionsLabel.setText("Disabled");
            this.backlogList.add(
                    TextBuilder.immutable("No backlog in review mode").text());
            this.updateUI();
            return;
        }

        String searchText = this.searchField.getText();
        int shown = 0;
        for (var assessment : this.lastFetchedAssessments) {
            if (searchText != null
                    && !assessment.submission().getParticipantIdentifier().contains(searchText)) {
                continue;
            }

            if (assessment.round() != this.selectedRound && assessment.round() != CorrectionRound.REVIEW) {
                continue;
            }

            shown++;

            this.addBacklogEntry(assessment);
        }

        this.shownSubmissionsLabel.setText(SHOWN_SUBMISSIONS_TEXT.formatted(shown, this.lastFetchedAssessments.size()));
        this.updateUI();
    }

    private void addBacklogEntry(PackedAssessment assessment) {
        // Participant
        this.backlogList.add(TextBuilder.immutable(assessment.submission().getParticipantIdentifier())
                .text());
        this.backlogList.add(createResultDateLabel(assessment), "alignx right");

        // Correction Round
        this.backlogList.add(
                TextBuilder.immutable(getRoundName(assessment.round())).text());
        this.backlogList.add(createScoreItem(assessment), "alignx right");
        this.backlogList.add(createActionButton(assessment), "growx");
    }

    private static JComponent createResultDateLabel(PackedAssessment assessment) {
        String resultText = "";
        if (assessment.isSubmitted() && assessment.result().completionDate() != null) {
            resultText = assessment
                    .result()
                    .completionDate()
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .format(ArtemisUtils.DATE_TIME_PATTERN);
        }

        return TextBuilder.immutable(resultText).text();
    }

    private static JComponent createScoreItem(PackedAssessment assessment) {
        // Score in percent
        String resultText = "";
        if (assessment.isSubmitted()) {
            resultText = "%.0f%%".formatted(assessment.result().score());
        }

        return TextBuilder.immutable(resultText).text();
    }

    private JButton createActionButton(PackedAssessment assessment) {
        // Action Button
        JButton reopenButton;
        if (assessment.isSubmitted()) {
            reopenButton = new JButton("Reopen");
        } else {
            reopenButton = new JButton("Continue");
            reopenButton.setForeground(JBColor.ORANGE);
        }
        reopenButton.addActionListener(_ -> ProjectState.getInstance(project).reopenAssessment(assessment));

        return reopenButton;
    }
}
