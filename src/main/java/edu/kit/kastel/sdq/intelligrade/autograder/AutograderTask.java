/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.autograder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import de.firemage.autograder.api.loader.AutograderLoader;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.ClonedProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.autograder.AutograderFailedException;
import edu.kit.kastel.sdq.artemis4j.grading.autograder.AutograderRunner;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.AutograderOption;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import org.jspecify.annotations.NonNull;

public class AutograderTask extends Task.Backgroundable {
    private static final Logger LOG = Logger.getInstance(AutograderTask.class);

    private final Assessment assessment;
    private final ClonedProgrammingSubmission clonedSubmission;
    private final Runnable onSuccessCallback;

    public static void execute(
            Assessment assessment, ClonedProgrammingSubmission clonedSubmission, Runnable onSuccessCallback) {
        new AutograderTask(assessment, clonedSubmission, onSuccessCallback)
                .setCancelText("Stop Autograder")
                .queue();
    }

    private AutograderTask(Assessment assessment, ClonedProgrammingSubmission clonedSubmission, Runnable onSuccess) {
        super(IntellijUtil.getActiveProject(), "Autograder", true);

        this.assessment = assessment;
        this.clonedSubmission = clonedSubmission;
        this.onSuccessCallback = onSuccess;
    }

    public void run(@NonNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);

        var settings = ArtemisSettingsState.getInstance();

        // Load Autograder from file
        if (settings.getAutograderOption() == AutograderOption.FROM_FILE
                && !loadAutograderFromFile(settings, indicator)) {
            return;
        }

        try {
            Consumer<String> statusConsumer = status -> indicator.setText("Autograder: " + status);

            var stats = AutograderRunner.runAutograderFallible(
                    assessment, clonedSubmission, Locale.GERMANY, 2, statusConsumer, null);

            String message = "Autograder made %d annotation(s). Please double-check all of them for false-positives!"
                    .formatted(stats.annotationsMade());
            ApplicationManager.getApplication()
                    .invokeLater(
                            () -> Messages.showMessageDialog(message, "Autograder Completed", AllIcons.Status.Success));

            ApplicationManager.getApplication().invokeLater(this.onSuccessCallback);
        } catch (AutograderFailedException e) {
            LOG.warn(e);
            ArtemisUtils.displayGenericErrorBalloon("Autograder Failed", e.getMessage());
        }
    }

    private boolean loadAutograderFromFile(ArtemisSettingsState settings, ProgressIndicator indicator) {
        if (!AutograderLoader.isAutograderLoaded()) {
            var path = settings.getAutograderPath();
            if (path == null || path.isBlank()) {
                ArtemisUtils.displayGenericErrorBalloon(
                        "No Autograder Path",
                        "Please set the path to the Autograder JAR, or choose to download it from GitHub.");
                return false;
            }

            indicator.setText("Loading Autograder");
            try {
                AutograderLoader.loadFromFile(Path.of(settings.getAutograderPath()));
            } catch (IOException e) {
                LOG.warn(e);
                ArtemisUtils.displayGenericErrorBalloon("Could not load Autograder", e.getMessage());
                return false;
            }
        } else {
            ArtemisUtils.displayGenericWarningBalloon(
                    "Autograder Already Loaded",
                    "Not reloading it from the specified file. Restart the IDE to reload it.");
        }

        return true;
    }
}
