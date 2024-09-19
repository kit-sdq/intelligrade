/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.login;

import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefCookie;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefCookieManager;

public class JwtRetriever extends CefLoadHandlerAdapter {
    private static final Logger LOG = Logger.getInstance(JwtRetriever.class);

    private static final String JWT_COOKIE_KEY = "jwt";

    private final JBCefBrowser browser;
    private final JFrame window;

    private volatile JBCefCookie jwtCookie;

    public JwtRetriever(JBCefBrowser browser, JFrame window) {
        this.browser = browser;
        this.window = window;
    }

    @Override
    public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        synchronized (this) {
            this.notifyAll();
        }
    }

    public JBCefCookie getJwtCookie() throws Exception {
        var settingsStore = ArtemisSettingsState.getInstance();
        String url = settingsStore.getArtemisInstanceUrl();

        synchronized (this) {
            while (true) {
                // We may have been woken up because the cookie is available
                if (jwtCookie != null) {
                    SwingUtilities.invokeLater(() -> {
                        window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
                        this.browser.getCefBrowser().close(true);
                    });
                    return jwtCookie;
                }

                // Otherwise, visit all cookies and look for the JWT cookie
                try {
                    CefCookieManager.getGlobalManager().visitUrlCookies(url, true, (cookie, count, total, delete) -> {
                        if (cookie.name.equals(JWT_COOKIE_KEY)) {
                            if (!cookie.value.equals(settingsStore.getArtemisAuthJWT())) {
                                synchronized (JwtRetriever.this) {
                                    jwtCookie = new JBCefCookie(cookie);
                                    this.notifyAll();
                                }
                            }
                            return false;
                        }
                        return true;
                    });
                } catch (RuntimeException e) {
                    // This can happen if the cookie manager is not yet initialized
                    // In this case, we just wait and try again
                    LOG.warn(e);
                }

                this.wait(500);
            }
        }
    }
}
