/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.event.ActionEvent;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;

import com.intellij.icons.AllIcons;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

public class BacklogPanel extends JPanel {
    private final SearchTextField searchField;
    private final JBCheckBox showFirstRound;
    private final JBCheckBox showSecondRound;
    private final JBLabel shownSubmissionsLabel;
    private final JPanel backlogList;

    private List<ProgrammingSubmission> lastFetchedSubmissions = new ArrayList<>();
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
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                updateBacklog();
            }
        });

        showFirstRound = new JBCheckBox("Round 1");
        showFirstRound.setSelected(true);
        showFirstRound.addActionListener(a -> updateBacklog());
        filterPanel.add(showFirstRound);

        showSecondRound = new JBCheckBox("Round 2");
        showSecondRound.setSelected(true);
        showSecondRound.addActionListener(a -> updateBacklog());
        filterPanel.add(showSecondRound);

        backlogList = new JBPanel<>(new MigLayout("wrap 5, gapx 10", "[][][][][grow]"));
        this.add(ScrollPaneFactory.createScrollPane(backlogList, true), "spanx 2, growx");

        var refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.addActionListener(this::refreshButtonClicked);
        this.add(refreshButton, "skip 1, alignx right");
    }

    private void refreshButtonClicked(ActionEvent actionEvent) {
        for (Runnable runnable : onBacklogUpdate) {
            runnable.run();
        }
    }

    public void addBacklogUpdateListener(Runnable listener) {
        onBacklogUpdate.add(listener);
    }

    public void setSubmissions(List<ProgrammingSubmission> submissions) {
        this.lastFetchedSubmissions = new ArrayList<>(submissions);
        this.lastFetchedSubmissions.sort(Comparator.comparing(ProgrammingSubmission::getSubmissionDate));
        this.updateBacklog();
    }

    public void clear() {
        this.backlogList.removeAll();
        this.updateUI();
    }

    private void updateBacklog() {
        backlogList.removeAll();

        String searchText = searchField.getText();
        boolean firstRound = showFirstRound.isSelected();
        boolean secondRound = showSecondRound.isSelected();

        int shown = 0;
        for (ProgrammingSubmission submission : lastFetchedSubmissions) {
            if (searchText != null && !submission.getParticipantIdentifier().contains(searchText)) {
                continue;
            }

            if (!firstRound && submission.getCorrectionRound() == 0) {
                continue;
            }

            if (!secondRound && submission.getCorrectionRound() == 1) {
                continue;
            }

            shown++;

            // Participant
            backlogList.add(new JBLabel(submission.getParticipantIdentifier()));
            addResultDateLabel(submission);
            // Correction Round
            backlogList.add(new JBLabel("Round " + (submission.getCorrectionRound() + 1)));
            addScoreItem(submission);
            addActionButton(submission);
        }

        shownSubmissionsLabel.setText("Showing %d/%d".formatted(shown, lastFetchedSubmissions.size()));

        this.updateUI();
    }

    private void addResultDateLabel(ProgrammingSubmission submission) {
        var latestResult = submission.getLatestResult();
        String resultText = "";
        if (submission.isSubmitted()) {
            resultText = latestResult
                    .map(resultDTO -> resultDTO.completionDate().withZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                    .orElse("???");
        }
        backlogList.add(new JBLabel(resultText), "alignx right");
    }

    private void addScoreItem(ProgrammingSubmission submission) {
        // Score in percent
        var latestResult = submission.getLatestResult();
        String resultText = "";
        if (submission.isSubmitted()) {
            resultText = latestResult
                    .map(resultDTO -> "%.0f%%".formatted(resultDTO.score()))
                    .orElse("???");
        }
        backlogList.add(new JBLabel(resultText), "alignx right");
    }

    private void addActionButton(ProgrammingSubmission submission) {
        // Action Button
        JButton reopenButton;
        if (submission.isSubmitted()) {
            reopenButton = new JButton("Reopen");
        } else if (ArtemisUtils.isSubmissionStarted(submission)) {
            reopenButton = new JButton("Continue");
            reopenButton.setForeground(JBColor.ORANGE);
        } else {
            reopenButton = new JButton("Start");
        }
        reopenButton.addActionListener(a -> PluginState.getInstance().reopenAssessment(submission));
        backlogList.add(reopenButton, "growx");
    }
}
