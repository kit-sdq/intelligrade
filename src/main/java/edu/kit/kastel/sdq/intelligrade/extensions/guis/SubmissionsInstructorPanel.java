package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBFont;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound;
import edu.kit.kastel.sdq.artemis4j.grading.PackedAssessment;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmissionWithResults;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubmissionsInstructorPanel extends SimpleToolWindowPanel {
    private static final Logger LOG = Logger.getInstance(SubmissionsInstructorPanel.class);

    private final JPanel statusPanel;
    private final SearchTextField searchField;
    private final JBLabel shownSubmissionsLabel;
    private final JBTable studentsTable;

    private List<ProgrammingSubmissionWithResults> allSubmissions = new ArrayList<>();

    public SubmissionsInstructorPanel() {
        super(true, true);

        var panel = new JBPanel<>(new MigLayout("wrap 1, fill", "[grow]", "[] 20px [] [grow]"));

        statusPanel = new JBPanel<>(new MigLayout("wrap 3, debug", "[grow][grow][grow]", "[50px:50px] [160px:160px]"));
        panel.add(statusPanel, "spanx 3, growx");

        var searchPanel = new JBPanel<>(new MigLayout("wrap 3", "[grow] [] 20px []"));

        searchField = new SearchTextField(false);
        searchPanel.add(searchField, "growx");
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                updateShownSubmissions();
            }
        });

        shownSubmissionsLabel = new JBLabel();
        searchPanel.add(shownSubmissionsLabel, "");

        var refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.addActionListener(a -> fetchSubmissions(PluginState.getInstance().getActiveExercise()));
        searchPanel.add(refreshButton);

        panel.add(searchPanel, "growx");

        this.studentsTable = new JBTable(new SubmissionsTableModel(List.of()));
        this.studentsTable.setDefaultRenderer(Object.class, new SubmissionTableCellRenderer());
        studentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentsTable.getSelectionModel().addListSelectionListener(listSelectionEvent -> {
            var selectedRow = studentsTable.getSelectedRow();
            if (selectedRow >= 0) {
                var selectedSubmission = ((SubmissionsTableModel) studentsTable.getModel()).submissions.get(selectedRow);
                setSelectedSubmission(selectedSubmission);
            }
        });
        panel.add(ScrollPaneFactory.createScrollPane(studentsTable), "spanx 3, grow");

        this.setContent(panel);

        PluginState.getInstance().registerExerciseSelectedListener(this::fetchSubmissions);
    }

    private void fetchSubmissions(Optional<ProgrammingExercise> exercise) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (exercise.isPresent()) {
                try {
                    this.allSubmissions = exercise.get().fetchAllSubmissions();
                } catch (ArtemisNetworkException e) {
                    ArtemisUtils.displayNetworkErrorBalloon("Failed to fetch assessments", e);
                }
            } else {
                this.allSubmissions = List.of();
            }

            ApplicationManager.getApplication().invokeLater(this::updateShownSubmissions);
        });
    }

    private void updateShownSubmissions() {
        var submissions = this.allSubmissions.stream()
                .filter(s -> s.getSubmission().getStudent().get().getLogin().contains(searchField.getText()))
                .toList();
        ((SubmissionsTableModel) this.studentsTable.getModel()).setSubmissions(submissions);
        shownSubmissionsLabel.setText("Showing %d/%d".formatted(submissions.size(), this.allSubmissions.size()));

        studentsTable.getSelectionModel().clearSelection();
        this.setSelectedSubmission(null);
    }

    private void setSelectedSubmission(ProgrammingSubmissionWithResults submission) {
        statusPanel.removeAll();

        if (submission == null) {
            statusPanel.add(new JBLabel("No submission selected").withFont(JBFont.h1()), "spanx 3, growx");

            this.updateUI();
            return;
        }

        // student
        var student = submission.getSubmission().getStudent().get();
        var studentPanel = new JBPanel<>(new MigLayout("wrap 2", "[grow, align left] [grow, align right]"));
        studentPanel.add(new JBLabel(student.getLogin() + " (" + student.getId() + ")").withFont(JBFont.h1()));

        var automaticResult = submission.getAutomaticResult();
        if (automaticResult.isPresent()) {
            studentPanel.add(new JBLabel("Tests: Passed %d/%d (%.2fP)".formatted(
                    automaticResult.get().passedTestCaseCount(),
                    automaticResult.get().testCaseCount(),
                    automaticResult.get().score() * submission.getSubmission().getExercise().getMaxPoints() / 100.0
            )));
        } else {
            studentPanel.add(new JBLabel("No tests executed"));
        }

        statusPanel.add(studentPanel, "spanx 3, growx");

        // correction rounds
        JPanel roundsPanel;
        if (submission.getSubmission().getExercise().hasSecondCorrectionRound()) {
            roundsPanel = new JBPanel<>(new MigLayout("wrap 3", "[grow, sg] [grow, sg] [grow, sg]"));
        } else {
            roundsPanel = new JBPanel<>(new MigLayout("wrap 1", "[grow, sg]"));
        }
        roundsPanel.add(buildRoundPanel(submission.getSubmission(), submission.getFirstRoundAssessment(), CorrectionRound.FIRST, submission.getSecondRoundAssessment() == null), "grow");

        if (submission.getSubmission().getExercise().hasSecondCorrectionRound()) {
            roundsPanel.add(buildRoundPanel(submission.getSubmission(), submission.getSecondRoundAssessment(), CorrectionRound.SECOND, submission.getFirstRoundAssessment() != null && submission.getFirstRoundAssessment().isSubmitted()), "grow");
            roundsPanel.add(buildRoundPanel(submission.getSubmission(), submission.getReviewAssessment(), CorrectionRound.REVIEW, submission.getSecondRoundAssessment() != null && submission.getSecondRoundAssessment().isSubmitted()), "grow");
        }

        statusPanel.add(roundsPanel, "spanx 3, growx");

        this.updateUI();
    }

    private JComponent buildRoundPanel(ProgrammingSubmission submission, PackedAssessment assessment, CorrectionRound round, boolean allowEdit) {
        var panel = new JBPanel<>(new MigLayout("wrap 1, fill, aligny top", "[grow, center]", "[top, grow] [grow] [grow] [grow] [grow]"));

        String roundName = switch (round) {
            case FIRST -> "Round 1";
            case SECOND -> "Round 2";
            case REVIEW -> "Review";
        };
        panel.add(new JBLabel(roundName).withFont(JBFont.h4()));

        JButton actionButton;
        if (assessment != null) {
            // Points
            // We do not have the feedbacks here, so we can't show the automatic/manual points split
            double points = assessment.result().score() * submission.getExercise().getMaxPoints() / 100.0;
            panel.add(new JBLabel("%.2f%% (~%.2fP)".formatted(assessment.result().score(), points)));

            // Assessment completion date
            if (assessment.isSubmitted()) {
                panel.add(new JBLabel("At " + assessment.result().completionDate().withZoneSameInstant(ZoneId.systemDefault())
                        .format(ArtemisUtils.DATE_TIME_PATTERN)));
            } else {
                panel.add(new JBLabel("In progress"));
            }

            // Assessor
            panel.add(new JBLabel("By " + assessment.getAssessor().getLogin()));

            // Action button
            if (round != CorrectionRound.REVIEW) {
                if (assessment.isSubmitted()) {
                    actionButton = new JButton("Reopen");
                } else {
                    actionButton = new JButton("Continue");
                    actionButton.setForeground(JBColor.ORANGE);
                }
            } else {
                actionButton = new JButton("Review");
            }
            actionButton.addActionListener(a -> PluginState.getInstance().reopenAssessment(assessment));

        } else {
            actionButton = new JButton("Start");
            actionButton.addActionListener(a -> PluginState.getInstance().startAssessment(submission, round));
        }
        actionButton.setEnabled(allowEdit);
        panel.add(actionButton);

        return panel;
    }

    private static class SubmissionsTableModel extends AbstractTableModel {
        private List<ProgrammingSubmissionWithResults> submissions;

        public SubmissionsTableModel(List<ProgrammingSubmissionWithResults> submissions) {
            this.submissions = submissions;
        }

        @Override
        public int getRowCount() {
            return submissions.size();
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public @Nls String getColumnName(int i) {
            return "Student";
        }

        @Override
        public Class<?> getColumnClass(int i) {
            return String.class;
        }

        @Override
        public Object getValueAt(int i, int i1) {
            return submissions.get(i).getSubmission().getStudent().get().getLogin();
        }

        public void setSubmissions(List<ProgrammingSubmissionWithResults> submissions) {
            this.submissions = submissions;
            fireTableDataChanged();
        }
    }

    private static class SubmissionTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            var model = (SubmissionsTableModel) table.getModel();
            var submission = model.submissions.get(row);
            if (submission.getFirstRoundAssessment() != null) {
                boolean finished;
                if (submission.getSubmission().getExercise().hasSecondCorrectionRound()) {
                    finished = submission.getSecondRoundAssessment() != null && submission.getSecondRoundAssessment().isSubmitted();
                } else {
                    finished = submission.getFirstRoundAssessment() != null && submission.getFirstRoundAssessment().isSubmitted();
                }

                if (finished) {
                    c.setBackground(JBColor.GREEN);
                } else {
                    c.setBackground(JBColor.ORANGE);
                }
            }

            return c;
        }
    }
}
