package edu.kit.kastel.listeners;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.utils.ArtemisUtils;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import java.util.List;


public class OnPlugInLoad implements AppLifecycleListener {

  private static final String LOGIN_ERROR_DIALOG_TITLE = "Error logging in!";


  @Override
  public void appFrameCreated(@NotNull List<String> commandLineArgs) {
    AppLifecycleListener.super.appFrameCreated(commandLineArgs);

    try {
      ArtemisUtils.getNewArtemisInstance();
    } catch (ArtemisClientException clientException) {

      //create Balloon notification indicating error & fix
      NotificationGroupManager.getInstance()
              .getNotificationGroup("IntelliGrade Notifications")
              .createNotification(
                      String.format("%s. This will make the grading PlugIn unusable!%n", clientException.getMessage()),
                      NotificationType.ERROR)
              .setTitle("IntelliGrade Login error")
              .addAction(new OpenSettingsAction("Configure..."))
              .notify(null);

    }
  }
}
