/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.state;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefCookie;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.login.CefUtils;
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.client.ArtemisInstance;
import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnection;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.Course;
import edu.kit.kastel.sdq.artemis4j.grading.Exam;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.InvalidGradingConfigException;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.EditorUtil;

public class PluginState {
    private static final String LOOSE_ASSESSMENT_MSG = "You already have an assessment loaded. Loading a new assessment"
            + " will cause you to loose all unsaved gradings! Load new assessment anyway?";

    private static final Logger log = Logger.getInstance(PluginState.class);
    private static PluginState pluginState;

    private final List<Runnable> stateChangeListeners = new ArrayList<>();
    private final List<Consumer<ActiveAssessment>> assessmentStartedListeners = new ArrayList<>();
    private final List<Runnable> assessmentClosedListeners = new ArrayList<>();

    private ArtemisConnection connection;
    private Course activeCourse;
    private Exam activeExam;
    private ProgrammingExercise activeExercise;
    private int correctionRound;

    private ActiveAssessment activeAssessment;

    public static PluginState getInstance() {
        if (pluginState == null) {
            pluginState = new PluginState();
        }
        return pluginState;
    }

    public boolean connect() {
        try {
            this.resetState();

            var settings = ArtemisSettingsState.getInstance();
            var instance = new ArtemisInstance(settings.getArtemisInstanceUrl());

            if (settings.getUsername() == null
                    || settings.getUsername().isBlank()
                    || settings.getArtemisPassword() == null
                    || settings.getArtemisPassword().isBlank()) {
                if (settings.getArtemisAuthJWT() == null
                        || settings.getArtemisAuthJWT().isBlank()) {
                    retrieveNewJWT();
                }
                this.connection = ArtemisConnection.fromToken(instance, settings.getArtemisAuthJWT());
            } else {
                this.connection = ArtemisConnection.connectWithUsernamePassword(
                        instance, settings.getUsername(), settings.getArtemisPassword());
            }

            return true;
        } catch (ArtemisClientException ex) {
            ArtemisUtils.displayGenericErrorBalloon("Error connecting to Artemis: %s".formatted(ex.getMessage()));
            return false;
        }
    }

    public boolean isConnected() {
        return connection != null;
    }

    public boolean isAssessing() {
        return activeAssessment != null;
    }

    public void startNextAssessment() {
        if (activeAssessment != null
                && !MessageDialogBuilder.yesNo("Unsaved assessment", LOOSE_ASSESSMENT_MSG)
                        .guessWindowAndAsk()) {
            ArtemisUtils.displayGenericErrorBalloon("Please finish the current assessment first");
            return;
        }

        if (activeCourse == null || activeExercise == null) {
            ArtemisUtils.displayGenericErrorBalloon(
                    "Please select a course and exercise: " + activeCourse + " " + activeExercise);
            return;
        }

        var gradingConfig = createGradingConfig();
        if (gradingConfig.isEmpty()) {
            return;
        }

        try {
            var nextAssessment = activeExercise.tryLockNextSubmission(correctionRound, gradingConfig.get());
            if (nextAssessment.isPresent()) {
                this.initializeAssessment(nextAssessment.get());
            } else {
                ArtemisUtils.displayGenericErrorBalloon(
                        "There are no more submissions to assess. Thanks for your work :)");
            }
        } catch (ArtemisClientException e) {
            ArtemisUtils.displayGenericErrorBalloon("Error starting assessment: %s".formatted(e.getMessage()));
        }
    }

    public void submitAssessment() {
        if (activeAssessment == null) {
            ArtemisUtils.displayGenericErrorBalloon("No active assessment");
            return;
        }

        try {
            activeAssessment.getAssessment().submit();
            this.cleanupAssessment();
        } catch (ArtemisClientException e) {
            ArtemisUtils.displayGenericErrorBalloon("Error submitting assessment: %s".formatted(e.getMessage()));
        }
    }

    public Optional<ArtemisConnection> getConnection() {
        return Optional.ofNullable(connection);
    }

    public Optional<Course> getActiveCourse() {
        return Optional.ofNullable(activeCourse);
    }

    public void setActiveCourse(Course course) {
        this.activeCourse = course;
    }

    public Optional<Exam> getActiveExam() {
        return Optional.ofNullable(activeExam);
    }

    public void setActiveExam(Exam exam) {
        this.activeExam = exam;
    }

    public Optional<ProgrammingExercise> getActiveExercise() {
        return Optional.ofNullable(activeExercise);
    }

    public void setActiveExercise(ProgrammingExercise exercise) {
        this.activeExercise = exercise;
    }

    public Optional<ActiveAssessment> getActiveAssessment() {
        return Optional.ofNullable(activeAssessment);
    }

    public void registerAssessmentStartedListener(Consumer<ActiveAssessment> listener) {
        this.assessmentStartedListeners.add(listener);
    }

    public void registerAssessmentClosedListener(Runnable listener) {
        this.assessmentClosedListeners.add(listener);
    }

    private void resetState() {
        connection = null;
        activeCourse = null;
        activeExam = null;
        activeExercise = null;
        activeAssessment = null;
        correctionRound = 0;
    }

    private void retrieveNewJWT() throws ArtemisClientException {
        var settings = ArtemisSettingsState.getInstance();

        try {
            if (!JBCefApp.isSupported()) {
                throw new ArtemisClientException(
                        "Embedded browser unavailable. Please log in using username and password.");
            }

            JBCefCookie jwtCookie = CefUtils.jcefBrowserLogin().get();
            settings.setArtemisAuthJWT(jwtCookie.getValue());
            settings.setJwtExpiry(jwtCookie.getExpires());
        } catch (ExecutionException e) {
            throw new ArtemisClientException("Interrupted while attempting to get login token", e);
        } catch (InterruptedException e) {
            throw new ArtemisClientException("Error retrieving JWT", e);
        }
    }

    private void initializeAssessment(Assessment assessment) {
        try {
            // generate notification because cloning is slow
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("IntelliGrade Notifications")
                    .createNotification(
                            "Cloning repository...\n This might take a while.", NotificationType.INFORMATION)
                    .setTitle("Please wait")
                    .notify(ProjectUtil.getActiveProject());

            // Cleanup first, in case there are files left from a previous assessment
            this.cleanupProjectDirectory();

            // Clone the new submission
            var clonedSubmission =
                    assessment.getSubmission().cloneViaVCSTokenInto(EditorUtil.getProjectRootDirectory(), null);

            // Synchronously refresh all files, so that they are up-to-date for the maven update
            EditorUtil.forceFilesSync();

            // Force IntelliJ to update the Maven project
            EditorUtil.getMavenManager().forceUpdateAllProjectsOrFindAllAvailablePomFiles();

            this.activeAssessment = new ActiveAssessment(assessment, clonedSubmission);
            this.assessmentStartedListeners.forEach(listener -> listener.accept(activeAssessment));
        } catch (IOException | ArtemisClientException e) {
            log.error("Error cloning submission", e);
            ArtemisUtils.displayGenericErrorBalloon("Error cloning submission: %s".formatted(e.getMessage()));

            try {
                assessment.cancel();
            } catch (ArtemisClientException ex) {
                ArtemisUtils.displayGenericErrorBalloon(
                        "Failed to free the assessment lock: %s".formatted(ex.getMessage()));
            }
        }
    }

    private void cleanupAssessment() {
        this.activeAssessment = null;

        // Do not close the ClonedProgrammingSubmission, since this would try to delete the workspace file
        // Instead, we delete the project directory manually
        this.cleanupProjectDirectory();

        // Tell IntelliJ's VCS manager that the Git repo is gone
        // This prevents an annoying popup that warns about a missing Git root
        EditorUtil.forceFilesSync();
        EditorUtil.getVcsManager().setDirectoryMappings(List.of());
        EditorUtil.getVcsManager().fireDirectoryMappingsChanged();

        this.assessmentClosedListeners.forEach(Runnable::run);
    }

    private Optional<GradingConfig> createGradingConfig() {
        var gradingConfigPath = ArtemisSettingsState.getInstance().getSelectedGradingConfigPath();
        if (gradingConfigPath == null) {
            ArtemisUtils.displayGenericErrorBalloon("Please select a grading config");
            return Optional.empty();
        }

        try {
            return Optional.of(
                    GradingConfig.readFromString(Files.readString(Path.of(gradingConfigPath)), activeExercise));
        } catch (IOException | InvalidGradingConfigException e) {
            ArtemisUtils.displayGenericErrorBalloon("Invalid grading config: %s".formatted(e.getMessage()));
            return Optional.empty();
        }
    }

    private void cleanupProjectDirectory() {
        var rootPath = EditorUtil.getProjectRootDirectory();
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dir.equals(rootPath)) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Error cleaning up project directory", e);
            ArtemisUtils.displayGenericErrorBalloon(
                    "Error cleaning up project directory: %s".formatted(e.getMessage()));
        }
    }
}
