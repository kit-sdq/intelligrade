/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Predicate;

import javax.swing.JButton;
import javax.swing.event.DocumentEvent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.TextComponentEmptyText;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBFont;
import edu.kit.kastel.sdq.artemis4j.grading.CorrectionRound;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.InvalidGradingConfigException;
import edu.kit.kastel.sdq.intelligrade.AssessmentTracker;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.listeners.AssessmentStateListener;
import edu.kit.kastel.sdq.intelligrade.listeners.ExerciseListener;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import edu.kit.kastel.sdq.intelligrade.state.ProjectState;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtils;
import edu.kit.kastel.sdq.intelligrade.utils.LatestRequestRunner;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class GeneralPanel extends JBPanel<GeneralPanel> {
    private static final int GRADING_CONFIG_VALIDATION_DELAY_MS = 400;

    private final Project project;
    private final LatestRequestRunner instructorUpdateRunner;
    private final Alarm gradingConfigValidationAlarm;

    private final LatestRequestRunner updateConfigRunner;

    private final JBLabel modeInfoLabel;
    private final JButton startGradingRound1Button;
    private final JButton startGradingRound2Button;
    private final JButton openInstructorDialogButton;
    private final TextFieldWithBrowseButton gradingConfigPathInput;

    private @Nullable ProgrammingExercise activeExercise;
    private @Nullable ActiveAssessment activeAssessment;
    private @Nullable String gradingConfigValidationError;
    private boolean reviewMode;
    private boolean instructorDialogAllowed;

    GeneralPanel(Disposable parentDisposable, Project project) {
        super(new MigLayout("wrap 1, hidemode 3", "[grow]"));

        this.project = project;
        this.instructorUpdateRunner = new LatestRequestRunner(project);
        this.updateConfigRunner = new LatestRequestRunner(project);
        this.gradingConfigValidationAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable);

        modeInfoLabel = new JBLabel();
        modeInfoLabel.setForeground(JBColor.GREEN);
        modeInfoLabel.setFont(JBFont.h3().asBold());
        add(modeInfoLabel, "align center");

        startGradingRound1Button = IntellijUtils.createWrappingButton("Start Grading Round 1");
        startGradingRound1Button.setForeground(JBColor.GREEN);
        startGradingRound1Button.addActionListener(
                _ -> ProjectState.getInstance(project).startNextAssessment(CorrectionRound.FIRST));
        add(startGradingRound1Button, "grow");

        startGradingRound2Button = IntellijUtils.createWrappingButton("Start Grading Round 2");
        startGradingRound2Button.setForeground(JBColor.GREEN);
        startGradingRound2Button.addActionListener(
                _ -> ProjectState.getInstance(project).startNextAssessment(CorrectionRound.SECOND));
        add(startGradingRound2Button, "grow");

        openInstructorDialogButton = IntellijUtils.createWrappingButton("Show All Submissions");
        openInstructorDialogButton.setForeground(JBColor.GREEN);
        openInstructorDialogButton.addActionListener(_ -> SubmissionsInstructorDialog.showDialog(project));
        openInstructorDialogButton.setVisible(false);
        add(openInstructorDialogButton, "grow");

        gradingConfigPathInput = createGradingConfigPathInput(parentDisposable);
        add(gradingConfigPathInput, "grow");

        ProjectState.getInstance(project).subscribe(parentDisposable, new ExerciseListener() {
            @Override
            public void exerciseChanged(@Nullable ProgrammingExercise exercise) {
                activeExercise = exercise;
                instructorDialogAllowed = false;
                updateState();

                if (exercise == null) {
                    return;
                }

                // Enable/disable instructor button(s)

                GeneralPanel.this
                        .instructorUpdateRunner
                        .fetchArtemis(() -> exercise.getCourse()
                                .isInstructor(exercise.getConnection().getAssessor()))
                        .withErrorNotification("Failed to check if current user is instructor")
                        .onFailureInEdt(_ -> instructorDialogAllowed = false)
                        .thenIf(() -> activeExercise == exercise, isInstructor -> {
                            instructorDialogAllowed = isInstructor;
                            updateState();
                        });
            }

            @Override
            public void configChanged(GradingConfig.@Nullable GradingConfigDTO config) {
                reviewMode = config != null && config.review();
                updateState();
            }
        });

        AssessmentTracker.getInstance(project).subscribe(parentDisposable, new AssessmentStateListener() {
            @Override
            public void activeAssessmentChanged(@Nullable ActiveAssessment assessment) {
                activeAssessment = assessment;
                updateState();
            }
        });
    }

    @RequiresEdt
    private void updateState() {
        boolean hasExercise = activeExercise != null;
        boolean isAssessing = activeAssessment != null;

        modeInfoLabel.setText(reviewMode ? "Review Mode (you have a review config)" : "");
        openInstructorDialogButton.setVisible(reviewMode);
        openInstructorDialogButton.setEnabled(reviewMode && instructorDialogAllowed);

        gradingConfigPathInput.setEnabled(!isAssessing);

        startGradingRound1Button.setEnabled(!isAssessing && !reviewMode && hasExercise);
        startGradingRound2Button.setEnabled(
                !isAssessing && !reviewMode && activeExercise != null && activeExercise.hasSecondCorrectionRound());
    }

    private TextFieldWithBrowseButton createGradingConfigPathInput(Disposable parentDisposable) {
        var input = new TextFieldWithBrowseButton();

        // TODO: Set where it should start browsing, ideally the location of the previous config or the parent of the
        // workspace?
        input.addBrowseFolderListener(
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor("json")));
        input.setText(ArtemisSettingsState.getInstance().getSelectedGradingConfigPath());

        var gradingConfigPathInput = input.getText();

        if (gradingConfigPathInput.isBlank()) {
            this.gradingConfigValidationError = "No grading config selected";
        } else if (!ProjectState.getInstance(project).hasLoadedGradingConfig()) {
            this.gradingConfigValidationError = "Selected grading config is invalid";
        } else {
            this.gradingConfigValidationError = null;
        }

        input.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NonNull DocumentEvent documentEvent) {
                var gradingConfigPath = input.getText().trim();

                ProjectState.getInstance(project)
                        .setSelectedGradingConfigPath(gradingConfigPath.isEmpty() ? null : gradingConfigPath);

                gradingConfigValidationAlarm.cancelAllRequests();

                if (!gradingConfigPath.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    updateGradingConfigValidationError("Grading config must be a JSON file");
                    return;
                }

                gradingConfigValidationAlarm.addRequest(
                        () -> updateGradingConfigWith(gradingConfigPath), GRADING_CONFIG_VALIDATION_DELAY_MS);
            }
        });

        new ComponentValidator(parentDisposable)
                .withValidator(() -> validateGradingConfigPath(input))
                .andRegisterOnDocumentListener(input.getTextField())
                .installOn(input.getTextField());

        var innerTextField = (JBTextField) input.getTextField();
        innerTextField.getEmptyText().setText("Path to grading config");
        innerTextField.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, (Predicate<JBTextField>)
                field -> field.getText().isEmpty());

        return input;
    }

    private void updateGradingConfigWith(String gradingConfigPath) {
        updateConfigRunner
                // This loads and parses the config on a background thread:
                .fetch(() -> {
                    var path = Path.of(gradingConfigPath);
                    var gradingConfigFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
                    if (gradingConfigFile == null || gradingConfigFile.isDirectory() || !gradingConfigFile.exists()) {
                        throw new InvalidGradingConfigException("Grading config file not found");
                    }

                    return GradingConfig.readDTOFromString(VfsUtilCore.loadText(gradingConfigFile));
                })
                // naturally failures related to the input path should be displayed:
                .handle(InvalidPathException.class, IOException.class, InvalidGradingConfigException.class)
                // No need to display an error balloon, we use the text message field for this
                .withoutErrorNotification()
                .withoutErrorLogging()
                .onFailureInEdt(exception -> {
                    String message = exception.getMessage();
                    if (exception instanceof InvalidPathException) {
                        message = "Invalid grading config path";
                    }

                    // just in case, we invalidate the currently loaded config
                    ProjectState.getInstance(project).clearLoadedGradingConfig(gradingConfigPath);
                    updateGradingConfigValidationError(
                            message == null || message.isBlank() ? "Selected grading config is invalid" : message);
                })
                // In case the request remains relevant, this will update the loaded config and reset the error
                .thenIf(
                        () -> !ProjectState.getInstance(project)
                                .hasDifferentSelectedGradingConfigPath(gradingConfigPath),
                        gradingConfigDTO -> {
                            if (ProjectState.getInstance(project)
                                    .setLoadedGradingConfig(gradingConfigPath, gradingConfigDTO)) {
                                updateGradingConfigValidationError(null);
                            }
                        });
    }

    private ValidationInfo validateGradingConfigPath(TextFieldWithBrowseButton input) {
        return gradingConfigValidationError == null ? null : new ValidationInfo(gradingConfigValidationError, input);
    }

    @RequiresEdt
    private void updateGradingConfigValidationError(@Nullable String error) {
        gradingConfigValidationError = error;
        ComponentValidator.getInstance(gradingConfigPathInput.getTextField()).ifPresent(ComponentValidator::revalidate);
    }
}
