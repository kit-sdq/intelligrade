/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.ui.jcef.JBCefApp;
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.client.ArtemisInstance;
import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnection;
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound;
import edu.kit.kastel.sdq.artemis4j.grading.PackedAssessment;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.User;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.InvalidGradingConfigException;
import edu.kit.kastel.sdq.intelligrade.AssessmentTracker;
import edu.kit.kastel.sdq.intelligrade.EndAssessmentService;
import edu.kit.kastel.sdq.intelligrade.ReopenAssessmentService;
import edu.kit.kastel.sdq.intelligrade.StartAssessmentService;
import edu.kit.kastel.sdq.intelligrade.SubmitAction;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisCredentialsProvider;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.login.CefUtils;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;

public class PluginState {
    private static final Logger LOG = Logger.getInstance(PluginState.class);

    private static PluginState pluginState;

    private final List<Consumer<ArtemisConnection>> connectedListeners = new ArrayList<>();
    private final List<Consumer<ProgrammingExercise>> exerciseSelectedListeners = new ArrayList<>();
    private final List<Consumer<ActiveAssessment>> assessmentStartedListeners = new ArrayList<>();
    private final List<Runnable> assessmentClosedListeners = new ArrayList<>();
    private final List<Consumer<GradingConfig.GradingConfigDTO>> gradingConfigChangedListeners = new ArrayList<>();
    private final List<Runnable> missingGradingConfigListeners = new ArrayList<>();
    private final Map<Long, User> knownAssessors = new HashMap<>();

    private ArtemisConnection connection;
    private ProgrammingExercise activeExercise;
    private GradingConfig.GradingConfigDTO cachedGradingConfigDTO;

    private ActiveAssessment activeAssessment;

    private PluginState() {
        // The code for opening/closing assessments is in kotlin, but this class keeps track of the active assessment
        // as well.
        //
        // With this, PluginState will be notified when an assessment changes.
        AssessmentTracker.INSTANCE.addListener(changedAssessment -> {
            activeAssessment = changedAssessment;

            // The invokeLater ensures that the listeners are running on EDT, which is required for UI updates.
            if (changedAssessment == null) {
                // Notify listeners that the assessment was closed
                for (Runnable listener : assessmentClosedListeners) {
                    ApplicationManager.getApplication().invokeLater(listener);
                }
            } else {
                // Notify listeners that the assessment was started
                for (Consumer<ActiveAssessment> listener : assessmentStartedListeners) {
                    ApplicationManager.getApplication().invokeLater(() -> listener.accept(changedAssessment));
                }
            }
        });

        // Try to parse the grading config once from storage
        getGradingConfigDTO(false);
    }

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

    /**
     * Registers a listener that is called when intelligrade needs the grading config, but it is missing.
     * <p>
     * This is used to highlight the input text box in which the grading config should be entered.
     *
     * @param listener the listener to be called
     */
    public void registerMissingGradingConfigListeners(Runnable listener) {
        this.missingGradingConfigListeners.add(listener);
    }

    /**
     * Registers a listener that is called when the grading config changes.
     * <p>
     * This is used to update the UI when the grading config changes.
     *
     * @param listener the listener to be called
     */
    public void registerGradingConfigChangedListener(Consumer<GradingConfig.GradingConfigDTO> listener) {
        this.gradingConfigChangedListeners.add(listener);
        if (this.cachedGradingConfigDTO != null) {
            // If the grading config is already loaded, call the listener immediately
            listener.accept(this.cachedGradingConfigDTO);
        }
    }

    public void registerConnectedListener(Consumer<ArtemisConnection> listener) {
        this.connectedListeners.add(listener);
        listener.accept(this.connection);
    }

    public void registerExerciseSelectedListener(Consumer<ProgrammingExercise> listener) {
        this.exerciseSelectedListeners.add(listener);
        listener.accept(this.activeExercise);
    }

    public boolean isAssessing() {
        return activeAssessment != null;
    }

    public void startNextAssessment(CorrectionRound correctionRound) {
        this.internalStartAssessment(correctionRound, null);
    }

    public void startAssessment(ProgrammingSubmission submission, CorrectionRound correctionRound) {
        this.internalStartAssessment(correctionRound, submission);
    }

    private void internalStartAssessment(CorrectionRound correctionRound, ProgrammingSubmission submission) {
        if (isAssessing()) {
            ArtemisUtils.displayFinishAssessmentFirstBalloon();
            return;
        }

        if (activeExercise == null) {
            ArtemisUtils.displayGenericErrorBalloon("Could not start assessment", "No course selected");
            return;
        }

        var gradingConfig = this.createGradingConfig();
        if (gradingConfig.isEmpty()) {
            return;
        }

        StartAssessmentService.getInstance(IntellijUtil.getActiveProject())
                .queue(correctionRound, gradingConfig.get(), activeExercise, submission);
    }

    public void saveAssessment() {
        if (!isAssessing()) {
            ArtemisUtils.displayNoAssessmentBalloon();
            return;
        }

        EndAssessmentService.getInstance(IntellijUtil.getActiveProject()).queue(SubmitAction.SAVE);
    }

    public void submitAssessment() {
        if (!isAssessing()) {
            ArtemisUtils.displayNoAssessmentBalloon();
            return;
        }

        EndAssessmentService.getInstance(IntellijUtil.getActiveProject()).queue(SubmitAction.SUBMIT);
    }

    public void cancelAssessment() {
        if (!isAssessing()) {
            ArtemisUtils.displayNoAssessmentBalloon();
            return;
        }

        EndAssessmentService.getInstance(IntellijUtil.getActiveProject()).queue(SubmitAction.CANCEL);
    }

    public void closeAssessment() {
        if (!isAssessing()) {
            ArtemisUtils.displayNoAssessmentBalloon();
            return;
        }

        EndAssessmentService.getInstance(IntellijUtil.getActiveProject()).queue(SubmitAction.CLOSE);
    }

    public void reopenAssessment(PackedAssessment assessment) {
        if (isAssessing()) {
            ArtemisUtils.displayFinishAssessmentFirstBalloon();
            return;
        }

        if (activeExercise == null) {
            ArtemisUtils.displayGenericErrorBalloon("Could not reopen assessment", "No exercise selected");
            return;
        }

        var gradingConfig = this.createGradingConfig();
        if (gradingConfig.isEmpty()) {
            return;
        }

        ReopenAssessmentService.getInstance(IntellijUtil.getActiveProject()).queue(assessment, gradingConfig.get());
    }

    public void setSelectedGradingConfigPath(String path) {
        ArtemisSettingsState.getInstance().setSelectedGradingConfigPath(path);
        this.cachedGradingConfigDTO = null;
    }

    public Optional<GradingConfig.GradingConfigDTO> getGradingConfigDTO(boolean required) {
        if (this.cachedGradingConfigDTO == null) {
            var gradingConfigPath = ArtemisSettingsState.getInstance().getSelectedGradingConfigPath();
            if (gradingConfigPath == null) {
                if (required) {
                    onInvalidGradingConfig("Please select a grading config");
                }
                return Optional.empty();
            }

            try {
                var fileContent = Files.readString(Path.of(gradingConfigPath));
                this.cachedGradingConfigDTO = GradingConfig.readDTOFromString(fileContent);

                for (var listener : this.gradingConfigChangedListeners) {
                    listener.accept(this.cachedGradingConfigDTO);
                }
            } catch (IOException | InvalidGradingConfigException e) {
                if (required) {
                    LOG.warn(e);
                    onInvalidGradingConfig(e.getMessage());
                }
                return Optional.empty();
            }
        }
        return Optional.of(cachedGradingConfigDTO);
    }

    public boolean hasReviewConfig() {
        var gradingConfigDTO = this.getGradingConfigDTO(false);
        if (gradingConfigDTO.isEmpty()) {
            return false;
        }
        return gradingConfigDTO.get().review();
    }

    public Optional<ProgrammingExercise> getActiveExercise() {
        return Optional.ofNullable(activeExercise);
    }

    public void setActiveExercise(ProgrammingExercise exercise) {
        this.activeExercise = exercise;
        for (var listener : this.exerciseSelectedListeners) {
            listener.accept(exercise);
        }
    }

    public Optional<ActiveAssessment> getActiveAssessment() {
        return Optional.ofNullable(activeAssessment);
    }

    public User getAssessor() throws ArtemisNetworkException {
        return connection.getAssessor();
    }

    public boolean isInstructor() throws ArtemisNetworkException {
        if (activeExercise == null) {
            return false;
        }

        return activeExercise.getCourse().isInstructor(getAssessor());
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

        AssessmentTracker.INSTANCE.clearAssessment();
    }

    private void onInvalidGradingConfig(String message) {
        ArtemisUtils.displayGenericErrorBalloon("No/invalid grading config", message);
        for (Runnable missingGradingConfigListener : this.missingGradingConfigListeners) {
            missingGradingConfigListener.run();
        }
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

    private Optional<GradingConfig> createGradingConfig() {
        var gradingConfigDTO = this.getGradingConfigDTO(true);
        if (gradingConfigDTO.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(GradingConfig.fromDTO(gradingConfigDTO.get(), activeExercise));
        } catch (InvalidGradingConfigException e) {
            LOG.warn(e);
            ArtemisUtils.displayGenericErrorBalloon("Invalid grading config", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<User> resolveAssessorId(long userId) {
        var exercise = this.getActiveExercise().orElse(null);
        if (exercise == null) {
            return Optional.empty();
        }

        if (knownAssessors.containsKey(userId)) {
            return Optional.of(knownAssessors.get(userId));
        }

        // Start by adding your own id:
        try {
            var thisAssessor = this.connection.getAssessor();
            knownAssessors.putIfAbsent(thisAssessor.getId(), thisAssessor);

            for (var submission : exercise.fetchAllSubmissions()) {
                var firstRound = submission.getFirstRoundAssessment();
                if (firstRound != null) {
                    var assessor = firstRound.getAssessor();
                    knownAssessors.putIfAbsent(assessor.getId(), assessor);
                }

                var secondRound = submission.getSecondRoundAssessment();
                if (secondRound != null) {
                    var assessor = secondRound.getAssessor();
                    knownAssessors.putIfAbsent(assessor.getId(), assessor);
                }
            }
        } catch (ArtemisNetworkException exception) {
            LOG.warn(exception);
            return Optional.empty();
        }

        return Optional.ofNullable(knownAssessors.get(userId));
    }
}
