/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.event.ActionEvent;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;

import com.intellij.icons.AllIcons;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBRadioButton;
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound;
import edu.kit.kastel.sdq.artemis4j.grading.PackedAssessment;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.NonNull;

public class BacklogPanel extends JPanel {
    private final SearchTextField searchField;
    private CorrectionRound selectedRound;
    private final ButtonGroup buttonGroup;
    private final JBLabel shownSubmissionsLabel;
    private final JPanel backlogList;

    private List<PackedAssessment> lastFetchedAssessments = new ArrayList<>();
    private final List<Runnable> onBacklogUpdate = new ArrayList<>();

    public BacklogPanel() {
        super(new MigLayout("wrap 2", "[grow] []"));

        var filterPanel = new JBPanel<>(new MigLayout("wrap 4", "[][grow][][]"));
        this.add(filterPanel, "spanx 2, growx");

        shownSubmissionsLabel = new JBLabel();
        filterPanel.add(shownSubmissionsLabel);

        // Disabling history here so that in the exam review the next student can't see the previous student's id
        searchField = new SearchTextField(false);
        filterPanel.add(searchField, "growx");
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NonNull DocumentEvent documentEvent) {
                updateBacklog();
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
            button.addActionListener(a -> {
                this.selectedRound = correctionRound;
                updateBacklog();
            });
            buttonGroup.add(button);
            filterPanel.add(button);
        }

        backlogList = new JBPanel<>(new MigLayout("wrap 5, gapx 10", "[][][][][grow]"));
        this.add(ScrollPaneFactory.createScrollPane(backlogList, true), "spanx 2, growx");

        var refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.addActionListener(this::refreshButtonClicked);
        this.add(refreshButton, "skip 1, alignx right");
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

    private void refreshButtonClicked(ActionEvent actionEvent) {
        for (Runnable runnable : onBacklogUpdate) {
            runnable.run();
        }
    }

    public void addBacklogUpdateListener(Runnable listener) {
        onBacklogUpdate.add(listener);
    }

    public void setAssessments(List<PackedAssessment> assessments) {
        this.lastFetchedAssessments = new ArrayList<>(assessments);
        // Sort by submission date, which matches the ordering in the Artemis backlog
        this.lastFetchedAssessments.sort(
                Comparator.comparing(a -> a.submission().getSubmissionDate()));
        if (!this.lastFetchedAssessments.isEmpty()) {
            // The first one will be the oldest date, and the last one the newest date.
            //
            // Find the last assessment that has been submitted:
            var latestSubmission = this.lastFetchedAssessments.stream()
                    // If the assessment has not been submitted, it has no completion date -> skip these
                    .filter(PackedAssessment::isSubmitted)
                    // The dates are sorted from the oldest (smallest) to the newest (largest),
                    // thus the max is the latest date
                    .max(Comparator.comparing(
                            packedAssessment -> packedAssessment.result().completionDate()))
                    // This can happen if no assessment has been submitted yet
                    .orElse(this.lastFetchedAssessments.getFirst());

            int number = getRoundNumber(latestSubmission.round());
            this.buttonGroup.clearSelection();

            int i = 1;
            for (var e = this.buttonGroup.getElements(); e.hasMoreElements(); ) {
                var button = e.nextElement();
                if (i == number) {
                    button.setSelected(true);
                    this.selectedRound = latestSubmission.round();
                    break;
                }
                i += 1;
            }
        }

        this.updateBacklog();
    }

    public void clear() {
        this.backlogList.removeAll();
        this.updateUI();
    }

    private void updateBacklog() {
        backlogList.removeAll();

        if (PluginState.getInstance().hasReviewConfig()) {
            shownSubmissionsLabel.setText("Disabled");
            backlogList.add(new JBLabel("No backlog in review mode"));
            this.updateUI();
            return;
        }

        String searchText = searchField.getText();
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

            // Participant
            backlogList.add(new JBLabel(assessment.submission().getParticipantIdentifier()));
            addResultDateLabel(assessment);
            // Correction Round
            backlogList.add(new JBLabel(getRoundName(assessment.round())));
            addScoreItem(assessment);
            addActionButton(assessment);
        }

        shownSubmissionsLabel.setText("Showing %d/%d".formatted(shown, lastFetchedAssessments.size()));

        this.updateUI();
    }

    private void addResultDateLabel(PackedAssessment assessment) {
        String resultText = "";
        if (assessment.isSubmitted()) {
            resultText = assessment
                    .result()
                    .completionDate()
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .format(ArtemisUtils.DATE_TIME_PATTERN);
        }
        backlogList.add(new JBLabel(resultText), "alignx right");
    }

    private void addScoreItem(PackedAssessment assessment) {
        // Score in percent
        String resultText = "";
        if (assessment.isSubmitted()) {
            resultText = "%.0f%%".formatted(assessment.result().score());
        }
        backlogList.add(new JBLabel(resultText), "alignx right");
    }

    private void addActionButton(PackedAssessment assessment) {
        // Action Button
        JButton reopenButton;
        if (assessment.isSubmitted()) {
            reopenButton = new JButton("Reopen");
        } else {
            reopenButton = new JButton("Continue");
            reopenButton.setForeground(JBColor.ORANGE);
        }
        reopenButton.addActionListener(a -> PluginState.getInstance().reopenAssessment(assessment));
        backlogList.add(reopenButton, "growx");
    }
}
