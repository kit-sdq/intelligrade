/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.login;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefCookie;
import com.intellij.ui.jcef.JBCefCookieManager;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;

public class JwtRetriever implements Callable<JBCefCookie> {

    private static final String JWT_COOKIE_KEY = "jwt";

    private final JBCefBrowser browser;

    private JBCefCookie cookieRetrieved;

    public JwtRetriever(JBCefBrowser browser) {
        this.browser = browser;
    }

    @Override
    public JBCefCookie call() throws Exception {
        ArtemisSettingsState settingsStore = ArtemisSettingsState.getInstance();

        JBCefCookieManager cookieManager = browser.getJBCefCookieManager();
        Future<List<JBCefCookie>> cookies = cookieManager.getCookies(settingsStore.getArtemisInstanceUrl(), true);
        // get all cookies, search for the jwt and update it in the settings if necessary
        this.cookieRetrieved = cookies.get().stream()
                .filter(cookie -> cookie.getName().equals(JWT_COOKIE_KEY))
                .findAny().orElseThrow(() -> new IllegalStateException("No JWT cookie found"));

        String jwt = this.cookieRetrieved.getValue();
        if (!jwt.equals(settingsStore.getArtemisAuthJWT())) {
            this.browser.getJBCefClient().dispose();
            this.browser.dispose();
        }
        return cookieRetrieved;
    }
}
