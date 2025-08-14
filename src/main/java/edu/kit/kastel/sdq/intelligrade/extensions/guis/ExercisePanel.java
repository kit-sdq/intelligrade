/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;

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
import com.intellij.util.ui.JBUI;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.client.AssessmentStatsDTO;
import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnection;
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound;
import edu.kit.kastel.sdq.artemis4j.grading.Course;
import edu.kit.kastel.sdq.artemis4j.grading.Exam;
import edu.kit.kastel.sdq.artemis4j.grading.PackedAssessment;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
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
    private JButton openInstructorDialog;
    private TextFieldWithBrowseButton gradingConfigPathInput;
    private transient Border originalGradingConfigBorder = null;

    private JPanel statisticsPanel;
    private JBLabel totalStatisticsLabel;
    private JBLabel userStatisticsLabel;

    private JPanel assessmentOrReviewPanel;

    private JPanel assessmentPanel;
    private JButton submitAssessmentButton;
    private JButton cancelAssessmentButton;
    private JButton saveAssessmentButton;
    private JButton closeAssessmentButton;
    private JButton reRunAutograder;

    private JPanel reviewPanel;
    private JButton submitReviewButton;
    private JButton cancelReviewButton;

    private BacklogPanel backlogPanel;

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

        assessmentOrReviewPanel = new JBPanel<>(new MigLayout("wrap 1, fillx", "[grow]"));
        createAssessmentPanel();
        createReviewPanel();
        content.add(new TitledSeparator("Assessment"), "spanx 2, growx");
        content.add(assessmentOrReviewPanel, "span 2, growx");

        content.add(new TitledSeparator("Backlog"), "spanx 2, growx");
        backlogPanel = new BacklogPanel();
        backlogPanel.addBacklogUpdateListener(this::updateBacklogAndStats);
        content.add(backlogPanel, "span 2, growx");

        setContent(ScrollPaneFactory.createScrollPane(content));

        exerciseSelector.addItemListener(this::handleExerciseSelected);

        examSelector.addItemListener(this::handleExamSelected);

        courseSelector.addItemListener(this::handleCourseSelected);

        PluginState.getInstance().registerConnectedListener(this::handleConnectionChange);

        PluginState.getInstance().registerAssessmentStartedListener(this::handleAssessmentStarted);

        PluginState.getInstance().registerAssessmentClosedListener(this::handleAssessmentClosed);

        PluginState.getInstance().registerGradingConfigChangedListener(this::handleGradingConfigChanged);

        PluginState.getInstance().registerMissingGradingConfigListeners(this::handleMissingGradingConfig);
    }

    private void createGeneralPanel() {
        generalPanel = new JBPanel<>(new MigLayout("wrap 1", "[grow]"));

        startGradingRound1Button = new JButton("Start Grading Round 1");
        startGradingRound1Button.setForeground(JBColor.GREEN);
        startGradingRound1Button.addActionListener(
                a -> PluginState.getInstance().startNextAssessment(CorrectionRound.FIRST));
        generalPanel.add(startGradingRound1Button, "growx");

        startGradingRound2Button = new JButton("Start Grading Round 2");
        startGradingRound2Button.setForeground(JBColor.GREEN);
        startGradingRound2Button.addActionListener(
                a -> PluginState.getInstance().startNextAssessment(CorrectionRound.SECOND));
        generalPanel.add(startGradingRound2Button, "growx");

        openInstructorDialog = new JButton("Show All Submissions");
        startGradingRound1Button.setForeground(JBColor.GREEN);
        openInstructorDialog.addActionListener(
                a -> SubmissionsInstructorDialog.showDialog());
        generalPanel.add(openInstructorDialog, "growx");

        gradingConfigPathInput = new TextFieldWithBrowseButton();
        gradingConfigPathInput.addBrowseFolderListener(
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor("json")));
        gradingConfigPathInput.setText(ArtemisSettingsState.getInstance().getSelectedGradingConfigPath());
        gradingConfigPathInput.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                PluginState.getInstance().setSelectedGradingConfigPath(gradingConfigPathInput.getText());

                // When nothing is selected, the border is red. This code is called when something has been selected
                // -> remove the red border
                if (originalGradingConfigBorder != null) {
                    gradingConfigPathInput.getTextField().setBorder(originalGradingConfigBorder);
                    originalGradingConfigBorder = null;
                }

                // TODO maybe this should be debounced in case the user types the path manually?
                updateSelectedExercise();
                updateAvailableActions();
            }
        });
        generalPanel.add(gradingConfigPathInput, "growx");

        var innerTextField = (JBTextField) gradingConfigPathInput.getTextField();
        innerTextField.getEmptyText().setText("Path to grading config");
        innerTextField.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, (Predicate<JBTextField>)
                f -> f.getText().isEmpty());
    }

    private void updateSelectedExercise() {
        var config = PluginState.getInstance().getGradingConfigDTO(false);
        if (config.isEmpty()) {
            return;
        }

        // if the selected exercise is compatible with the grading config, do nothing
        int selectedIndex = exerciseSelector.getSelectedIndex();
        if (config.get().isAllowedForExercise(
                exerciseSelector.getItemAt(selectedIndex).getId())) {
            return;
        }

        // this searches for the first exercise that the grading config can be used with
        for (int i = 0; i < exerciseSelector.getItemCount(); i++) {
            ProgrammingExercise exercise = exerciseSelector.getItemAt(i);
            if (config.get().isAllowedForExercise(exercise.getId())) {
                exerciseSelector.setSelectedIndex(i);
                return;
            }
        }
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

        submitAssessmentButton = new JButton("Submit Assessment");
        submitAssessmentButton.setForeground(JBColor.GREEN);
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
        saveAssessmentButton.addActionListener(a -> PluginState.getInstance().saveAssessment());
        assessmentPanel.add(saveAssessmentButton, "growx");

        closeAssessmentButton = new JButton("Close Assessment");
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

    private void createReviewPanel() {
        reviewPanel = new JBPanel<>(new MigLayout("wrap 1", "[grow]"));

        submitReviewButton = new JButton("Submit Review");
        submitReviewButton.setForeground(JBColor.GREEN);
        submitReviewButton.addActionListener(a -> PluginState.getInstance().submitAssessment());
        reviewPanel.add(submitReviewButton, "growx");

        cancelReviewButton = new JButton("Cancel Review");
        cancelReviewButton.addActionListener(a -> {
            var confirmed = MessageDialogBuilder.okCancel(
                            "Cancel Review?", "Your review will be discarded.")
                    .guessWindowAndAsk();

            if (confirmed) {
                PluginState.getInstance().closeAssessment();
            }
        });
        reviewPanel.add(cancelReviewButton, "growx");
    }

    private void updateAvailableActions() {
        // This functions consolidates all the logic that enables/disables buttons and panels
        // based on whether we have an active assessment and review/not review

        if (PluginState.getInstance().isAssessing()) {
            var assessment = PluginState.getInstance().getActiveAssessment().orElseThrow();
            boolean review = assessment.isReview();

            // Assessing -> can't start a new assessment
            startGradingRound1Button.setEnabled(false);
            startGradingRound2Button.setEnabled(false);

            // Select assessment buttons based on the active assessment and enable/disable buttons
            assessmentOrReviewPanel.removeAll();
            if (review) {
                assessmentOrReviewPanel.add(reviewPanel, "growx, pad 0");
                reviewPanel.setEnabled(true);
                submitReviewButton.setEnabled(true);
                cancelReviewButton.setEnabled(true);
            } else {
                assessmentOrReviewPanel.add(assessmentPanel, "growx, pad 0");
                assessmentPanel.setEnabled(true);
                submitAssessmentButton.setEnabled(true);
                cancelAssessmentButton.setEnabled(!assessment.getAssessment().isSubmitted());
                saveAssessmentButton.setEnabled(true);
                closeAssessmentButton.setEnabled(true);
                reRunAutograder.setEnabled(true);
            }
        } else {
            boolean review = PluginState.getInstance().hasReviewConfig();

            // Start buttons
            startGradingRound1Button.setEnabled(!review);
            var exercise = PluginState.getInstance().getActiveExercise();
            if (exercise.isPresent()) {
                startGradingRound2Button.setEnabled(!review && exercise.get().hasSecondCorrectionRound());
            }

            // Select assessment buttons based on the grading config
            assessmentOrReviewPanel.removeAll();
            if (review) {
                assessmentOrReviewPanel.add(reviewPanel, "growx, pad 0");
            } else {
                assessmentOrReviewPanel.add(assessmentPanel, "growx, pad 0");
            }

            // Disable all assessment buttons
            assessmentPanel.setEnabled(false);
            reviewPanel.setEnabled(false);
            submitAssessmentButton.setEnabled(false);
            cancelAssessmentButton.setEnabled(false);
            saveAssessmentButton.setEnabled(false);
            closeAssessmentButton.setEnabled(false);
            reRunAutograder.setEnabled(false);
            submitReviewButton.setEnabled(false);
            cancelReviewButton.setEnabled(false);
        }

        updateUI();
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
                    for (var exercise : sortExercises(group.getProgrammingExercises())) {
                        exerciseSelector.addItem(exercise);
                    }
                }
            } else {
                for (ProgrammingExercise programmingExercise :
                        sortExercises(courseSelector.getItem().getProgrammingExercises())) {
                    exerciseSelector.addItem(programmingExercise);
                }
            }
        } catch (ArtemisNetworkException ex) {
            LOG.warn(ex);
            ArtemisUtils.displayNetworkErrorBalloon("Failed to fetch exercise info", ex);
        }

        updateSelectedExercise();
        updateAvailableActions();
        updateUI();
    }

    private static <T extends ProgrammingExercise> List<T> sortExercises(List<T> exercises) {
        List<T> result = new ArrayList<>(exercises);

        result.sort((a, b) -> CharSequence.compare(a.getTitle(), b.getTitle()));

        return result;
    }

    private void handleCourseSelected(ItemEvent e) {
        if (e.getStateChange() != ItemEvent.DESELECTED) {
            var course = (Course) e.getItem();

            try {
                // Enable/disable instructor button(s)
                // Can't use PluginState::isInstructor here, since the exercise is not yet initialized
                openInstructorDialog.setEnabled(course.isInstructor(PluginState.getInstance().getAssessor()));

                // Update the exam selector with the exams of the course
                // This triggers an item event in the exam selector, which updates the exercise selector
                examSelector.removeAllItems();
                examSelector.addItem(new OptionalExam(null));
                for (Exam exam : course.getExams()) {
                    examSelector.addItem(new OptionalExam(exam));
                }
                updateSelectedExercise();
                updateAvailableActions();
                updateUI();
            } catch (ArtemisNetworkException ex) {
                LOG.warn(ex);
                ArtemisUtils.displayNetworkErrorBalloon("Failed to fetch exam info", ex);
            }
        }
    }

    private void handleConnectionChange(ArtemisConnection connection) {
        courseSelector.removeAllItems();

        if (connection != null) {
            // When a connection is established, update the course selector with the courses of the connection
            try {
                connectedLabel.setText("\u2714 Connected to "
                        + connection.getClient().getInstance().getDomain() + " as "
                        + connection.getAssessor().getLogin());
                connectedLabel.setForeground(JBColor.GREEN);
                for (Course course : connection.getCourses()) {
                    courseSelector.addItem(course);
                }
            } catch (ArtemisNetworkException ex) {
                LOG.warn(ex);
                ArtemisUtils.displayNetworkErrorBalloon("Failed to fetch course info", ex);
            }
        } else {
            connectedLabel.setText("\u274C Not connected");
            connectedLabel.setForeground(JBColor.RED);
        }

        updateAvailableActions();
        updateUI();
    }

    private void handleGradingConfigChanged(GradingConfig.GradingConfigDTO gradingConfigDTO) {
        updateAvailableActions();
    }

    private void handleAssessmentStarted(ActiveAssessment assessment) {
        // startGradingRound1Button.setEnabled(false);
        // startGradingRound2Button.setEnabled(false);
        //
        // if (assessment.isReview()) {
        //     assessmentPanel.setEnabled(false);
        //     reviewPanel.setEnabled(true);
        // } else {
        //     assessmentPanel.setEnabled(true);
        //     reviewPanel.setEnabled(false);
        //
        //     submitAssessmentButton.setEnabled(true);
        //     cancelAssessmentButton.setEnabled(
        //             !assessment.getAssessment().isSubmitted());
        //     saveAssessmentButton.setEnabled(true);
        //     closeAssessmentButton.setEnabled(true);
        //     reRunAutograder.setEnabled(true);
        // }
        updateAvailableActions();

        // We don't want the grading config to change while an assessment is in progress
        gradingConfigPathInput.setEnabled(false);

        updateBacklogAndStats();
    }

    private void handleAssessmentClosed() {
        // boolean review = PluginState.getInstance().hasReviewConfig();
        // startGradingRound1Button.setEnabled(!review);
        // // If no exercise is selected (e.g. not connected to artemis), the getItem() will return null.
        // if (exerciseSelector.getItem() != null) {
        //     startGradingRound2Button.setEnabled(!review && exerciseSelector.getItem().hasSecondCorrectionRound());
        // }
        //
        // assessmentPanel.setEnabled(false);
        // submitAssessmentButton.setEnabled(false);
        // cancelAssessmentButton.setEnabled(false);
        // saveAssessmentButton.setEnabled(false);
        // closeAssessmentButton.setEnabled(false);
        // reRunAutograder.setEnabled(false);
        //
        // reviewPanel.setEnabled(false);
        // submitReviewButton.setEnabled(false);
        // cancelReviewButton.setEnabled(false);

        updateAvailableActions();
        gradingConfigPathInput.setEnabled(true);
        updateBacklogAndStats();
    }

    private void handleMissingGradingConfig() {
        originalGradingConfigBorder = gradingConfigPathInput.getTextField().getBorder();
        // Add a red border to the field to indicate that the grading config is missing:
        gradingConfigPathInput.getTextField().setBorder(JBUI.Borders.customLine(JBColor.RED));
    }

    private void updateBacklogAndStats() {
        // Fetch data in the background, but do all UI updates on the EDT!
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (PluginState.getInstance().getActiveExercise().isEmpty()) {
                return;
            }

            var exercise = PluginState.getInstance().getActiveExercise().orElseThrow();

            List<PackedAssessment> assessments;
            AssessmentStatsDTO stats;

            try {
                assessments = exercise.fetchMyAssessments();
                stats = exercise.fetchAssessmentStats();
            } catch (ArtemisNetworkException ex) {
                LOG.warn(ex);
                ArtemisUtils.displayNetworkErrorBalloon("Failed to fetch backlog or statistics", ex);
                ApplicationManager.getApplication().invokeLater(() -> {
                    backlogPanel.clear();
                });
                return;
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                updateStatistics(exercise, stats, assessments);
                backlogPanel.setAssessments(assessments);
                updateUI();

                // Tell the user that we've done something
                ToolWindowManager.getInstance(IntellijUtil.getActiveProject())
                        .notifyByBalloon("Artemis", MessageType.INFO, "Backlog updated");
            });
        });
    }

    private void updateStatistics(
            ProgrammingExercise exercise, AssessmentStatsDTO stats, List<PackedAssessment> assessments) {
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
                assessments.stream().filter(PackedAssessment::isSubmitted).count();
        int lockedSubmissions = assessments.size() - submittedSubmissions;
        String userText = "%d (%d locked)".formatted(submittedSubmissions, lockedSubmissions);
        userStatisticsLabel.setText(userText);
    }

    private record OptionalExam(Exam exam) {
        @Override
        public String toString() {
            return exam == null ? "<No Exam>" : exam.toString();
        }
    }
}
