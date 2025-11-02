/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.autograder;

import java.awt.BorderLayout;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import de.firemage.autograder.api.FailureInformation;
import de.firemage.autograder.api.loader.AutograderLoader;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.ClonedProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.autograder.AutograderFailedException;
import edu.kit.kastel.sdq.artemis4j.grading.autograder.AutograderRunner;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.AutograderOption;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import edu.kit.kastel.sdq.intelligrade.widgets.TextBuilder;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

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

    public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);

        var settings = ArtemisSettingsState.getInstance();

        // Load Autograder from file
        if (settings.getAutograderOption() == AutograderOption.FROM_FILE
                && !loadAutograderFromFile(settings, indicator)) {
            return;
        }

        try {
            List<FailureInformation> failures = new ArrayList<>();
            Consumer<String> statusConsumer = status -> indicator.setText("Autograder: " + status);

            var stats = AutograderRunner.runAutograderFallible(
                    assessment, clonedSubmission, Locale.GERMANY, 2, statusConsumer, failures::add);

            String message = "Autograder made %d annotation(s). Please double-check all of them for false-positives!"
                    .formatted(stats.annotationsMade());

            ApplicationManager.getApplication().invokeLater(() -> {
                if (failures.isEmpty()) {
                    Messages.showMessageDialog(message, "Autograder Completed", AllIcons.Status.Success);
                } else {
                    showAutograderErrorPopup(stats.annotationsMade(), failures);
                }
            });
            ApplicationManager.getApplication().invokeLater(this.onSuccessCallback);
        } catch (AutograderFailedException e) {
            LOG.warn(e);
            ArtemisUtils.displayGenericErrorBalloon("Autograder Failed", e.getMessage());
        }
    }

    public static void showAutograderErrorPopup(int annotationsMade, List<FailureInformation> failures) {
        var mainPanel = new JBPanel<>(new MigLayout("wrap", "[grow]", "[][][grow]"));
        mainPanel.add(new JBLabel("Autograder made %d annotation(s).".formatted(annotationsMade)), "growx");
        mainPanel.add(new JBLabel("However, the following failures occurred during execution:"), "growx");

        StringJoiner result = new StringJoiner(System.lineSeparator());
        for (var failure : failures) {
            StringWriter stringWriter = new StringWriter();
            failure.exception().printStackTrace(new PrintWriter(stringWriter));

            result.add(stringWriter.toString());
        }

        mainPanel.add(
                TextBuilder.textArea(result.toString())
                        .editable(false)
                        .maxLines(20)
                        .component(),
                "grow");
        new WarningDialog(IntellijUtil.getActiveProject(), "Autograder Completed with Failures", mainPanel).show();
    }

    private static class WarningDialog extends MessageDialog {
        private final JComponent content;

        public WarningDialog(Project project, String title, JComponent content) {
            super(
                    project,
                    null,
                    "",
                    title,
                    new String[] {CommonBundle.getOkButtonText()},
                    0,
                    0,
                    AllIcons.General.WarningDialog,
                    null,
                    true);

            this.content = content;
            this.init();
        }

        @Override
        protected JComponent doCreateCenterPanel() {
            JPanel panel = createIconPanel();

            // This might be called while this.content is still null
            if (this.content != null) {
                // panel.add(Messages.wrapToScrollPaneIfNeeded(this.content, 50, 20), BorderLayout.CENTER);
                panel.add(this.content, BorderLayout.CENTER);
            }

            return panel;
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
