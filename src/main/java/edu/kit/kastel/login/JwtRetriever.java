package edu.kit.kastel.login;

import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefCookie;
import com.intellij.ui.jcef.JBCefCookieManager;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class JwtRetriever extends Thread {

  private static final String JWT_COOKIE_KEY = "jwt";

  private final JBCefBrowser browser;

  public JwtRetriever(JBCefBrowser browser) {
    this.browser = browser;
  }

  @Override
  public void run() {

    JBCefCookieManager cookieManager = browser.getJBCefCookieManager();
    Future<List<JBCefCookie>> cookies = cookieManager.getCookies(
            ArtemisSettingsState.getInstance().getArtemisInstanceUrl(),
            true
    );

    try {
      //get all cookies, search for the jwt and update it in the settings if necessary
      cookies.get().stream()
              .filter(cookie -> cookie.getName().equals(JWT_COOKIE_KEY))
              .forEach(cookie -> {
                String jwt = cookie.getValue();
                if (!jwt.equals(ArtemisSettingsState.getInstance().getArtemisAuthJWT())) {
                  ArtemisSettingsState.getInstance().setArtemisAuthJWT(jwt);
                  this.browser.getJBCefClient().dispose();
                  this.browser.dispose();
                }
              });
    } catch (InterruptedException | ExecutionException exception) {
      this.interrupt();
    }
  }
}
