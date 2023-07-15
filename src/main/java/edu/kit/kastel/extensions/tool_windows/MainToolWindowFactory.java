package edu.kit.kastel.extensions.tool_windows;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import edu.kit.kastel.extensions.guis.AssesmentViewContent;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.api.artemis.Course;
import edu.kit.kastel.sdq.artemis4j.api.artemis.Exercise;
import edu.kit.kastel.sdq.artemis4j.api.artemis.exam.Exam;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.wrappers.Displayable;
import edu.kit.kastel.wrappers.DisplayableCourse;
import edu.kit.kastel.wrappers.DisplayableExam;
import edu.kit.kastel.wrappers.DisplayableExercise;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.GridLayout;
import java.util.List;
import java.util.Objects;

public class MainToolWindowFactory implements ToolWindowFactory {

  private static final String EXAMS_FETCH_ERROR_FORMAT = "Unable to fetch Exams for course %s.";
  private static final String EXERCISES_FETCH_ERROR_FORMAT = "Unable to fetch exercises for course %s.";

  //set up automated GUI and generate necessary bindings
  private final JPanel contentPanel = new JPanel();
  private final AssesmentViewContent generatedMenu = new AssesmentViewContent();
  private final TextFieldWithBrowseButton gradingConfigInput = generatedMenu.getGradingConfigPathInput();
  private final TextFieldWithBrowseButton autograderConfigInput = generatedMenu.getAutograderConfigPathInput();
  private final ComboBox<Displayable<Course>> coursesComboBox = generatedMenu.getCoursesDropdown();
  private final ComboBox<Displayable<Exam>> examsComboBox = generatedMenu.getExamsDropdown();
  private final ComboBox<Displayable<Exercise>> exerciseComboBox = generatedMenu.getExercisesDropdown();

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    contentPanel.setLayout(new GridLayout());

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

    toolWindow.getContentManager().addContent(content);
  }

  private void addListeners() {
    gradingConfigInput.addBrowseFolderListener(
            new TextBrowseFolderListener(new FileChooserDescriptor(
                    true,
                    false,
                    false,
                    false,
                    false,
                    false)
            ));
    autograderConfigInput.addBrowseFolderListener(new TextBrowseFolderListener(new FileChooserDescriptor(
            true,
            false,
            false,
            false,
            false,
            false)
    ));
  }

  private void populateDropdowns() throws ArtemisClientException {
    //add all courses to the courses dropdown
    List<Course> courses = ArtemisUtils.getArtemisClientInstance().getCourseArtemisClient().getCoursesForAssessment();
    courses.forEach(course -> coursesComboBox.addItem(new DisplayableCourse(course)));

    //populate exam and exercise dropdown if a course is selected
    coursesComboBox.addItemListener(itemEvent -> {
      Course selectedCourse = ((DisplayableCourse) itemEvent.getItem()).getWrappedValue();
      populateExamDropdown(selectedCourse);
      populateExercisesDropdown(selectedCourse);
    });

    //populate the dropdowns once because on load event listener is not triggered
    Course initial = ((DisplayableCourse) Objects.requireNonNull(coursesComboBox.getSelectedItem())).getWrappedValue();
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
    } catch (ArtemisClientException _e) {
      ArtemisUtils.displayGenericErrorBalloon(String.format(EXAMS_FETCH_ERROR_FORMAT, course));
    }
  }

  private void populateExercisesDropdown(@NotNull Course course) {
    exerciseComboBox.removeAllItems();

    try {
      course.getExercises().forEach(exercise -> exerciseComboBox.addItem(new DisplayableExercise(exercise)));
    } catch (ArtemisClientException _e) {
      ArtemisUtils.displayGenericErrorBalloon(String.format(EXERCISES_FETCH_ERROR_FORMAT, course));
    }
  }
}
