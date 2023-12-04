package edu.kit.kastel.extensions.tool_windows;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import edu.kit.kastel.extensions.guis.AssessmentViewContent;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.listeners.ExerciseSelectedListener;
import edu.kit.kastel.listeners.GradingConfigSelectedListener;
import edu.kit.kastel.listeners.OnSaveAssessmentBtnClick;
import edu.kit.kastel.listeners.OnSubmitAssessmentBtnClick;
import edu.kit.kastel.listeners.StartAssesment1Listener;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.api.artemis.Course;
import edu.kit.kastel.sdq.artemis4j.api.artemis.Exercise;
import edu.kit.kastel.sdq.artemis4j.api.artemis.exam.Exam;
import edu.kit.kastel.state.AssessmentModeHandler;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.wrappers.Displayable;
import edu.kit.kastel.wrappers.DisplayableCourse;
import edu.kit.kastel.wrappers.DisplayableExam;
import edu.kit.kastel.wrappers.DisplayableExercise;
import java.awt.GridLayout;
import java.util.List;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * This class handles all logic for the main grading UI.
 * It does not handle any other logic, that should be factored out.
 */
public class MainToolWindowFactory implements ToolWindowFactory {

  private static final String EXAMS_FETCH_ERROR_FORMAT =
          "Unable to fetch Exams for course %s.";
  private static final String EXERCISES_FETCH_ERROR_FORMAT =
          "Unable to fetch exercises for course %s.";

  //set up automated GUI and generate necessary bindings
  private final JPanel contentPanel = new JPanel();
  private final AssessmentViewContent generatedMenu = new AssessmentViewContent();
  private final TextFieldWithBrowseButton gradingConfigInput =
          generatedMenu.getGradingConfigPathInput();
  private final TextFieldWithBrowseButton autograderConfigInput =
          generatedMenu.getAutograderConfigPathInput();
  private final ComboBox<Displayable<Course>> coursesComboBox = generatedMenu.getCoursesDropdown();
  private final ComboBox<Displayable<Exam>> examsComboBox = generatedMenu.getExamsDropdown();
  private final ComboBox<Displayable<Exercise>> exerciseComboBox =
          generatedMenu.getExercisesDropdown();

  private final JButton startAssessment1Btn = generatedMenu.getBtnGradingRound1();

  private final JButton saveAssessmentBtn = generatedMenu.getBtnSaveAssessment();

  private final JButton submitAssessmentBtn = generatedMenu.getSubmitAssesmentBtn();

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    contentPanel.setLayout(new GridLayout());

    //give up if logging in to Artemis failed
    if (!ArtemisUtils.getArtemisClientInstance().isReady()) {
      return;
    }

    //add content to menu panel
    contentPanel.add(generatedMenu);
    Content content = ContentFactory.getInstance().createContent(
            this.contentPanel,
            null,
            false
    );

    addListeners();
    try {
      populateDropdowns();
    } catch (ArtemisClientException exc) {
      ArtemisUtils.displayLoginErrorBalloon("Error retrieving courses!", null);
    }

    //connect label to AssessmentModeHandler
    AssessmentModeHandler.getInstance().connectIndicatorLabel(generatedMenu.getAssessmentModeLabel());

    toolWindow.getContentManager().addContent(content);
  }

  private void addListeners() {
    gradingConfigInput.addBrowseFolderListener(
            new TextBrowseFolderListener(FileChooserDescriptorFactory
                    .createSingleFileDescriptor("json")
            )
    );
    autograderConfigInput.addBrowseFolderListener(
            new TextBrowseFolderListener(FileChooserDescriptorFactory
                    .createSingleFileDescriptor("json")
            )
    );

    // why the heck would you add a listener for text change like this????
    gradingConfigInput
            .getTextField()
            .getDocument()
            .addDocumentListener(new GradingConfigSelectedListener(gradingConfigInput));

    //set config path saved in settings
    ArtemisSettingsState settings = ArtemisSettingsState.getInstance();
    gradingConfigInput.setText(settings.getSelectedGradingConfigPath());

    //parse config on exercise select
    exerciseComboBox.addItemListener(new ExerciseSelectedListener(generatedMenu));

    //add listener for Button that starts first grading round
    startAssessment1Btn.addActionListener(new StartAssesment1Listener(generatedMenu));

    //button that saves assessment
    saveAssessmentBtn.addActionListener(new OnSaveAssessmentBtnClick());

    //button that submits assessment
    submitAssessmentBtn.addActionListener(new OnSubmitAssessmentBtnClick(generatedMenu.getStatisticsContainer()));

  }

  private void populateDropdowns() throws ArtemisClientException {
    //add all courses to the courses dropdown
    List<Course> courses = ArtemisUtils
            .getArtemisClientInstance()
            .getCourseArtemisClient()
            .getCourses();
    courses.forEach(course -> coursesComboBox.addItem(new DisplayableCourse(course)));

    //populate exam and exercise dropdown if a course is selected
    coursesComboBox.addItemListener(itemEvent -> {
      Course selectedCourse = ((DisplayableCourse) itemEvent.getItem()).getWrappedValue();
      populateExamDropdown(selectedCourse);
      populateExercisesDropdown(selectedCourse);
    });

    //populate the dropdowns once because on load event listener is not triggered
    Course initial = ((DisplayableCourse) Objects.requireNonNull(coursesComboBox.getSelectedItem()))
            .getWrappedValue();
    populateExamDropdown(initial);
    populateExercisesDropdown(initial);
  }

  private void populateExamDropdown(@NotNull Course course) {
    examsComboBox.removeAllItems();
    //we usually do not want to select the exam. Whe thus create a null Item
    examsComboBox.addItem(new DisplayableExam(null));
    try {
      //try to add all exams to the dropdown or fail
      course.getExams().forEach(exam -> examsComboBox.addItem(new DisplayableExam(exam)));
    } catch (ArtemisClientException e) {
      ArtemisUtils.displayGenericErrorBalloon(String.format(EXAMS_FETCH_ERROR_FORMAT, course));
    }
  }

  private void populateExercisesDropdown(@NotNull Course course) {
    exerciseComboBox.removeAllItems();

    try {
      course.getExercises().forEach(exercise ->
              exerciseComboBox.addItem(new DisplayableExercise(exercise))
      );
    } catch (ArtemisClientException e) {
      ArtemisUtils.displayGenericErrorBalloon(String.format(EXERCISES_FETCH_ERROR_FORMAT, course));
    }
  }
}
