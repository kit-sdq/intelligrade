package edu.kit.kastel.listeners;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenSettingsAction extends NotificationAction {
  public OpenSettingsAction(@Nullable @NlsContexts.NotificationContent String text) {
    super(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
    ShowSettingsUtil.getInstance().showSettingsDialog(null, "IntelliGrade Settings");
  }
}
