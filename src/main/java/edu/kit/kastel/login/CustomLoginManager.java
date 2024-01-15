package edu.kit.kastel.login;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.ui.jcef.JBCefApp;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.utils.ArtemisUtils;
import org.jetbrains.annotations.NotNull;

public class CustomLoginManager extends edu.kit.kastel.sdq.artemis4j.client.LoginManager {

  private static final String JFCE_UNAVAILABLE_ERRORMESSAGE =
          "Embedded browser unavailable. Please log in using username and password.";


  public CustomLoginManager(String hostname, String username, String password) {
    super(hostname, username, password);
  }

  @Override
  public void login() throws ArtemisClientException {
    if (this.hostname.isBlank()) {
      throw new ArtemisClientException("Login without hostname is impossible");
    } else if (this.username.isBlank() || this.password.isBlank()) {
      //check log in data and open login Browser iff needed
      //only do browser login if no token is set and either username or password is not set as well
      if (ArtemisSettingsState.getInstance().getArtemisAuthJWT() == null
              || ArtemisSettingsState.getInstance().getArtemisAuthJWT().isBlank()
      ) {
        if (JBCefApp.isSupported()) {
          //open the JCEF Browser in a new Panel to allow user to log in
          CefUtils.jcefBrowserLogin();
        } else {
          //if JCEF is unavailable suggest usage of conventional login
          displayJCEFUnavailableError();
        }
      }
      //get Token from Settings, which should be set now
      this.token = ArtemisSettingsState.getInstance().getArtemisAuthJWT();
    } else {
      this.token = this.loginViaUsernameAndPassword();
    }
    this.assessor = this.fetchAssessor();
  }


  private void displayJCEFUnavailableError() {
    ArtemisUtils.displayLoginErrorBalloon(
            JFCE_UNAVAILABLE_ERRORMESSAGE,
            new NotificationAction("Log in...") {
              @Override
              public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                ShowSettingsUtil
                        .getInstance()
                        .showSettingsDialog(null, "IntelliGrade Settings");
              }
            }
    );
  }
}
