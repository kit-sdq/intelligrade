/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.utils;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.format.DateTimeFormatter;

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

    public static boolean isAssessorInstructorForCurrentExercise() {
        if (PluginState.getInstance().getActiveExercise().isEmpty()) {
            return false;
        }

        try {
            var assessor = PluginState.getInstance().getAssessor();
            return assessor != null && PluginState.getInstance().getActiveExercise().get().getCourse().isInstructor(assessor);
        } catch (ArtemisNetworkException ex) {
            ArtemisUtils.displayNetworkErrorBalloon("Could not check instructor status", ex);
            return false;
        }
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
