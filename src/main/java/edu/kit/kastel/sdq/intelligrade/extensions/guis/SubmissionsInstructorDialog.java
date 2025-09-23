/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
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
import org.jetbrains.annotations.Nullable;

public class SubmissionsInstructorDialog extends DialogWrapper {
    private static final Logger LOG = Logger.getInstance(SubmissionsInstructorDialog.class);

    private JPanel statusPanel;
    private SearchTextField searchField;
    private JBLabel shownSubmissionsLabel;
    private JBTable studentsTable;

    private List<ProgrammingSubmissionWithResults> allSubmissions = new ArrayList<>();

    public static void showDialog() {
        ApplicationManager.getApplication().invokeLater(() -> new SubmissionsInstructorDialog().show());
    }

    public SubmissionsInstructorDialog() {
        super((Project) null);

        this.setTitle("All Submissions");
        this.setModal(false);
        this.init();
        PluginState.getInstance().registerExerciseSelectedListener(this::fetchSubmissions);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        var panel = new JBPanel<>(new MigLayout("wrap 1, fill", "[400px:400px]", "[] 20px [] [200px:null, grow]"));

        statusPanel = new JBPanel<>(new MigLayout("wrap 3", "[grow][grow][grow]", "[50px:50px] [] [80:80px]"));
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
        refreshButton.addActionListener(a ->
                fetchSubmissions(PluginState.getInstance().getActiveExercise().orElse(null)));
        searchPanel.add(refreshButton);

        panel.add(searchPanel, "growx");

        this.studentsTable = new JBTable(new SubmissionsTableModel(List.of()));
        // this.studentsTable.setDefaultRenderer(Object.class, new SubmissionTableCellRenderer());
        studentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentsTable.getSelectionModel().addListSelectionListener(listSelectionEvent -> {
            var selectedRow = studentsTable.getSelectedRow();
            if (selectedRow >= 0) {
                var selectedSubmission =
                        ((SubmissionsTableModel) studentsTable.getModel()).submissions.get(selectedRow);
                setSelectedSubmission(selectedSubmission);
            }
        });
        panel.add(ScrollPaneFactory.createScrollPane(studentsTable), "spanx 3, grow");

        return panel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[] {this.myCancelAction};
    }

    private void fetchSubmissions(ProgrammingExercise exercise) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (exercise != null) {
                try {
                    this.allSubmissions = exercise.fetchAllSubmissions();
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
                .filter(submission -> {
                    String query = searchField.getText().trim().toLowerCase();
                    var student = submission.getSubmission().getStudent().orElseThrow();

                    // Login name, i.e. u-Kürzel
                    if (student.getLogin().toLowerCase().contains(query)) {
                        return true;
                    }

                    // Student id, i.e. matriculation number
                    return String.valueOf(student.getId()).contains(query);
                })
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
            studentPanel.add(new JBLabel("Tests: Passed %d/%d (%.2fP)"
                    .formatted(
                            automaticResult.get().passedTestCaseCount(),
                            automaticResult.get().testCaseCount(),
                            automaticResult.get().score()
                                    * submission.getSubmission().getExercise().getMaxPoints()
                                    / 100.0)));
        } else {
            studentPanel.add(new JBLabel("No tests executed"));
        }

        statusPanel.add(studentPanel, "spanx 3, growx");

        // action button
        boolean review = PluginState.getInstance().hasReviewConfig();
        JBLabel configInfo = new JBLabel();
        if (review) {
            configInfo.setText("You have a review config");
        } else {
            configInfo.setText("You have a regular config");
        }
        statusPanel.add(configInfo, "spanx 3, center");

        // correction rounds
        JPanel roundsPanel = new JBPanel<>(new MigLayout("wrap 3", "[grow, sg] [grow, sg] [grow, sg]", "[top, 250px]"));

        boolean allowFirstRoundEdit = !review && !submission.isSecondRoundStarted();
        roundsPanel.add(
                buildRoundPanel(
                        submission.getFirstRoundAssessment(),
                        CorrectionRound.FIRST,
                        allowFirstRoundEdit,
                        submission.getSubmission()),
                "grow");
        if (submission.getSubmission().getExercise().hasSecondCorrectionRound()) {
            boolean allowSecondRoundEdit = !review && submission.isFirstRoundFinished();
            roundsPanel.add(
                    buildRoundPanel(
                            submission.getSecondRoundAssessment(),
                            CorrectionRound.SECOND,
                            allowSecondRoundEdit,
                            submission.getSubmission()),
                    "grow");

            boolean allowReviewEdit = review && submission.isSecondRoundFinished();
            roundsPanel.add(
                    buildRoundPanel(
                            submission.getReviewAssessment(),
                            CorrectionRound.REVIEW,
                            allowReviewEdit,
                            submission.getSubmission()),
                    "grow");
        } else {
            // These panels are just for visual uniformity
            roundsPanel.add(buildRoundPanel(null, CorrectionRound.SECOND, false, submission.getSubmission()), "grow");
            roundsPanel.add(buildRoundPanel(null, CorrectionRound.REVIEW, false, submission.getSubmission()), "grow");
        }

        statusPanel.add(roundsPanel, "spanx 3, growx");

        this.updateUI();
    }

    private JComponent buildRoundPanel(
            PackedAssessment assessment, CorrectionRound round, boolean allowEdit, ProgrammingSubmission submission) {
        var panel = new JBPanel<>(
                new MigLayout("wrap 1, fill, aligny top", "[50px, center]", "[top, grow] [bottom, grow]"));

        String roundName =
                switch (round) {
                    case FIRST -> "Round 1";
                    case SECOND -> "Round 2";
                    case REVIEW -> "Review";
                };
        panel.add(new JBLabel(roundName).withFont(JBFont.h4()));

        JButton actionButton;
        if (assessment != null) {
            // TODO More info would be nice, but we can't reliably get it from the second round/review assessment
            // TODO Also, we maybe don't want to show this confidential information to students in the review session

            // Action button
            if (round != CorrectionRound.REVIEW) {
                if (assessment.isSubmitted()) {
                    actionButton = new JButton("Reopen");
                    actionButton.setForeground(JBColor.GREEN);
                } else {
                    actionButton = new JButton("Continue");
                    actionButton.setForeground(JBColor.ORANGE);
                }
            } else {
                actionButton = new JButton("Review");
                actionButton.setForeground(JBColor.GREEN);
            }
            actionButton.addActionListener(a -> {
                this.close(OK_EXIT_CODE);
                PluginState.getInstance().reopenAssessment(assessment);
            });
        } else {
            actionButton = new JButton("Start");
            actionButton.setForeground(JBColor.GREEN);
            actionButton.addActionListener(a -> {
                this.close(OK_EXIT_CODE);
                PluginState.getInstance().startAssessment(submission, round);
            });
        }
        actionButton.setEnabled(allowEdit);
        panel.add(actionButton);

        return panel;
    }

    private void updateUI() {
        // Both seems to be needed
        this.statusPanel.revalidate();
        this.repaint();
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

    // private static class SubmissionTableCellRenderer extends DefaultTableCellRenderer {
    //     @Override
    //     public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean
    // hasFocus, int row, int column) {
    //         Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    //
    //         var model = (SubmissionsTableModel) table.getModel();
    //         var submission = model.submissions.get(row);
    //         if (submission.getFirstRoundAssessment() != null) {
    //             boolean finished;
    //             if (submission.getSubmission().getExercise().hasSecondCorrectionRound()) {
    //                 finished = submission.getSecondRoundAssessment() != null &&
    // submission.getSecondRoundAssessment().isSubmitted();
    //             } else {
    //                 finished = submission.getFirstRoundAssessment() != null &&
    // submission.getFirstRoundAssessment().isSubmitted();
    //             }
    //
    //             if (finished) {
    //                 c.setBackground(JBColor.GREEN);
    //             } else {
    //                 c.setBackground(JBColor.ORANGE);
    //             }
    //         }
    //
    //         return c;
    //     }
    // }
}
