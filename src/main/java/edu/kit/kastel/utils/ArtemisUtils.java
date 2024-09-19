/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnection;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.Course;
import edu.kit.kastel.sdq.artemis4j.grading.Exam;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import org.jetbrains.annotations.Nullable;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Utility Class to handle Artemis related common tasks such as
 * creating a new client and logging in or creating Error messages.
 */
public final class ArtemisUtils {
    private static final String LOGIN_ERROR_DIALOG_TITLE = "IntelliGrade Login error";
    public static final String GENERIC_ARTEMIS_ERROR_TITLE = "Artemis Error";

    private static ArtemisConnection connection;
    private static Course activeCourse;
    private static Exam activeExam;
    private static ProgrammingExercise activeExercise;
    private static Assessment activeAssessment;

    private ArtemisUtils() {}

    public static boolean doesUrlExist(String url) {
        try {
            var connection = (HttpURLConnection) new URI(url).toURL().openConnection();
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Display an error ballon that indicates a login error.
     *
     * @param msg The message to be displayed. Should describe the login error.
     * @param fix A possible fix for the error. Should be non-null. A null value
     *            is only allowed if a fix is provided in the message.
     */
    public static void displayLoginErrorBalloon(String msg, @Nullable AnAction fix) {
        // create Balloon notification indicating error & fix
        Notification balloon = NotificationGroupManager.getInstance()
                .getNotificationGroup("IntelliGrade Notifications")
                .createNotification(msg, NotificationType.ERROR)
                .setTitle(LOGIN_ERROR_DIALOG_TITLE);
        // add fix if available
        if (fix != null) {
            balloon.addAction(fix);
        }

        balloon.notify(null);
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

    public static void displayGenericExceptionBalloon(Exception e) {
        displayGenericErrorBalloon("IntelliGrade Error", e.getMessage());
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
