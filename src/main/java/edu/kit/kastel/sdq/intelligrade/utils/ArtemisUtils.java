/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.utils;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;

import edu.kit.kastel.sdq.artemis4j.client.AssessmentType;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.ClonedProgrammingSubmission;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;

/**
 * Utility Class to handle Artemis related common tasks such as
 * creating a new client and logging in or creating Error messages.
 */
public final class ArtemisUtils {
    public static final DateTimeFormatter DATE_TIME_PATTERN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private ArtemisUtils() {}

    public static boolean doesUrlExist(String url) {
        try {
            var connection = (HttpURLConnection) new URI(url).toURL().openConnection();
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception ex) {
            return false;
        }
    }

    public static ClonedProgrammingSubmission cloneViaSSH(Assessment assessment, Path workspacePath)
            throws ArtemisClientException {
        // We need to switch the classloader here (same as
        // https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html#using-serviceloader)
        // Somewhere deep in the auth libs, an instanceof check is performed, which returns false in
        // some cases where the same class was loaded with two different class loaders (the plugin
        // and the platform ones)
        var currentThread = Thread.currentThread();
        var originalClassLoader = currentThread.getContextClassLoader();
        var pluginClassLoader = PluginState.getInstance().getClass().getClassLoader();
        try {
            currentThread.setContextClassLoader(pluginClassLoader);
            return assessment.getSubmission().cloneViaSSHInto(workspacePath);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    public static boolean isSubmissionStarted(ProgrammingSubmission submission) {
        return !submission.isSubmitted()
                && submission.getLatestResult().isPresent()
                && submission.getLatestResult().get().assessmentType() != AssessmentType.AUTOMATIC;
    }

    public static void displayGenericErrorBalloon(String title, String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("IntelliGrade Notifications")
                .createNotification(content, NotificationType.ERROR)
                .setTitle(title)
                .notify(null);
    }

    public static void displayGenericWarningBalloon(String title, String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("IntelliGrade Notifications")
                .createNotification(content, NotificationType.WARNING)
                .setTitle(title)
                .notify(null);
    }

    public static void displayGenericInfoBalloon(String title, String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("IntelliGrade Notifications")
                .createNotification(content, NotificationType.INFORMATION)
                .setTitle(title)
                .notify(null);
    }

    public static void displayNetworkErrorBalloon(String content, ArtemisNetworkException cause) {
        displayGenericErrorBalloon("Network Error", content + " (" + cause.getMessage() + ")");
    }

    public static void displayNoAssessmentBalloon() {
        displayGenericWarningBalloon("No active assessment", "Please start an assessment first.");
    }

    public static void displayFinishAssessmentFirstBalloon() {
        displayGenericWarningBalloon(
                "Finish assessment first",
                "Please finish the current assessment first. If you do not want to, please cancel it.");
    }
}
