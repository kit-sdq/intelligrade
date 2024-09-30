/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.event.ItemEvent;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.TextComponentEmptyText;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.client.AssessmentStatsDTO;
import edu.kit.kastel.sdq.artemis4j.client.AssessmentType;
import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnection;
import edu.kit.kastel.sdq.artemis4j.grading.Course;
import edu.kit.kastel.sdq.artemis4j.grading.Exam;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

public class ExercisePanel extends SimpleToolWindowPanel {
    private static final Logger LOG = Logger.getInstance(ExercisePanel.class);

    private final JBLabel connectedLabel;

    private final ComboBox<Course> courseSelector;
    private final ComboBox<OptionalExam> examSelector;
    private final ComboBox<ProgrammingExercise> exerciseSelector;

    private JPanel generalPanel;
    private JButton startGradingRound1Button;
    private JButton startGradingRound2Button;
    private TextFieldWithBrowseButton gradingConfigPathInput;

    private JPanel statisticsPanel;
    private JBLabel totalStatisticsLabel;
    private JBLabel userStatisticsLabel;

    private JPanel assessmentPanel;
    private JButton submitAssessmentButton;
    private JButton cancelAssessmentButton;
    private JButton saveAssessmentButton;
    private JButton closeAssessmentButton;
    private JButton reRunAutograder;

    private JPanel backlogPanel;
    private JPanel backlogList;

    public ExercisePanel() {
        super(true, true);

        connectedLabel = new JBLabel();
        JPanel content = new JBPanel<>(new MigLayout("wrap 2", "[][grow]"));
        content.add(connectedLabel, "span 2, alignx center");

        content.add(new JBLabel("Course:"));
        courseSelector = new ComboBox<>();
        content.add(courseSelector, "growx");

        content.add(new JBLabel("Exam:"));
        examSelector = new ComboBox<>();
        content.add(examSelector, "growx");

        content.add(new JBLabel("Exercise:"));
        exerciseSelector = new ComboBox<>();
        content.add(exerciseSelector, "growx");

        createStatisticsPanel();
        content.add(statisticsPanel, "span 2, growx");

        createGeneralPanel();
        content.add(new TitledSeparator("General"), "spanx 2, growx");
        content.add(generalPanel, "span 2, growx");

        content.add(new TitledSeparator("Assessment"), "spanx 2, growx");
        createAssessmentPanel();
        content.add(assessmentPanel, "span 2, growx");

        content.add(new TitledSeparator("Backlog"), "spanx 2, growx");
        createBacklogPanel();
        content.add(backlogPanel, "span 2, growx");

        setContent(ScrollPaneFactory.createScrollPane(content));

        exerciseSelector.addItemListener(this::handleExerciseSelected);

        examSelector.addItemListener(this::handleExamSelected);

        courseSelector.addItemListener(this::handleCourseSelected);

        PluginState.getInstance().registerConnectedListener(this::handleConnectionChange);

        PluginState.getInstance().registerAssessmentStartedListener(this::handleAssessmentStarted);

        PluginState.getInstance().registerAssessmentClosedListener(this::handleAssessmentClosed);
    }

    private void createGeneralPanel() {
        generalPanel = new JBPanel<>(new MigLayout("wrap 1", "[grow]"));

        startGradingRound1Button = new JButton("Start Grading Round 1");
        startGradingRound1Button.setForeground(JBColor.GREEN);
        startGradingRound1Button.addActionListener(
                a -> PluginState.getInstance().startNextAssessment(0));
        generalPanel.add(startGradingRound1Button, "growx");

        startGradingRound2Button = new JButton("Start Grading Round 2");
        startGradingRound2Button.setForeground(JBColor.GREEN);
        startGradingRound2Button.addActionListener(
                a -> PluginState.getInstance().startNextAssessment(1));
        generalPanel.add(startGradingRound2Button, "growx");

        gradingConfigPathInput = new TextFieldWithBrowseButton();
        gradingConfigPathInput.addBrowseFolderListener(
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor("json")));
        gradingConfigPathInput.setText(ArtemisSettingsState.getInstance().getSelectedGradingConfigPath());
        gradingConfigPathInput.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                ArtemisSettingsState.getInstance().setSelectedGradingConfigPath(gradingConfigPathInput.getText());
            }
        });
        generalPanel.add(gradingConfigPathInput, "growx");

        var innerTextField = (JBTextField) gradingConfigPathInput.getTextField();
        innerTextField.getEmptyText().setText("Path to grading config");
        innerTextField.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, (Predicate<JBTextField>)
                f -> f.getText().isEmpty());
    }

    private void createStatisticsPanel() {
        statisticsPanel = new JBPanel<>(new MigLayout("wrap 2", "[][grow]"));

        statisticsPanel.add(new JBLabel("Submissions:"));
        totalStatisticsLabel = new JBLabel();
        statisticsPanel.add(totalStatisticsLabel);

        statisticsPanel.add(new JBLabel("Your Assessments:"));
        userStatisticsLabel = new JBLabel();
        statisticsPanel.add(userStatisticsLabel);
    }

    private void createAssessmentPanel() {
        assessmentPanel = new JBPanel<>(new MigLayout("wrap 2", "[grow][grow]"));
        assessmentPanel.setEnabled(false);

        submitAssessmentButton = new JButton("Submit Assessment");
        submitAssessmentButton.setForeground(JBColor.GREEN);
        submitAssessmentButton.setEnabled(false);
        submitAssessmentButton.addActionListener(a -> PluginState.getInstance().submitAssessment());
        assessmentPanel.add(submitAssessmentButton, "growx");

        cancelAssessmentButton = new JButton("Cancel Assessment");
        cancelAssessmentButton.setEnabled(false);
        cancelAssessmentButton.addActionListener(a -> {
            var confirmed = MessageDialogBuilder.okCancel(
                            "Cancel Assessment?", "Your assessment will be discarded, and the lock will be freed.")
                    .guessWindowAndAsk();

            if (confirmed) {
                PluginState.getInstance().cancelAssessment();
            }
        });
        assessmentPanel.add(cancelAssessmentButton, "growx");

        saveAssessmentButton = new JButton("Save Assessment");

        saveAssessmentButton.setEnabled(false);
        saveAssessmentButton.addActionListener(a -> PluginState.getInstance().saveAssessment());
        assessmentPanel.add(saveAssessmentButton, "growx");

        closeAssessmentButton = new JButton("Close Assessment");
        closeAssessmentButton.setEnabled(false);
        closeAssessmentButton.addActionListener(a -> {
            var confirmed = MessageDialogBuilder.okCancel(
                            "Close Assessment?", "Your will loose any unsaved progress, but you will keep the lock.")
                    .guessWindowAndAsk();

            if (confirmed) {
                PluginState.getInstance().closeAssessment();
            }
        });
        assessmentPanel.add(closeAssessmentButton, "growx");

        reRunAutograder = new JButton("Re-run Autograder");
        reRunAutograder.setEnabled(false);
        reRunAutograder.addActionListener(a -> {
            var confirmed = MessageDialogBuilder.okCancel(
                            "Re-Run Autograder?", "This may create duplicate annotations!")
                    .guessWindowAndAsk();

            if (confirmed) {
                PluginState.getInstance().getActiveAssessment().orElseThrow().runAutograder();
            }
        });
        assessmentPanel.add(reRunAutograder, "spanx 2, growx");
    }

    private void createBacklogPanel() {
        backlogPanel = new JBPanel<>(new MigLayout("wrap 2", "[grow] []"));

        backlogList = new JBPanel<>(new MigLayout("wrap 5, gapx 10", "[][][][][grow]"));
        backlogPanel.add(ScrollPaneFactory.createScrollPane(backlogList, true), "spanx 2, growx");

        var refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.addActionListener(a -> updateBacklogAndStats());
        backlogPanel.add(refreshButton, "skip 1, alignx right");
    }

    private void handleExerciseSelected(ItemEvent e) {
        // Exercise selected: Update plugin state, enable/disable grading buttons, update backlog
        if (e.getStateChange() != ItemEvent.DESELECTED) {
            var exercise = (ProgrammingExercise) e.getItem();
            startGradingRound2Button.setEnabled(
                    !PluginState.getInstance().isAssessing() && exercise.hasSecondCorrectionRound());

            PluginState.getInstance().setActiveExercise(exercise);

            updateBacklogAndStats();
        }
    }

    private void handleExamSelected(ItemEvent e) {
        // If an exam was selected, update the exercise selector with the exercises of the exam
        // If no exam was selected, update the exercise selector with the exercises of the course
        if (e.getStateChange() == ItemEvent.DESELECTED) {
            return;
        }

        try {
            exerciseSelector.removeAllItems();
            var item = (OptionalExam) e.getItem();
            if (item.exam() != null) {
                for (var group : item.exam().getExerciseGroups()) {
                    for (var exercise : group.getProgrammingExercises()) {
                        exerciseSelector.addItem(exercise);
                    }
                }
            } else {
                for (ProgrammingExercise programmingExercise :
                        courseSelector.getItem().getProgrammingExercises()) {
                    exerciseSelector.addItem(programmingExercise);
                }
            }
        } catch (ArtemisNetworkException ex) {
            LOG.warn(ex);
            ArtemisUtils.displayNetworkErrorBalloon("Failed to fetch exercise info", ex);
        }

        updateUI();
    }

    private void handleCourseSelected(ItemEvent e) {
        // Course was selected: Update the exam selector with the exams of the course
        // This triggers an item event in the exam selector, which updates the exercise selector
        if (e.getStateChange() != ItemEvent.DESELECTED) {
            try {
                var course = (Course) e.getItem();
                examSelector.removeAllItems();
                examSelector.addItem(new OptionalExam(null));
                for (Exam exam : course.getExams()) {
                    examSelector.addItem(new OptionalExam(exam));
                }
                updateUI();
            } catch (ArtemisNetworkException ex) {
                LOG.warn(ex);
                ArtemisUtils.displayNetworkErrorBalloon("Failed to fetch exam info", ex);
            }
        }
    }

    private void handleConnectionChange(Optional<ArtemisConnection> connection) {
        courseSelector.removeAllItems();

        if (connection.isPresent()) {
            // When a connection is established, update the course selector with the courses of the connection
            try {
                connectedLabel.setText("✔ Connected to "
                        + connection.get().getClient().getInstance().getDomain() + " as "
                        + connection.get().getAssessor().getLogin());
                connectedLabel.setForeground(JBColor.GREEN);
                for (Course course : connection.get().getCourses()) {
                    courseSelector.addItem(course);
                }
            } catch (ArtemisNetworkException ex) {
                LOG.warn(ex);
                ArtemisUtils.displayNetworkErrorBalloon("Failed to fetch course info", ex);
            }
        } else {
            connectedLabel.setText("❌ Not connected");
            connectedLabel.setForeground(JBColor.RED);
        }

        updateUI();
    }

    private void handleAssessmentStarted(ActiveAssessment assessment) {
        startGradingRound1Button.setEnabled(false);
        startGradingRound2Button.setEnabled(false);

        assessmentPanel.setEnabled(true);
        submitAssessmentButton.setEnabled(true);
        cancelAssessmentButton.setEnabled(
                !assessment.getAssessment().getSubmission().isSubmitted());
        saveAssessmentButton.setEnabled(true);
        closeAssessmentButton.setEnabled(true);
        reRunAutograder.setEnabled(true);

        updateBacklogAndStats();
    }

    private void handleAssessmentClosed() {
        startGradingRound1Button.setEnabled(true);
        startGradingRound2Button.setEnabled(exerciseSelector.getItem().hasSecondCorrectionRound());

        assessmentPanel.setEnabled(false);
        submitAssessmentButton.setEnabled(false);
        cancelAssessmentButton.setEnabled(false);
        saveAssessmentButton.setEnabled(false);
        closeAssessmentButton.setEnabled(false);
        reRunAutograder.setEnabled(false);

        updateBacklogAndStats();
    }

    private void updateBacklogAndStats() {
        // Fetch data in the background, but do all UI updates on the EDT!
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            var exercise = PluginState.getInstance().getActiveExercise().orElseThrow();

            List<ProgrammingSubmission> submissions;
            AssessmentStatsDTO stats;
            try {
                submissions = exercise.fetchSubmissions();
                stats = exercise.fetchAssessmentStats();
            } catch (ArtemisNetworkException ex) {
                LOG.warn(ex);
                ArtemisUtils.displayNetworkErrorBalloon("Failed to fetch backlog or statistics", ex);
                ApplicationManager.getApplication().invokeLater(() -> {
                    backlogList.removeAll();
                    this.updateUI();
                });
                return;
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                updateStatistics(exercise, stats, submissions);
                updateBacklog(submissions);
                updateUI();

                // Tell the user that we've done something
                ToolWindowManager.getInstance(IntellijUtil.getActiveProject())
                        .notifyByBalloon("Artemis", MessageType.INFO, "Backlog updated");
            });
        });
    }

    private void updateStatistics(
            ProgrammingExercise exercise, AssessmentStatsDTO stats, List<ProgrammingSubmission> submissions) {
        String totalText;
        if (exercise.hasSecondCorrectionRound()) {
            totalText = "%d / %d / %d (%d locked)"
                    .formatted(
                            stats.numberOfAssessmentsOfCorrectionRounds()
                                    .getFirst()
                                    .inTime(),
                            stats.numberOfAssessmentsOfCorrectionRounds().get(1).inTime(),
                            stats.numberOfSubmissions().inTime(),
                            stats.totalNumberOfAssessmentLocks());
        } else {
            totalText = "%d / %d (%d locked)"
                    .formatted(
                            stats.numberOfAssessmentsOfCorrectionRounds()
                                    .getFirst()
                                    .inTime(),
                            stats.numberOfSubmissions().inTime(),
                            stats.totalNumberOfAssessmentLocks());
        }
        totalStatisticsLabel.setText(totalText);

        int submittedSubmissions = (int)
                submissions.stream().filter(ProgrammingSubmission::isSubmitted).count();
        int lockedSubmissions = (int)
                submissions.stream().filter(ExercisePanel::isSubmissionStarted).count();
        String userText = "%d (%d locked)".formatted(submittedSubmissions, lockedSubmissions);
        userStatisticsLabel.setText(userText);
    }

    private void updateBacklog(List<ProgrammingSubmission> submissions) {
        backlogList.removeAll();

        List<ProgrammingSubmission> sortedSubmissions = new ArrayList<>(submissions);
        sortedSubmissions.sort(Comparator.comparing(ProgrammingSubmission::getSubmissionDate));
        for (ProgrammingSubmission submission : sortedSubmissions) {
            // Participant
            backlogList.add(new JBLabel(submission.getParticipantIdentifier()));

            // Submission date
            String dateText = submission
                    .getSubmissionDate()
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT));
            backlogList.add(new JBLabel(dateText), "alignx right");

            // Correction Round
            backlogList.add(new JBLabel("Round " + (submission.getCorrectionRound() + 1)));

            // Score in percent
            var latestResult = submission.getLatestResult();
            String resultText = "";
            if (submission.isSubmitted()) {
                resultText = latestResult
                        .map(resultDTO -> "%.0f%%".formatted(resultDTO.score()))
                        .orElse("???");
            }
            backlogList.add(new JBLabel(resultText), "alignx right");

            // Action Button
            JButton reopenButton;
            if (submission.isSubmitted()) {
                reopenButton = new JButton("Reopen");
            } else if (isSubmissionStarted(submission)) {
                reopenButton = new JButton("Continue");
                reopenButton.setForeground(JBColor.ORANGE);
            } else {
                reopenButton = new JButton("Start");
            }
            reopenButton.addActionListener(a -> PluginState.getInstance().reopenAssessment(submission));
            backlogList.add(reopenButton, "growx");
        }
    }

    private static boolean isSubmissionStarted(ProgrammingSubmission submission) {
        return !submission.isSubmitted()
                && submission.getLatestResult().isPresent()
                && submission.getLatestResult().get().assessmentType() != AssessmentType.AUTOMATIC;
    }

    private record OptionalExam(Exam exam) {
        @Override
        public String toString() {
            return exam == null ? "<No Exam>" : exam.toString();
        }
    }
}
