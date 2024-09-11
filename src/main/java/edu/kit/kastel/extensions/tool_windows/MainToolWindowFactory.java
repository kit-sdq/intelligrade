/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.tool_windows;

import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

import com.intellij.DynamicBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import edu.kit.kastel.extensions.guis.AssessmentViewContent;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.listeners.GradingConfigSelectedListener;
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.grading.Course;
import edu.kit.kastel.sdq.artemis4j.grading.Exam;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.state.ActiveAssessment;
import edu.kit.kastel.state.PluginState;
import edu.kit.kastel.utils.ArtemisUtils;
import org.jetbrains.annotations.NotNull;

/**
 * This class handles all logic for the main grading UI.
 * It does not handle any other logic, that should be factored out.
 */
public class MainToolWindowFactory implements ToolWindowFactory, DumbAware {

    private static final String EXAMS_FETCH_ERROR_FORMAT = "Unable to fetch Exams for course %s.";
    private static final String EXERCISES_FETCH_ERROR_FORMAT = "Unable to fetch exercises for course %s.";
    private static final Locale LOCALE = DynamicBundle.getLocale();

    // set up automated GUI and generate necessary bindings
    private final JPanel contentPanel = new JPanel();
    private final AssessmentViewContent generatedMenu = new AssessmentViewContent();
    private final TextFieldWithBrowseButton gradingConfigInput = generatedMenu.getGradingConfigPathInput();
    private final ComboBox<Course> coursesComboBox = generatedMenu.getCoursesDropdown();
    private final ComboBox<Exam> examsComboBox = generatedMenu.getExamsDropdown();
    private final ComboBox<ProgrammingExercise> exerciseComboBox = generatedMenu.getExercisesDropdown();

    private final JButton startAssessment1Btn = generatedMenu.getBtnGradingRound1();

    private final JButton saveAssessmentBtn = generatedMenu.getBtnSaveAssessment();

    private final JButton submitAssessmentBtn = generatedMenu.getSubmitAssesmentBtn();

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        contentPanel.setLayout(new GridLayout());

        // give up if logging in to Artemis failed
        if (!PluginState.getInstance().isConnected()) {
            return;
        }

        // add content to menu panel
        contentPanel.add(generatedMenu);
        Content content = ContentFactory.getInstance().createContent(this.contentPanel, null, false);

        addListeners();
        try {
            populateDropdowns();
        } catch (ArtemisClientException exc) {
            ArtemisUtils.displayLoginErrorBalloon("Error retrieving courses!", null);
        }

        toolWindow.getContentManager().addContent(content);

        PluginState.getInstance().registerAssessmentStartedListener(this::onAssessmentStarted);
    }

    private void addListeners() {
        gradingConfigInput.addBrowseFolderListener(
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor("json")));

        // why the heck would you add a listener for text change like this????
        gradingConfigInput
                .getTextField()
                .getDocument()
                .addDocumentListener(new GradingConfigSelectedListener(gradingConfigInput));

        // set config path saved in settings
        ArtemisSettingsState settings = ArtemisSettingsState.getInstance();
        gradingConfigInput.setText(settings.getSelectedGradingConfigPath());

        coursesComboBox.addItemListener(itemEvent -> {
            if (itemEvent.getStateChange() == ItemEvent.DESELECTED) {
                return;
            }

            Course selectedCourse = ((Course) itemEvent.getItem());
            PluginState.getInstance().setActiveCourse(selectedCourse);
            populateExamDropdown(selectedCourse);
            populateExercisesDropdown(selectedCourse);
        });

        exerciseComboBox.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.DESELECTED) {
                PluginState.getInstance().setActiveExercise((ProgrammingExercise) e.getItem());
            }
        });

        // add listener for Button that starts first grading round
        startAssessment1Btn.addActionListener(e -> PluginState.getInstance().startNextAssessment());

        // button that saves assessment
        saveAssessmentBtn.addActionListener(e -> {
            // TODO
        });

        // button that submits assessment
        submitAssessmentBtn.addActionListener(e -> PluginState.getInstance().submitAssessment());
    }

    private void populateDropdowns() throws ArtemisClientException {
        var connection = PluginState.getInstance().getConnection().orElseThrow();

        // add all courses to the courses dropdown
        coursesComboBox.removeAllItems();
        connection.getCourses().forEach(coursesComboBox::addItem);

        // populate the dropdowns once because on load event listener is not triggered
        Course initial = ((Course) Objects.requireNonNull(coursesComboBox.getSelectedItem()));
        populateExamDropdown(initial);
        populateExercisesDropdown(initial);
    }

    private void populateExamDropdown(@NotNull Course course) {
        examsComboBox.removeAllItems();
        // we usually do not want to select the exam. Whe thus create a null Item
        examsComboBox.addItem(null);

        try {
            course.getExams().forEach(examsComboBox::addItem);
        } catch (ArtemisClientException e) {
            ArtemisUtils.displayGenericErrorBalloon(String.format(EXAMS_FETCH_ERROR_FORMAT, course));
        }
    }

    private void populateExercisesDropdown(@NotNull Course course) {
        exerciseComboBox.removeAllItems();

        try {
            course.getProgrammingExercises().forEach(exerciseComboBox::addItem);
        } catch (ArtemisClientException e) {
            ArtemisUtils.displayGenericErrorBalloon(String.format(EXERCISES_FETCH_ERROR_FORMAT, course));
        }
    }

    private void onAssessmentStarted(ActiveAssessment assessment) {
        // clear content before adding new
        generatedMenu.getRatingGroupContainer().removeAll();

        // add all rating groups
        assessment.getGradingConfig().getRatingGroups().stream()
                // only add assessment group if it is non-empty
                .filter(ratingGroup -> !ratingGroup.getMistakeTypes().isEmpty())
                .forEach(ratingGroup -> {

                    // calculate grid size
                    int colsPerRatingGroup = ArtemisSettingsState.getInstance().getColumnsPerRatingGroup();
                    int numRows = ratingGroup.getMistakeTypes().size() / colsPerRatingGroup;

                    // create a panel of appropriate size for each rating group
                    JPanel ratingCroupContainer = new JPanel(new GridLayout(numRows + 1, colsPerRatingGroup));

                    ratingCroupContainer.setBorder(BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(JBColor.LIGHT_GRAY),
                            String.format(
                                    "%s [%.2f of %.2f]",
                                    ratingGroup.getDisplayName().translateTo(LOCALE),
                                    ratingGroup.getMinPenalty(),
                                    ratingGroup.getMaxPenalty())));

                    // add buttons to rating group
                    ratingGroup.getMistakeTypes().stream()
                            // sort buttons alphabetically
                            // TODO: for some reason this is broken
                            .sorted(Comparator.comparing(
                                    mistake -> mistake.getButtonText().translateTo(LOCALE)))
                            .forEach(mistakeType -> {
                                // create button, add listener and add it to the container
                                JButton assessmentButton =
                                        new JButton(mistakeType.getButtonText().translateTo(LOCALE));
                                assessmentButton.addActionListener(e -> {
                                    // add annotation to the current caret position
                                    PluginState.getInstance()
                                            .getActiveAssessment()
                                            .orElseThrow()
                                            .addAnnotationAtCaret(mistakeType, false);
                                });
                                ratingCroupContainer.add(assessmentButton);
                            });

                    generatedMenu.getRatingGroupContainer().add(ratingCroupContainer);
                });
    }
}
