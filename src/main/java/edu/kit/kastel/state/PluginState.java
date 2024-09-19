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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.ui.jcef.JBCefApp;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.login.CefUtils;
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.client.ArtemisInstance;
import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnection;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.Course;
import edu.kit.kastel.sdq.artemis4j.grading.Exam;
import edu.kit.kastel.sdq.artemis4j.grading.MoreRecentSubmissionException;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.metajson.AnnotationMappingException;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.InvalidGradingConfigException;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.EditorUtil;

public class PluginState {
    private static final Logger LOG = Logger.getInstance(PluginState.class);

    private static PluginState pluginState;

    private final List<Consumer<Optional<ArtemisConnection>>> connectedListeners = new ArrayList<>();
    private final List<Consumer<ActiveAssessment>> assessmentStartedListeners = new ArrayList<>();
    private final List<Runnable> assessmentClosedListeners = new ArrayList<>();

    private ArtemisConnection connection;
    private Course activeCourse;
    private Exam activeExam;
    private ProgrammingExercise activeExercise;

    private ActiveAssessment activeAssessment;

    public static PluginState getInstance() {
        if (pluginState == null) {
            pluginState = new PluginState();
        }
        return pluginState;
    }

    public void connect() {
        this.resetState();

        var settings = ArtemisSettingsState.getInstance();

        String url = settings.getArtemisInstanceUrl();
        if (!ArtemisUtils.doesUrlExist(url)) {
            ArtemisUtils.displayGenericErrorBalloon(
                    "Artemis URL not reachable",
                    "The Artemis URL is not valid, or you do not have a working internet connection.");
            this.notifyConnectedListeners();
            return;
        }

        var instance = new ArtemisInstance(settings.getArtemisInstanceUrl());

        CompletableFuture<ArtemisConnection> connectionFuture;
        if (settings.isUseTokenLogin()) {
            connectionFuture = retrieveJWT().thenApplyAsync(token -> ArtemisConnection.fromToken(instance, token));
        } else {
            connectionFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return ArtemisConnection.connectWithUsernamePassword(
                            instance, settings.getUsername(), settings.getArtemisPassword());
                } catch (ArtemisClientException e) {
                    throw new CompletionException(e);
                }
            });
        }

        connectionFuture
                .thenAcceptAsync(connection -> {
                    this.connection = connection;
                    try {
                        this.verifyLogin();
                        this.notifyConnectedListeners();
                    } catch (ArtemisClientException e) {
                        throw new CompletionException(e);
                    }
                })
                .exceptionallyAsync(e -> {
                    LOG.error(e);
                    ArtemisUtils.displayGenericErrorBalloon("Artemis Login failed", e.getMessage());
                    this.connection = null;
                    this.notifyConnectedListeners();
                    return null;
                });
    }

    public boolean isConnected() {
        return connection != null;
    }

    public void registerConnectedListener(Consumer<Optional<ArtemisConnection>> listener) {
        this.connectedListeners.add(listener);
        listener.accept(Optional.ofNullable(this.connection));
    }

    public boolean isAssessing() {
        return activeAssessment != null;
    }

    public void startNextAssessment(int correctionRound) {
        if (activeAssessment != null) {
            ArtemisUtils.displayFinishAssessmentFirstBalloon();
            return;
        }

        if (activeCourse == null || activeExercise == null) {
            ArtemisUtils.displayGenericErrorBalloon("Could not start assessment", "No course selected");
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

                if (this.activeAssessment.getAssessment().getAnnotations().isEmpty()) {
                    this.activeAssessment.runAutograder();
                } else {
                    ArtemisUtils.displayGenericInfoBalloon(
                            "Skipping Autograder", "The submission already has annotations. Skipping the Autograder.");
                }

                ArtemisUtils.displayGenericInfoBalloon(
                        "Assessment started",
                        "You can now grade the submission. Please make sure that are familiar with all grading guidelines.");
            } else {
                ArtemisUtils.displayGenericInfoBalloon(
                        "Could not start assessment",
                        "There are no more submissions to assess. Thanks for your work :)");
            }
        } catch (ArtemisNetworkException e) {
            LOG.error(e);
            ArtemisUtils.displayNetworkErrorBalloon("Could not lock assessment", e);
        } catch (AnnotationMappingException e) {
            LOG.error(e);
            ArtemisUtils.displayGenericErrorBalloon(
                    "Could not parse assessment",
                    "Could not parse previous assessment. This is a serious bug; please contact the Übungsleitung!");
        }
    }

    public void saveAssessment() {
        if (activeAssessment == null) {
            ArtemisUtils.displayNoAssessmentBalloon();
            return;
        }

        try {
            activeAssessment.getAssessment().save();
            ArtemisUtils.displayGenericInfoBalloon("Assessment saved", "The assessment has been saved.");
        } catch (ArtemisNetworkException e) {
            LOG.error(e);
            ArtemisUtils.displayNetworkErrorBalloon("Could not save assessment", e);
        } catch (AnnotationMappingException e) {
            LOG.error(e);
            ArtemisUtils.displayGenericErrorBalloon(
                    "Could not save assessment",
                    "Failed to serialize the assessment. This is a serious bug; please contact the Übungsleitung!");
        }
    }

    public void submitAssessment() {
        if (activeAssessment == null) {
            ArtemisUtils.displayNoAssessmentBalloon();
            return;
        }

        try {
            activeAssessment.getAssessment().submit();
            this.cleanupAssessment();
        } catch (ArtemisNetworkException e) {
            LOG.error(e);
            ArtemisUtils.displayNetworkErrorBalloon("Could not submit assessment", e);
        } catch (AnnotationMappingException e) {
            LOG.error(e);
            ArtemisUtils.displayGenericErrorBalloon(
                    "Could not submit assessment",
                    "Failed to serialize the assessment. This is a serious bug; please contact the Übungsleitung!");
        }
    }

    public void cancelAssessment() {
        if (activeAssessment == null) {
            ArtemisUtils.displayNoAssessmentBalloon();
            return;
        }

        try {
            activeAssessment.getAssessment().cancel();
            this.cleanupAssessment();
        } catch (ArtemisNetworkException e) {
            LOG.error(e);
            ArtemisUtils.displayNetworkErrorBalloon("Could not submit assessment", e);
        }
    }

    public void closeAssessment() {
        if (activeAssessment == null) {
            ArtemisUtils.displayNoAssessmentBalloon();
            return;
        }

        this.cleanupAssessment();
    }

    public void reopenAssessment(ProgrammingSubmission submission) {
        if (activeAssessment != null) {
            ArtemisUtils.displayFinishAssessmentFirstBalloon();
            return;
        }

        if (activeCourse == null || activeExercise == null) {
            ArtemisUtils.displayGenericErrorBalloon("Could not reopen assessment", "No course or exercise selected");
            return;
        }

        var gradingConfig = createGradingConfig();
        if (gradingConfig.isEmpty()) {
            return;
        }

        try {
            var assessment = submission.tryLock(gradingConfig.get());
            if (assessment.isPresent()) {
                this.initializeAssessment(assessment.get());
            } else {
                ArtemisUtils.displayGenericErrorBalloon(
                        "Failed to reopen assessment", "Most likely, your lock has been taken by someone else.");
            }
        } catch (ArtemisNetworkException e) {
            LOG.error(e);
            ArtemisUtils.displayNetworkErrorBalloon("Could not lock assessment", e);
        } catch (AnnotationMappingException e) {
            LOG.error(e);
            ArtemisUtils.displayGenericErrorBalloon(
                    "Could not parse assessment",
                    "Could not parse previous assessment. This is a serious bug; please contact the Übungsleitung!");
        } catch (MoreRecentSubmissionException e) {
            LOG.error(e);
            ArtemisUtils.displayGenericErrorBalloon(
                    "Could not reopen assessment", "The student has submitted a newer version of his code.");
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
        if (this.isAssessing()) {
            listener.accept(activeAssessment);
        }
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
    }

    private CompletableFuture<String> retrieveJWT() {
        var settings = ArtemisSettingsState.getInstance();

        String previousJwt = settings.getArtemisAuthJWT();
        if (previousJwt != null && !previousJwt.isBlank()) {
            return CompletableFuture.completedFuture(previousJwt);
        }

        if (!JBCefApp.isSupported()) {
            return CompletableFuture.failedFuture(
                    new CompletionException(new IllegalStateException("JCEF unavailable")));
        }

        var jwtCookieFuture = CefUtils.jcefBrowserLogin();
        return jwtCookieFuture.thenApplyAsync(cookie -> {
            settings.setArtemisAuthJWT(cookie.getValue());
            settings.setJwtExpiry(cookie.getExpires());
            return cookie.getValue();
        });
    }

    private void verifyLogin() throws ArtemisClientException {
        // This triggers a request and forces a connection error if the token is invalid
        this.connection.getAssessor();
    }

    private void notifyConnectedListeners() {
        this.connectedListeners.forEach(l -> l.accept(Optional.ofNullable(this.connection)));
    }

    private void initializeAssessment(Assessment assessment) {
        try {
            // Cleanup first, in case there are files left from a previous assessment
            this.cleanupProjectDirectory();

            // Clone the new submission
            var clonedSubmission =
                    assessment.getSubmission().cloneViaVCSTokenInto(EditorUtil.getProjectRootDirectory(), null);

            // Refresh all files, so that they are up-to-date for the maven update
            EditorUtil.forceFilesSync(() -> {
                // Force IntelliJ to update the Maven project
                EditorUtil.getMavenManager().forceUpdateAllProjectsOrFindAllAvailablePomFiles();
            });

            this.activeAssessment = new ActiveAssessment(assessment, clonedSubmission);
            this.assessmentStartedListeners.forEach(listener -> listener.accept(activeAssessment));

        } catch (IOException | ArtemisClientException e) {
            LOG.error(e);
            ArtemisUtils.displayGenericErrorBalloon("Error cloning submission", e.getMessage());

            // Cancel the assessment to prevent spurious locks
            try {
                assessment.cancel();
            } catch (ArtemisNetworkException ex) {
                LOG.error(ex);
                ArtemisUtils.displayGenericErrorBalloon("Failed to free the assessment lock", ex.getMessage());
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
        EditorUtil.forceFilesSync(() -> {
            EditorUtil.getVcsManager().setDirectoryMappings(List.of());
            EditorUtil.getVcsManager().fireDirectoryMappingsChanged();
        });

        this.assessmentClosedListeners.forEach(Runnable::run);
    }

    private Optional<GradingConfig> createGradingConfig() {
        var gradingConfigPath = ArtemisSettingsState.getInstance().getSelectedGradingConfigPath();
        if (gradingConfigPath == null) {
            ArtemisUtils.displayGenericErrorBalloon("No grading config", "Please select a grading config");
            return Optional.empty();
        }

        try {
            return Optional.of(
                    GradingConfig.readFromString(Files.readString(Path.of(gradingConfigPath)), activeExercise));
        } catch (IOException | InvalidGradingConfigException e) {
            LOG.error(e);
            ArtemisUtils.displayGenericErrorBalloon("Invalid grading config", e.getMessage());
            return Optional.empty();
        }
    }

    private void cleanupProjectDirectory() {
        // Close all open editors
        var editorManager = FileEditorManager.getInstance(EditorUtil.getActiveProject());
        for (var editor : editorManager.getAllEditors()) {
            editorManager.closeFile(editor.getFile());
        }

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
            LOG.error(e);
            ArtemisUtils.displayGenericErrorBalloon("Error cleaning up project directory", e.getMessage());
        }
    }
}
