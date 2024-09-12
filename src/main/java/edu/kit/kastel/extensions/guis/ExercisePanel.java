package edu.kit.kastel.extensions.guis;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.grading.Course;
import edu.kit.kastel.sdq.artemis4j.grading.Exam;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.state.PluginState;
import edu.kit.kastel.utils.ArtemisUtils;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import java.awt.event.ItemEvent;

public class ExercisePanel extends SimpleToolWindowPanel {
    private final JPanel content = new JBPanel<>(new MigLayout("wrap 2", "[][grow]"));

    private ComboBox<Course> courseSelector;
    private ComboBox<OptionalExam> examSelector;
    private ComboBox<ProgrammingExercise> exerciseSelector;
    private TextFieldWithBrowseButton gradingConfigPathInput;

    private JPanel generalPanel;
    private JButton startGradingRound1Button;
    private JButton startGradingRound2Button;

    private JPanel assessmentPanel;
    private JButton saveAssessmentButton;
    private JButton submitAssessmentButton;
    private JButton reloadAssessmentButton;
    private JButton cancelAssessmentButton;
    private JButton reRunAutograder;

    private JPanel backlogPanel;
    private JPanel backlogList;

    public ExercisePanel() {
        super(true, true);

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
        gradingConfigPathInput.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor("json")));
        content.add(gradingConfigPathInput, "growx");

        createGeneralPanel();
        content.add(generalPanel, "span 2, growx");

        createAssessmentPanel();
        content.add(assessmentPanel, "span 2, growx");

        createBacklogPanel();
        content.add(backlogPanel, "span 2, growx");

        setContent(ScrollPaneFactory.createScrollPane(content));

        exerciseSelector.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.DESELECTED) {
                var exercise = (ProgrammingExercise) e.getItem();
                startGradingRound2Button.setEnabled(exercise.hasSecondCorrectionRound());

                PluginState.getInstance().setActiveCourse(courseSelector.getItem());
                PluginState.getInstance().setActiveExam(examSelector.getItem().exam());
                PluginState.getInstance().setActiveExercise(exercise);

                updateBacklog();
            }
        });

        examSelector.addItemListener(e -> {
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
                        courseSelector.getItem().getProgrammingExercises().forEach(exerciseSelector::addItem);
                    }
                    updateUI();

                } catch (ArtemisClientException ex) {
                    ArtemisUtils.displayGenericErrorBalloon("Failed to fetch exercise info: " + ex.getMessage());
                }
            }
        });

        courseSelector.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.DESELECTED) {
                try {
                    var course = (Course) e.getItem();
                    examSelector.removeAllItems();
                    examSelector.addItem(new OptionalExam(null));
                    course.getExams().forEach(exam -> examSelector.addItem(new OptionalExam(exam)));
                    updateUI();
                } catch (ArtemisClientException ex) {
                    ArtemisUtils.displayGenericErrorBalloon("Failed to fetch exam info: " + ex.getMessage());
                }
            }
        });

        PluginState.getInstance().registerConnectedListener(() -> {
            try {
                courseSelector.removeAllItems();
                PluginState.getInstance().getConnection().get().getCourses().forEach(courseSelector::addItem);
                updateUI();
            } catch (ArtemisClientException ex) {
                ArtemisUtils.displayGenericErrorBalloon("Failed to fetch course info: " + ex.getMessage());
            }
        });

        PluginState.getInstance().registerAssessmentStartedListener(assessment -> {
            assessmentPanel.setEnabled(true);
            saveAssessmentButton.setEnabled(true);
            submitAssessmentButton.setEnabled(true);
            reloadAssessmentButton.setEnabled(true);
            cancelAssessmentButton.setEnabled(!assessment.getAssessment().getSubmission().isSubmitted());
            reRunAutograder.setEnabled(true);
        });

        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            assessmentPanel.setEnabled(false);
            saveAssessmentButton.setEnabled(false);
            submitAssessmentButton.setEnabled(false);
            reloadAssessmentButton.setEnabled(false);
            cancelAssessmentButton.setEnabled(false);
            reRunAutograder.setEnabled(false);

            updateBacklog();
        });
    }

    private record OptionalExam(Exam exam) {
        @Override
        public String toString() {
            return exam == null ? "<No Exam>" : exam.toString();
        }
    }

    private void createGeneralPanel() {
        generalPanel = new JBPanel<>().withBorder(new TitledBorder("General"));
        generalPanel.setLayout(new MigLayout("wrap 1", "[grow]"));

        startGradingRound1Button = new JButton("Start Grading Round 1");
        startGradingRound1Button.addActionListener(a -> PluginState.getInstance().startNextAssessment(0));
        generalPanel.add(startGradingRound1Button, "growx");

        startGradingRound2Button = new JButton("Start Grading Round 2");
        startGradingRound2Button.addActionListener(a -> PluginState.getInstance().startNextAssessment(1));
        generalPanel.add(startGradingRound2Button, "growx");
    }

    private void createAssessmentPanel() {
        assessmentPanel = new JBPanel<>(new MigLayout("wrap 2", "[grow][grow]"))
                .withBorder(new TitledBorder("Assessment"));
        assessmentPanel.setEnabled(false);

        saveAssessmentButton = new JButton("Save Assessment");
        saveAssessmentButton.setEnabled(false);
        saveAssessmentButton.addActionListener(a -> PluginState.getInstance().saveAssessment());
        assessmentPanel.add(saveAssessmentButton, "growx");

        submitAssessmentButton = new JButton("Submit Assessment");
        submitAssessmentButton.setEnabled(false);
        submitAssessmentButton.addActionListener(a -> PluginState.getInstance().submitAssessment());
        assessmentPanel.add(submitAssessmentButton, "growx");

        reloadAssessmentButton = new JButton("Reload Assessment");
        reloadAssessmentButton.setEnabled(false);
        assessmentPanel.add(reloadAssessmentButton, "growx");

        cancelAssessmentButton = new JButton("Cancel Assessment");
        cancelAssessmentButton.setEnabled(false);
        cancelAssessmentButton.addActionListener(a -> PluginState.getInstance().cancelAssessment());
        assessmentPanel.add(cancelAssessmentButton, "growx");

        reRunAutograder = new JButton("Re-run Autograder");
        reRunAutograder.setEnabled(false);
        assessmentPanel.add(reRunAutograder, "spanx 2, growx");
    }

    private void createBacklogPanel() {
        backlogPanel = new JBPanel<>().withBorder(new TitledBorder("Backlog"));
        backlogPanel.setLayout(new MigLayout("", "[grow]"));

        backlogList = new JBPanel<>(new MigLayout("wrap 3", "[][][grow]"));
        backlogPanel.add(ScrollPaneFactory.createScrollPane(backlogList), "growx");
    }

    private void updateBacklog() {
        backlogList.removeAll();

        var exercise = PluginState.getInstance().getActiveExercise().get();
        try {
            exercise.fetchSubmissions(0, true).forEach(submission -> {
                backlogList.add(new JBLabel("12. 9. 2024"));
                backlogList.add(new JBLabel("90%"));

                JButton reopenButton;
                if (submission.isSubmitted()) {
                    reopenButton = new JButton("Reopen Assessment");
                } else {
                    reopenButton = new JButton("Continue Assessment");
                }
                reopenButton.addActionListener(a -> PluginState.getInstance().reopenAssessment(submission));
                backlogList.add(reopenButton, "growx");
            });
        } catch (ArtemisNetworkException e) {
            ArtemisUtils.displayGenericErrorBalloon("Failed to fetch backlog: " + e.getMessage());
        }

        updateUI();
    }
}
