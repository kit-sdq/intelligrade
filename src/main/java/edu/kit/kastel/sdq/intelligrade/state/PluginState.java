/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.state;

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
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.ui.jcef.JBCefApp;
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.client.ArtemisInstance;
import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnection;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound;
import edu.kit.kastel.sdq.artemis4j.grading.MoreRecentSubmissionException;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.User;
import edu.kit.kastel.sdq.artemis4j.grading.metajson.AnnotationMappingException;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.InvalidGradingConfigException;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisCredentialsProvider;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.login.CefUtils;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

public class PluginState {
    private static final Logger LOG = Logger.getInstance(PluginState.class);

    private static PluginState pluginState;

    private final List<Consumer<ArtemisConnection>> connectedListeners = new ArrayList<>();
    private final List<Consumer<ActiveAssessment>> assessmentStartedListeners = new ArrayList<>();
    private final List<Runnable> assessmentClosedListeners = new ArrayList<>();

    private ArtemisConnection connection;
    private ProgrammingExercise activeExercise;

    private ActiveAssessment activeAssessment;

    public static PluginState getInstance() {
        if (pluginState == null) {
            pluginState = new PluginState();
        }
        return pluginState;
    }

    /**
     * Logs out while displaying a warning if an assessment is still running
     */
    public void logout() {
        boolean answer = true;
        // check if confirmation is necessary because assessment is running
        if (isAssessing()) {
            answer = MessageDialogBuilder.okCancel(
                            "Logging out while assessing!",
                            "Logging out while assessing will discard current changes. Continue?")
                    .guessWindowAndAsk();
        }
        // actually reset state
        if (answer) {
            this.resetState();

            // reset state in settings
            ArtemisCredentialsProvider.getInstance().setJwt(null);
            ArtemisCredentialsProvider.getInstance().setArtemisPassword(null);
            ArtemisSettingsState.getInstance().setJwtExpiry(null);

            // reset JBCef cookies iff available
            if (JBCefApp.isSupported()) {
                CefUtils.resetCookies();
            }
        }
        this.notifyConnectedListeners();
    }

    public void connect() {
        this.resetState();

        var settings = ArtemisSettingsState.getInstance();
        var credentials = ArtemisCredentialsProvider.getInstance();

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
                            instance, settings.getUsername(), credentials.getArtemisPassword());
                } catch (ArtemisClientException e) {
                    throw new CompletionException(e);
                }
            });
        }

        connectionFuture
                .thenAcceptAsync(newConnection -> {
                    this.connection = newConnection;
                    try {
                        this.verifyLogin();
                        this.notifyConnectedListeners();
                    } catch (ArtemisClientException e) {
                        throw new CompletionException(e);
                    }
                })
                .exceptionallyAsync(e -> {
                    LOG.warn(e);
                    ArtemisUtils.displayGenericErrorBalloon("Artemis Login failed", e.getMessage());
                    this.connection = null;
                    this.notifyConnectedListeners();
                    return null;
                });
    }

    public void registerConnectedListener(Consumer<ArtemisConnection> listener) {
        this.connectedListeners.add(listener);
        listener.accept(this.connection);
    }

    public boolean isAssessing() {
        return activeAssessment != null;
    }

    public void startNextAssessment(CorrectionRound correctionRound) {
        if (activeAssessment != null) {
            ArtemisUtils.displayFinishAssessmentFirstBalloon();
            return;
        }

        if (activeExercise == null) {
            ArtemisUtils.displayGenericErrorBalloon("Could not start assessment", "No course selected");
            return;
        }

        var gradingConfig = createGradingConfig();
        if (gradingConfig.isEmpty()) {
            return;
        }

        new StartAssessmentTask(correctionRound, gradingConfig.get()).queue();
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
            LOG.warn(e);
            ArtemisUtils.displayNetworkErrorBalloon("Could not save assessment", e);
        } catch (AnnotationMappingException e) {
            LOG.warn(e);
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
            LOG.warn(e);
            ArtemisUtils.displayNetworkErrorBalloon("Could not submit assessment", e);
        } catch (AnnotationMappingException e) {
            LOG.warn(e);
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
            LOG.warn(e);
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

        if (activeExercise == null) {
            ArtemisUtils.displayGenericErrorBalloon("Could not reopen assessment", "No exercise selected");
            return;
        }

        var gradingConfig = createGradingConfig();
        if (gradingConfig.isEmpty()) {
            return;
        }

        new ReopenAssessmentTask(submission, gradingConfig.get()).queue();
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

    public User getAssessor() throws ArtemisNetworkException {
        return connection.getAssessor();
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
        activeExercise = null;
        activeAssessment = null;
    }

    private CompletableFuture<String> retrieveJWT() {
        var settings = ArtemisSettingsState.getInstance();
        var credentials = ArtemisCredentialsProvider.getInstance();

        return CompletableFuture.supplyAsync(() -> {
            String previousJwt = credentials.getJwt();
            if (previousJwt != null && !previousJwt.isBlank()) {
                return previousJwt;
            }

            if (!JBCefApp.isSupported()) {
                throw new CompletionException(new IllegalStateException("JCEF unavailable"));
            }

            try {
                var cookie = CefUtils.jcefBrowserLogin().get();
                credentials.setJwt(cookie.getValue());
                settings.setJwtExpiry(cookie.getExpires());
                return cookie.getValue();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CompletionException(ex);
            } catch (ExecutionException e) {
                throw new CompletionException(e);
            }
        });
    }

    private void verifyLogin() throws ArtemisClientException {
        // This triggers a request and forces a connection error if the token is invalid
        this.connection.getAssessor();
    }

    private void notifyConnectedListeners() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Consumer<ArtemisConnection> l : this.connectedListeners) {
                l.accept(this.connection);
            }
        });
    }

    private boolean initializeAssessment(Assessment assessment) {
        try {
            // Cleanup first, in case there are files left from a previous assessment
            this.cleanupProjectDirectory();

            // Clone the new submission
            var clonedSubmission =
                    switch (ArtemisSettingsState.getInstance().getVcsAccessOption()) {
                        case SSH -> {
                            // We need to switch the classloader here (same as
                            // https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html#using-serviceloader)
                            // Somewhere deep in the auth libs, an instanceof check is performed, which returns false in
                            // some cases where the same class was loaded with two different class loaders (the plugin
                            // and the platform ones)
                            Thread currentThread = Thread.currentThread();
                            ClassLoader originalClassLoader = currentThread.getContextClassLoader();
                            ClassLoader pluginClassLoader = this.getClass().getClassLoader();
                            try {
                                currentThread.setContextClassLoader(pluginClassLoader);
                                yield assessment
                                        .getSubmission()
                                        .cloneViaSSHInto(IntellijUtil.getProjectRootDirectory());
                            } finally {
                                currentThread.setContextClassLoader(originalClassLoader);
                            }
                        }
                        case TOKEN -> assessment
                                .getSubmission()
                                .cloneViaVCSTokenInto(IntellijUtil.getProjectRootDirectory(), null);
                    };

            // Refresh all files, so that they are up-to-date for the maven update
            IntellijUtil.forceFilesSync(() -> {
                // Force IntelliJ to update the Maven project
                IntellijUtil.getMavenManager().forceUpdateAllProjectsOrFindAllAvailablePomFiles();
            });

            this.activeAssessment = new ActiveAssessment(assessment, clonedSubmission);
            for (Consumer<ActiveAssessment> listener : this.assessmentStartedListeners) {
                listener.accept(activeAssessment);
            }

            return true;
        } catch (ArtemisClientException e) {
            LOG.warn(e);
            ArtemisUtils.displayGenericErrorBalloon("Error cloning submission", e.getMessage());

            // Cancel the assessment to prevent spurious locks
            try {
                assessment.cancel();
            } catch (ArtemisNetworkException ex) {
                LOG.warn(ex);
                ArtemisUtils.displayGenericErrorBalloon("Failed to free the assessment lock", ex.getMessage());
            }

            return false;
        }
    }

    private void cleanupAssessment() {
        this.activeAssessment = null;

        // Do not close the ClonedProgrammingSubmission, since this would try to delete the workspace file
        // Instead, we delete the project directory manually
        this.cleanupProjectDirectory();

        // Tell IntelliJ's VCS manager that the Git repo is gone
        // This prevents an annoying popup that warns about a missing Git root
        IntellijUtil.forceFilesSync(() -> {
            IntellijUtil.getVcsManager().setDirectoryMappings(List.of());
            IntellijUtil.getVcsManager().fireDirectoryMappingsChanged();
        });

        for (Runnable assessmentClosedListener : this.assessmentClosedListeners) {
            assessmentClosedListener.run();
        }
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
            LOG.warn(e);
            ArtemisUtils.displayGenericErrorBalloon("Invalid grading config", e.getMessage());
            return Optional.empty();
        }
    }

    private void cleanupProjectDirectory() {
        // Close all open editors
        var editorManager = FileEditorManager.getInstance(IntellijUtil.getActiveProject());
        ApplicationManager.getApplication().invokeAndWait(() -> {
            for (var editor : editorManager.getAllEditors()) {
                editorManager.closeFile(editor.getFile());
            }
        });

        var rootPath = IntellijUtil.getProjectRootDirectory();
        // Delete all directory contents, but not the directory itself
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    FileUtils.forceDelete(file.toFile());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dir.equals(rootPath)) {
                        FileUtils.forceDelete(dir.toFile());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.warn(e);
            ArtemisUtils.displayGenericErrorBalloon("Error cleaning up project directory", e.getMessage());
        }
    }

    private class StartAssessmentTask extends Task.Modal {
        private final CorrectionRound correctionRound;
        private final GradingConfig gradingConfig;

        public StartAssessmentTask(CorrectionRound correctionRound, GradingConfig gradingConfig) {
            super(IntellijUtil.getActiveProject(), "Starting Assessment", false);
            this.correctionRound = correctionRound;
            this.gradingConfig = gradingConfig;
        }

        @Override
        public void run(@NotNull ProgressIndicator progressIndicator) {
            try {
                progressIndicator.setText("Locking...");
                var nextAssessment = activeExercise.tryLockNextSubmission(correctionRound, gradingConfig);
                if (nextAssessment.isPresent()) {
                    progressIndicator.setText("Cloning...");
                    if (!initializeAssessment(nextAssessment.get())) {
                        return;
                    }

                    // Now everything is done - the submission is properly locked, and the repository is cloned
                    if (activeAssessment.getAssessment().getAnnotations().isEmpty()) {
                        activeAssessment.runAutograder();
                    } else {
                        ArtemisUtils.displayGenericInfoBalloon(
                                "Skipping Autograder",
                                "The submission already has annotations. Skipping the Autograder.");
                    }

                    ArtemisUtils.displayGenericInfoBalloon(
                            "Assessment started",
                            "You can now grade the submission. Please make sure that are familiar with all "
                                    + "grading guidelines.");
                } else {
                    ArtemisUtils.displayGenericInfoBalloon(
                            "Could not start assessment",
                            "There are no more submissions to assess. Thanks for your work :)");
                }
            } catch (ArtemisNetworkException e) {
                LOG.warn(e);
                ArtemisUtils.displayNetworkErrorBalloon("Could not lock assessment", e);
            } catch (AnnotationMappingException e) {
                LOG.warn(e);
                ArtemisUtils.displayGenericErrorBalloon(
                        "Could not parse assessment",
                        "Could not parse previous assessment. This is a serious bug; please contact the "
                                + "Übungsleitung!");
            }
        }
    }

    private class ReopenAssessmentTask extends Task.Modal {
        private final ProgrammingSubmission submission;
        private final GradingConfig gradingConfig;

        public ReopenAssessmentTask(ProgrammingSubmission submission, GradingConfig gradingConfig) {
            super(IntellijUtil.getActiveProject(), "Reopening Assessment", false);
            this.submission = submission;
            this.gradingConfig = gradingConfig;
        }

        @Override
        public void run(@NotNull ProgressIndicator progressIndicator) {
            try {
                progressIndicator.setText("Locking...");
                var assessment = submission.tryLock(gradingConfig);
                if (assessment.isPresent()) {
                    progressIndicator.setText("Cloning...");
                    initializeAssessment(assessment.get());
                } else {
                    ArtemisUtils.displayGenericErrorBalloon(
                            "Failed to reopen assessment", "Most likely, your lock has been taken by someone else.");
                }

            } catch (ArtemisNetworkException e) {
                LOG.warn(e);
                ArtemisUtils.displayNetworkErrorBalloon("Could not lock assessment", e);
            } catch (AnnotationMappingException e) {
                LOG.warn(e);
                ArtemisUtils.displayGenericErrorBalloon(
                        "Could not parse assessment",
                        "Could not parse previous assessment. This is a serious bug; please contact the "
                                + "Übungsleitung!");
            } catch (MoreRecentSubmissionException e) {
                LOG.warn(e);
                ArtemisUtils.displayGenericErrorBalloon(
                        "Could not reopen assessment", "The student has submitted a newer version of his code.");
            }
        }
    }
}
