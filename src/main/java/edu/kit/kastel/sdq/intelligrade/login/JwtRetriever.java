/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.login;

import javax.swing.SwingUtilities;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefCookie;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisCredentialsProvider;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefCookieManager;

public class JwtRetriever extends CefLoadHandlerAdapter {
    private static final Logger LOG = Logger.getInstance(JwtRetriever.class);

    private static final String JWT_COOKIE_KEY = "jwt";

    private final JBCefBrowser browser;
    private final CefDialog window;

    private volatile JBCefCookie jwtCookie;

    public JwtRetriever(JBCefBrowser browser, CefDialog window) {
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
        var settings = ArtemisSettingsState.getInstance();
        var credentials = ArtemisCredentialsProvider.getInstance();

        String url = settings.getArtemisInstanceUrl();
        String jwt = credentials.getJwt();

        synchronized (this) {
            while (true) {
                // We may have been woken up because the cookie is available
                if (jwtCookie != null) {
                    // Can't use ApplicationManager.getApplication().invokeLater here,
                    // as this is only called on application exit on Linux in this specific case (not sure why)
                    SwingUtilities.invokeLater(() -> {
                        window.performOKAction();
                        // window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
                        this.browser.getCefBrowser().close(true);
                    });
                    return jwtCookie;
                }

                // Otherwise, visit all cookies and look for the JWT cookie
                try {
                    CefCookieManager.getGlobalManager().visitUrlCookies(url, true, (cookie, count, total, delete) -> {
                        if (cookie.name.equals(JWT_COOKIE_KEY)) {
                            if (!cookie.value.equals(jwt)) {
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
