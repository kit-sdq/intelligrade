package edu.kit.kastel.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.client.RestClientManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


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
  public static synchronized @NotNull RestClientManager getArtemisClientInstance() {
    if (artemisClient == null) {
      //retrieve settings
      ArtemisSettingsState settings = ArtemisSettingsState.getInstance();

      //create new Artemis Instance
      var artemisInstance = new RestClientManager(
              settings.getArtemisInstanceUrl(),
              settings.getUsername(),
              settings.getArtemisPassword()
      );

      //try logging in
      try {
        artemisInstance.login();
      } catch (ArtemisClientException clientException) {
        ArtemisUtils.displayLoginErrorBalloon(
                String.format("%s. This will make the grading PlugIn unusable!%n", clientException.getMessage()),
                new NotificationAction("Configure...") {
                  @Override
                  public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(null, "IntelliGrade Settings");
                  }
                }
        );
      }
      artemisClient = artemisInstance;
    }
    return artemisClient;
  }

  public static void displayLoginErrorBalloon(String msg, @Nullable AnAction fix) {
    //create Balloon notification indicating error & fix
    Notification balloon = NotificationGroupManager.getInstance()
            .getNotificationGroup("IntelliGrade Notifications")
            .createNotification(msg, NotificationType.ERROR)
            .setTitle(LOGIN_ERROR_DIALOG_TITLE);
    //add fix if available
    if (fix != null) balloon.addAction(fix);

    balloon.notify(null);
  }

  public static void displayGenericErrorBalloon(String balloonContent){
    NotificationGroupManager.getInstance()
            .getNotificationGroup("IntelliGrade Notifications")
            .createNotification(balloonContent, NotificationType.ERROR)
            .setTitle(ArtemisUtils.GENERIC_ARTEMIS_ERROR_TITLE)
            .notify(null);
  }

}
