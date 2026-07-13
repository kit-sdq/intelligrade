/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.state;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound;
import edu.kit.kastel.sdq.artemis4j.grading.PackedAssessment;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.InvalidGradingConfigException;
import edu.kit.kastel.sdq.intelligrade.AssessmentTracker;
import edu.kit.kastel.sdq.intelligrade.EndAssessmentService;
import edu.kit.kastel.sdq.intelligrade.ReopenAssessmentService;
import edu.kit.kastel.sdq.intelligrade.StartAssessmentService;
import edu.kit.kastel.sdq.intelligrade.SubmitAction;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.listeners.ExerciseListener;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import org.jspecify.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class ProjectState implements Disposable {
    private static final Logger LOG = Logger.getInstance(ProjectState.class);

    private final Project project;
    private volatile ProgrammingExercise activeExercise;
    private volatile GradingConfig.GradingConfigDTO cachedGradingConfigDTO;

    public ProjectState(Project project) {
        this.project = project;

        // Try to parse the grading config once from storage.
        // Subscribers receive this cached value via subscribe(...); no bus event is needed during service construction.
        getGradingConfigDTO(false, false);

        this.subscribe(this, new ExerciseListener() {
            @Override
            public void exerciseChanged(@Nullable ProgrammingExercise exercise) {
                ProjectState.this.activeExercise = exercise;
            }
        });
    }

    public static ProjectState getInstance(Project project) {
        return project.getService(ProjectState.class);
    }

    public void setSelectedGradingConfigPath(@Nullable String gradingConfigPath) {
        if (Objects.equals(ArtemisSettingsState.getInstance().getSelectedGradingConfigPath(), gradingConfigPath)) {
            return;
        }

        var hadLoadedConfig = cachedGradingConfigDTO != null;
        cachedGradingConfigDTO = null;
        ArtemisSettingsState.getInstance().setSelectedGradingConfigPath(gradingConfigPath);

        if (hadLoadedConfig) {
            notifyGradingConfigChanged(null);
        }
    }

    public boolean hasDifferentSelectedGradingConfigPath(String gradingConfigPath) {
        return !Objects.equals(ArtemisSettingsState.getInstance().getSelectedGradingConfigPath(), gradingConfigPath);
    }

    public boolean setLoadedGradingConfig(String gradingConfigPath, GradingConfig.GradingConfigDTO gradingConfigDTO) {
        if (hasDifferentSelectedGradingConfigPath(gradingConfigPath)) {
            return false;
        }

        cachedGradingConfigDTO = gradingConfigDTO;
        notifyGradingConfigChanged(gradingConfigDTO);
        return true;
    }

    public void clearLoadedGradingConfig(String gradingConfigPath) {
        if (hasDifferentSelectedGradingConfigPath(gradingConfigPath) || cachedGradingConfigDTO == null) {
            return;
        }

        cachedGradingConfigDTO = null;
        notifyGradingConfigChanged(null);
    }

    public void clearProjectSessionState() {
        activeExercise = null;

        AssessmentTracker.getInstance(project).clearAssessment();
    }

    /**
     * Registers a listener that is called when the grading config or exercise changes.
     * <p>
     * This is used to update the UI when the grading config or exercise changes.
     *
     * @param listener the listener to be called
     */
    public void subscribe(Disposable parentDisposable, ExerciseListener listener) {
        project.getMessageBus().connect(parentDisposable).subscribe(ExerciseListener.TOPIC, listener);

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }

            if (this.cachedGradingConfigDTO != null) {
                // If the grading config is already loaded, call the listener immediately
                listener.configChanged(this.cachedGradingConfigDTO);
            }

            listener.exerciseChanged(this.activeExercise);
        });
    }

    // TODO: This should move to AssessmentTracker
    public boolean isAssessing() {
        return AssessmentTracker.getInstance(project).getActiveAssessment() != null;
    }
    // TODO: This should move to AssessmentTracker
    public Optional<ActiveAssessment> getActiveAssessment() {
        return Optional.ofNullable(AssessmentTracker.getInstance(project).getActiveAssessment());
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

        StartAssessmentService.getInstance(project)
                .queue(correctionRound, gradingConfig.get(), activeExercise, submission);
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

        ReopenAssessmentService.getInstance(project).queue(assessment, gradingConfig.get());
    }

    public void updateAssessmentState(SubmitAction action) {
        if (!isAssessing()) {
            ArtemisUtils.displayNoAssessmentBalloon();
            return;
        }

        EndAssessmentService.getInstance(project).queue(action);
    }

    private void notifyGradingConfigChanged(GradingConfig.@Nullable GradingConfigDTO gradingConfigDTO) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }

            // Skip outdated notifications
            if (gradingConfigDTO != this.cachedGradingConfigDTO) {
                return;
            }

            project.getMessageBus().syncPublisher(ExerciseListener.TOPIC).configChanged(gradingConfigDTO);
        });
    }

    private Optional<GradingConfig.GradingConfigDTO> getGradingConfigDTO(boolean required) {
        return getGradingConfigDTO(required, true);
    }

    private Optional<GradingConfig.GradingConfigDTO> getGradingConfigDTO(boolean required, boolean notify) {
        if (this.cachedGradingConfigDTO == null) {
            var gradingConfigPath = ArtemisSettingsState.getInstance().getSelectedGradingConfigPath();
            if (gradingConfigPath == null) {
                if (required) {
                    onInvalidGradingConfig("Please select a grading config");
                }
                return Optional.empty();
            }

            try {
                var gradingConfigFile =
                        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(gradingConfigPath));
                if (gradingConfigFile == null) {
                    throw new IOException("Grading config file not found: " + gradingConfigPath);
                }

                var fileContent = VfsUtilCore.loadText(gradingConfigFile);
                this.cachedGradingConfigDTO = GradingConfig.readDTOFromString(fileContent);

                if (notify) {
                    this.notifyGradingConfigChanged(this.cachedGradingConfigDTO);
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

    public boolean hasLoadedReviewConfig() {
        return this.getLoadedGradingConfigDTO()
                .map(GradingConfig.GradingConfigDTO::review)
                .orElse(false);
    }

    public boolean hasLoadedGradingConfig() {
        return this.cachedGradingConfigDTO != null;
    }

    public Optional<GradingConfig.GradingConfigDTO> getLoadedGradingConfigDTO() {
        return Optional.ofNullable(this.cachedGradingConfigDTO);
    }

    public Optional<ProgrammingExercise> getActiveExercise() {
        return Optional.ofNullable(this.activeExercise);
    }

    private void onInvalidGradingConfig(String message) {
        ArtemisUtils.displayGenericErrorBalloon("No/invalid grading config", message);
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

    /**
     * Returns the root directory of this project.
     * This is the directory under which the project configuration files (like .idea) are stored.
     * There are some caveats, see {@link Project#getBasePath()}.
     *
     * @return the path to the root directory of this project
     */
    public Path getProjectRootDirectory() {
        var basePath = this.project.getBasePath();
        return Path.of(Objects.requireNonNullElse(basePath, ""));
    }

    private String loadInspectionsProfile() throws IOException {
        try (var in = ProjectState.class.getResourceAsStream("/Project_Default.xml")) {
            if (in == null) {
                throw new IllegalStateException("Default inspections profile not found in resources");
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void setupProjectProfile() {
        try {
            var profileContent = loadInspectionsProfile();

            WriteAction.runAndWait(() -> {
                try {
                    var baseDirectory = ProjectUtil.guessProjectDir(project);
                    if (baseDirectory == null) {
                        throw new IOException("Project base directory is not available");
                    }

                    var profileDirectory = VfsUtil.createDirectories(
                            baseDirectory.getPath() + "/" + Project.DIRECTORY_STORE_FOLDER + "/inspectionProfiles");
                    var profileFile = profileDirectory.findOrCreateChildData(this, "Project_Default.xml");
                    VfsUtil.saveText(profileFile, profileContent);
                } catch (IOException ioException) {
                    throw new UncheckedIOException(ioException);
                }
            });
        } catch (IOException ioException) {
            throw new IllegalStateException("Could not write default profile", ioException);
        } catch (UncheckedIOException ioException) {
            throw new IllegalStateException("Could not write default profile", ioException.getCause());
        }
    }

    public Optional<VirtualFile> findAnnotationVirtualFile(Annotation annotation) {
        var baseDirectory = ProjectUtil.guessProjectDir(this.project);
        if (baseDirectory == null) {
            return Optional.empty();
        }

        var relativePath = ActiveAssessment.ASSIGNMENT_SUB_PATH + "/"
                + annotation.getFilePath().replace("\\", "/");
        return Optional.ofNullable(baseDirectory.findFileByRelativePath(relativePath));
    }

    public static String colorToCSS(Color color) {
        return "rgb(%d, %d, %d)".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override
    public void dispose() {
        // Nothing to dispose, but necessary because of subscription to the message bus
    }
}
