/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.event.ActionEvent;
import java.time.ZoneId;
import java.util.ArrayList;
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
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBRadioButton;
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound;
import edu.kit.kastel.sdq.artemis4j.grading.PackedAssessment;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import edu.kit.kastel.sdq.intelligrade.widgets.FlowHideLayout;
import edu.kit.kastel.sdq.intelligrade.widgets.FlowWrapLayout;
import edu.kit.kastel.sdq.intelligrade.widgets.TextBuilder;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.NonNull;

public class BacklogPanel extends JPanel {
    private static final String SHOWN_SUBMISSIONS_TEXT = "Showing %d/%d";

    private final SearchTextField searchField;
    private CorrectionRound selectedRound;
    private final ButtonGroup buttonGroup;
    private final JTextComponent shownSubmissionsLabel;
    private final JPanel backlogList;

    private List<PackedAssessment> lastFetchedAssessments = new ArrayList<>();
    private final List<Runnable> onBacklogUpdate = new ArrayList<>();

    public BacklogPanel() {
        super(new MigLayout("wrap 1", "[grow]"));

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
            this.buttonGroup.add(button);
            filterPanel.add(button);
        }

        this.backlogList = new JBPanel<>(new FlowHideLayout(Set.of(0, 4), "gapx 10", "[][][][][grow]"));
        this.add(this.backlogList, "grow");

        var refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.addActionListener(this::refreshButtonClicked);
        this.add(refreshButton, "alignx right");
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
        for (Runnable runnable : this.onBacklogUpdate) {
            runnable.run();
        }
    }

    public void addBacklogUpdateListener(Runnable listener) {
        this.onBacklogUpdate.add(listener);
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
                    // This shouldn't be necessary, but just to be safe:
                    .filter(packedAssessment -> packedAssessment.result().completionDate() != null)
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
        this.backlogList.removeAll();

        if (PluginState.getInstance().hasReviewConfig()) {
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

    private static JButton createActionButton(PackedAssessment assessment) {
        // Action Button
        JButton reopenButton;
        if (assessment.isSubmitted()) {
            reopenButton = new JButton("Reopen");
        } else {
            reopenButton = new JButton("Continue");
            reopenButton.setForeground(JBColor.ORANGE);
        }
        reopenButton.addActionListener(a -> PluginState.getInstance().reopenAssessment(assessment));

        return reopenButton;
    }
}
