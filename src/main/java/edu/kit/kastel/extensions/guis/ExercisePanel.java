/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.guis;

import java.awt.event.ItemEvent;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
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
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.client.AssessmentType;
import edu.kit.kastel.sdq.artemis4j.grading.Course;
import edu.kit.kastel.sdq.artemis4j.grading.Exam;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission;
import edu.kit.kastel.state.PluginState;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.EditorUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

public class ExercisePanel extends SimpleToolWindowPanel {
    private static final Logger LOG = Logger.getInstance(ExercisePanel.class);

    private final JBLabel connectedLabel;

    private final ComboBox<Course> courseSelector;
    private final ComboBox<OptionalExam> examSelector;
    private final ComboBox<ProgrammingExercise> exerciseSelector;
    private final TextFieldWithBrowseButton gradingConfigPathInput;

    private JPanel generalPanel;
    private JButton startGradingRound1Button;
    private JButton startGradingRound2Button;

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

        content.add(new JBLabel("Grading Config:"));
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
        content.add(gradingConfigPathInput, "growx");

        createGeneralPanel();
        content.add(generalPanel, "span 2, growx");

        createAssessmentPanel();
        content.add(assessmentPanel, "span 2, growx");

        createBacklogPanel();
        content.add(backlogPanel, "span 2, growx");

        setContent(ScrollPaneFactory.createScrollPane(content));

        exerciseSelector.addItemListener(e -> {
            // Exercise selected: Update plugin state, enable/disable grading buttons, update backlog
            if (e.getStateChange() != ItemEvent.DESELECTED) {
                var exercise = (ProgrammingExercise) e.getItem();
                startGradingRound2Button.setEnabled(
                        !PluginState.getInstance().isAssessing() && exercise.hasSecondCorrectionRound());

                PluginState.getInstance().setActiveCourse(courseSelector.getItem());
                PluginState.getInstance().setActiveExam(examSelector.getItem().exam());
                PluginState.getInstance().setActiveExercise(exercise);

                updateBacklog();
            }
        });

        examSelector.addItemListener(e -> {
            // If an exam was selected, update the exercise selector with the exercises of the exam
            // If no exam was selected, update the exercise selector with the exercises of the course
            if (e.getStateChange() != ItemEvent.DESELECTED) {
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
        });

        courseSelector.addItemListener(e -> {
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
        });

        PluginState.getInstance().registerConnectedListener(connection -> {
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
        });

        PluginState.getInstance().registerAssessmentStartedListener(assessment -> {
            startGradingRound1Button.setEnabled(false);
            startGradingRound2Button.setEnabled(false);

            assessmentPanel.setEnabled(true);
            submitAssessmentButton.setEnabled(true);
            cancelAssessmentButton.setEnabled(
                    !assessment.getAssessment().getSubmission().isSubmitted());
            saveAssessmentButton.setEnabled(true);
            closeAssessmentButton.setEnabled(true);
            reRunAutograder.setEnabled(true);
        });

        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            startGradingRound1Button.setEnabled(true);
            startGradingRound2Button.setEnabled(exerciseSelector.getItem().hasSecondCorrectionRound());

            assessmentPanel.setEnabled(false);
            submitAssessmentButton.setEnabled(false);
            cancelAssessmentButton.setEnabled(false);
            saveAssessmentButton.setEnabled(false);
            closeAssessmentButton.setEnabled(false);
            reRunAutograder.setEnabled(false);

            updateBacklog();
        });
    }

    private void createGeneralPanel() {
        generalPanel = new JBPanel<>().withBorder(new TitledBorder(new LineBorder(JBColor.border()), "General"));
        generalPanel.setLayout(new MigLayout("wrap 1", "[grow]"));

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
    }

    private void createAssessmentPanel() {
        assessmentPanel = new JBPanel<>(new MigLayout("wrap 2", "[grow][grow]"))
                .withBorder(new TitledBorder(new LineBorder(JBColor.border()), "Assessment"));
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
        backlogPanel = new JBPanel<>(new MigLayout("wrap 2", "[grow] []"))
                .withBorder(new TitledBorder(new LineBorder(JBColor.border()), "Backlog"));

        backlogList = new JBPanel<>(new MigLayout("wrap 4, gapx 30", "[][][][grow]"));
        backlogPanel.add(ScrollPaneFactory.createScrollPane(backlogList, true), "spanx 2, growx");

        var refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.addActionListener(a -> updateBacklog());
        backlogPanel.add(refreshButton, "skip 1, alignx right");
    }

    private void updateBacklog() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            var exercise = PluginState.getInstance().getActiveExercise().orElseThrow();

            List<ProgrammingSubmission> submissions;
            try {
                submissions = exercise.fetchSubmissions();
            } catch (ArtemisNetworkException ex) {
                LOG.warn(ex);
                ArtemisUtils.displayNetworkErrorBalloon("Failed to fetch backlog", ex);
                ApplicationManager.getApplication().invokeLater(() -> {
                    backlogList.removeAll();
                    this.updateUI();
                });
                return;
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                backlogList.removeAll();
                List<ProgrammingSubmission> sortedSubmissions = new ArrayList<>(submissions);
                sortedSubmissions.sort(Comparator.comparing(ProgrammingSubmission::getSubmissionDate));
                for (ProgrammingSubmission submission : sortedSubmissions) {
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
                        reopenButton = new JButton("Reopen Assessment");
                    } else if (latestResult.isPresent()
                            && latestResult.get().assessmentType() != AssessmentType.AUTOMATIC) {
                        reopenButton = new JButton("Continue Assessment");
                        reopenButton.setForeground(JBColor.ORANGE);
                    } else {
                        reopenButton = new JButton("Start Assessment");
                    }
                    reopenButton.addActionListener(
                            a -> PluginState.getInstance().reopenAssessment(submission));
                    backlogList.add(reopenButton, "growx");
                }

                updateUI();

                // Tell the user that we've done something
                ToolWindowManager.getInstance(EditorUtil.getActiveProject())
                        .notifyByBalloon("Artemis", MessageType.INFO, "Backlog updated");
            });
        });
    }

    private record OptionalExam(Exam exam) {
        @Override
        public String toString() {
            return exam == null ? "<No Exam>" : exam.toString();
        }
    }
}
