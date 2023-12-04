package edu.kit.kastel.listeners;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageDialogBuilder;
import edu.kit.kastel.extensions.guis.AssessmentViewContent;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.api.artemis.Exercise;
import edu.kit.kastel.sdq.artemis4j.api.artemis.ExerciseStats;
import edu.kit.kastel.sdq.artemis4j.api.artemis.assessment.LockResult;
import edu.kit.kastel.sdq.artemis4j.api.artemis.assessment.Submission;
import edu.kit.kastel.sdq.artemis4j.api.client.IAssessmentArtemisClient;
import edu.kit.kastel.state.AssessmentModeHandler;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.AssessmentUtils;
import edu.kit.kastel.wrappers.Displayable;
import edu.kit.kastel.wrappers.ExtendedLockResult;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

/**
 * Listener that gets called when the first grading round is started.
 */
public class StartAssesment1Listener implements ActionListener {

  private static final String NO_CONFIG_SELECTED_MSG =
          "Please select the appropriate grading config";

  private static final String SELECT_EXERCISE_MSG =
          "Please select an exercise to start grading";

  private static final String ERROR_NEXT_ASSESSMENT_FORMATTER =
          "Error requestung a new submission lock: %s "
                  + "(this most likely means there are no more submissions to be graded)";

  private static final String GIT_ERROR_FORMATTER =
          "Error cloning submission  \"%s\". Are you authenticated?";

  private static final String ERROR_DELETE_REPO_DIR =
          "Error deleting existing submission directory.";

  private static final String LOOSE_ASSESSMENT_MSG =
          "You already have an assessment loaded. Loading a new assessment"
                  + " will cause you to loose all unsaved gradings! Load new assessment anyway?";
  private final AssessmentViewContent gui;

  private final IAssessmentArtemisClient assessmentClient = ArtemisUtils
          .getArtemisClientInstance()
          .getAssessmentArtemisClient();

  public StartAssesment1Listener(AssessmentViewContent gui) {
    this.gui = gui;
  }

  @Override
  public void actionPerformed(ActionEvent actionEvent) {

    //check if any config is selected. If wrong config is selected Button will be unclickable
    if (gui.getGradingConfigPathInput().getText().isBlank()) {
      ArtemisUtils.displayGenericErrorBalloon(NO_CONFIG_SELECTED_MSG);
      return;
    }

    if (gui.getExercisesDropdown().getSelectedItem() == null) {
      ArtemisUtils.displayGenericErrorBalloon(SELECT_EXERCISE_MSG);
      return;
    }

    //check if an assessment is already loaded
    if (AssessmentModeHandler.getInstance().isInAssesmentMode()
            && !MessageDialogBuilder.yesNo(
                    "Unsaved assessment",
                    LOOSE_ASSESSMENT_MSG)
            .guessWindowAndAsk()) {
      return;
    }

    //get the assessment and try to obtain a lock 
    Exercise selectedExercise = ((Displayable<Exercise>) gui.getExercisesDropdown().getSelectedItem())
            .getWrappedValue();

    Optional<LockResult> assessmentLockWrapper;
    try {

      ExerciseStats stats = assessmentClient.getStats(selectedExercise);

      //TODO: Calculate if assessments are still to be graded or not

      assessmentLockWrapper = assessmentClient.startNextAssessment(selectedExercise, 0);

    } catch (ArtemisClientException e) {
      ArtemisUtils.displayGenericErrorBalloon(
              String.format(ERROR_NEXT_ASSESSMENT_FORMATTER, e.getMessage())
      );
      return;
    }

    if (assessmentLockWrapper.isEmpty()) {
      ArtemisUtils.displayGenericErrorBalloon(
              //TODO: correct error message
              String.format(ERROR_NEXT_ASSESSMENT_FORMATTER, "")
      );
      return;
    }

    //process the submission iff a lock was obtained
    LockResult assessmentLock = assessmentLockWrapper.get();

    Optional<String> repoUrlWrapper =
            getRepoUrlFromAssessmentLock(assessmentLock, selectedExercise);

    //obtain student name or use submission number
    String repoIdentifier;
    try {
      repoIdentifier = ArtemisUtils
              .getArtemisClientInstance()
              .getSubmissionArtemisClient()
              .getSubmissionById(selectedExercise, assessmentLock.getSubmissionId())
              .getParticipantIdentifier();
    } catch (ArtemisClientException e) {
      repoIdentifier = Integer.toString(assessmentLock.getSubmissionId());
    }

    String repositoryName = String.format(
            "%s_%s", selectedExercise.getShortName(),
            repoIdentifier
    );

    repoUrlWrapper.ifPresent(repoUrl -> this.cloneSubmissionToTempdir(repoUrl, repositoryName));

    //set assessment mode
    AssessmentModeHandler.getInstance().enableAssessmentMode(new ExtendedLockResult(assessmentLock, selectedExercise));

    //update statistics
    ArtemisUtils.updateStats(selectedExercise, gui.getStatisticsContainer());

  }

  /**
   * Use an assessmentLock to get the repository URL of a submission.
   *
   * @param assessmentLock   the lock you obtained before to grade the Submission
   * @param selectedExercise The exercise this submission belongs to
   * @return Optional#empty if an error occurred, The wrapped string otherwise
   */
  private Optional<String> getRepoUrlFromAssessmentLock(
          @NotNull LockResult assessmentLock,
          @NotNull Exercise selectedExercise) {

    Optional<String> repoUrl = Optional.empty();
    try {
      //try to get the repository URL of the submission
      Submission toBeGraded = ArtemisUtils
              .getArtemisClientInstance()
              .getSubmissionArtemisClient()
              .getSubmissionById(selectedExercise, assessmentLock.getSubmissionId());
      repoUrl = Optional.of(toBeGraded.getRepositoryUrl());
    } catch (ArtemisClientException e) {
      //display Error Balloon on failure
      ArtemisUtils.displayGenericErrorBalloon(
              String.format(ERROR_NEXT_ASSESSMENT_FORMATTER, e.getMessage())
      );
    }

    return repoUrl;
  }

  private void cloneSubmissionToTempdir(@NotNull String repoUrl, @NotNull String repoName) {
    //construct repo path
    File submissionRepoDir = new File(
            String.valueOf(
                    Path.of(System.getProperty("java.io.tmpdir"), repoName)
            )
    );

    //delete repository folder if it exists
    if (submissionRepoDir.exists()) {
      try (Stream<Path> files = Files.walk(submissionRepoDir.toPath())) {
        files.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
      } catch (IOException e) {
        ArtemisUtils.displayGenericErrorBalloon(ERROR_DELETE_REPO_DIR);
      }

    }


    //try to clone the repository
    CloneCommand cloner = Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(submissionRepoDir)
            .setCredentialsProvider(
                    new UsernamePasswordCredentialsProvider(
                            ArtemisSettingsState.getInstance().getUsername(),
                            ArtemisSettingsState.getInstance().getArtemisPassword()
                    )
            );
    //generate notification because cloning is slow
    NotificationGroupManager.getInstance()
            .getNotificationGroup("IntelliGrade Notifications")
            .createNotification("Cloning repository...\n This might take a while.",
                    NotificationType.INFORMATION)
            .setTitle("Please wait")
            .notify(ProjectUtil.getActiveProject());

    try (Git ignored = cloner.call()) {
      //empty because of try with resource
    } catch (InvalidRemoteException | TransportException e) {
      ArtemisUtils.displayGenericErrorBalloon(String.format(GIT_ERROR_FORMATTER, e.getMessage()));
      return;
    } catch (GitAPIException e) {
      LoggerFactory.getLogger(StartAssesment1Listener.class).error(e.toString());
      return;
    }

    //document selected exercise
    int selectedIdx = this.gui.getExercisesDropdown().getSelectedIndex();

    //open project
    ProjectUtil.openOrImport(submissionRepoDir.toPath(), ProjectUtil.getActiveProject(), false);

    //reset listeners because they get registered when a new project is opened
    AssessmentUtils.resetAssessmentListeners();

    //generate notification because cloning is slow
    NotificationGroupManager.getInstance()
            .getNotificationGroup("IntelliGrade Notifications")
            .createNotification(String.format("Submission %s cloned successfully.", repoName),
                    NotificationType.INFORMATION)
            .setTitle("Finished")
            .notify(ProjectUtil.getActiveProject());

    //set correct exercise in panel (new IDE instance resets this)
    this.gui.getExercisesDropdown().setSelectedIndex(selectedIdx);
  }
}


