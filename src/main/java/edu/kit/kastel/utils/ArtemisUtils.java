/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.login.CustomLoginManager;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.client.RestClientManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility Class to handle Artemis related common tasks such as
 * creating a new client and logging in or creating Error messages.
 */
public final class ArtemisUtils {
    private static final String LOGIN_ERROR_DIALOG_TITLE = "IntelliGrade Login error";
    public static final String GENERIC_ARTEMIS_ERROR_TITLE = "Artemis Error";

    private static RestClientManager artemisClient;

    private ArtemisUtils() {
        throw new IllegalAccessError("Utility Class Constructor");
    }

    /**
     * get an instance of the Artemis Client. Create one if necessary (singleton).
     *
     * @return the instance persisted or created
     */
    public static @NotNull RestClientManager getArtemisClientInstance() {
        if (artemisClient == null) {
            // retrieve settings
            ArtemisSettingsState settings = ArtemisSettingsState.getInstance();

            var tokenLoginManager = new CustomLoginManager(
                    settings.getArtemisInstanceUrl(), settings.getUsername(), settings.getArtemisPassword());

            // create new Artemis Instance
            var artemisInstance = new RestClientManager(settings.getArtemisInstanceUrl(), tokenLoginManager);

            // try logging in
            try {
                tokenLoginManager.login();
            } catch (ArtemisClientException clientException) {
                ArtemisUtils.displayLoginErrorBalloon(
                        String.format(
                                "%s. This will make the grading PlugIn unusable!%n", clientException.getMessage()),
                        new NotificationAction("Configure...") {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                                ShowSettingsUtil.getInstance().showSettingsDialog(null, "IntelliGrade Settings");
                            }
                        });
            }
            artemisClient = artemisInstance;
        }
        return artemisClient;
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

    /**
     * Display an error balloon that indicates a generic error message.
     *
     * @param balloonContent The message to be displayed in the error balloon
     */
    public static void displayGenericErrorBalloon(String balloonContent) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("IntelliGrade Notifications")
                .createNotification(balloonContent, NotificationType.ERROR)
                .setTitle(ArtemisUtils.GENERIC_ARTEMIS_ERROR_TITLE)
                .notify(null);
    }
}
