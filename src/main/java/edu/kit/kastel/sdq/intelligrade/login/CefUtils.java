/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.login;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.swing.SwingUtilities;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import com.intellij.ui.jcef.JBCefCookie;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import org.cef.CefApp;
import org.cef.handler.CefFocusHandler;

public final class CefUtils {
    private static JBCefClient browserClient = JBCefApp.getInstance().createClient();

    static {
        // TODO choose a different disposer, see
        // https://plugins.jetbrains.com/docs/intellij/disposers.html?from=IncorrectParentDisposable#choosing-a-disposable-parent
        Disposer.register(ApplicationManager.getApplication(), browserClient);
    }

    private CefUtils() {
        throw new IllegalAccessError("Utility Class");
    }

    /**
     * Reset the CEF Browsers cookies to remove the artemis login token
     */
    public static void resetCookies() {

        // offscreen rendering is problematic on Linux
        JBCefBrowser browser = JBCefBrowser.createBuilder()
                .setClient(browserClient)
                .setOffScreenRendering(false)
                .build();

        // clear cookies
        CefApp.getInstance().onInitialization(state -> {
            browser.getJBCefCookieManager().deleteCookies(null, null);
        });
        browser.dispose();
    }

    /**
     * Create and display a Window containing a JBCef Window to request login. Call this on the EDT!
     *
     * @return A future on the JWT Cookie to log in. Don't await it on the EDT.
     */
    public static CompletableFuture<JBCefCookie> jcefBrowserLogin() {

        // offscreen rendering is problematic on Linux
        JBCefBrowser browser = JBCefBrowser.createBuilder()
                .setClient(browserClient)
                .setOffScreenRendering(false)
                .setUrl(ArtemisSettingsState.getInstance().getArtemisInstanceUrl())
                .build();

        // set focus handler because it gets invoked sometimes and causes NullPE otherwise
        CefFocusHandler focusHandler = new CefWindowFocusHandler();
        browserClient.addFocusHandler(focusHandler, browser.getCefBrowser());

        var jwtFuture = new CompletableFuture<JBCefCookie>();

        SwingUtilities.invokeLater(() -> {
            // create window, display it and navigate to log in URL
            var window = new CefDialog(browser);
            window.show();

            JwtRetriever jwtRetriever = new JwtRetriever(browser, window);
            browserClient.addLoadHandler(jwtRetriever, browser.getCefBrowser());

            // Wait for CEF initialization
            CefApp.getInstance()
                    .onInitialization(state -> jwtFuture.completeAsync(() -> {
                        try {
                            return jwtRetriever.getJwtCookie();
                        } catch (Exception ex) {
                            throw new CompletionException(ex);
                        }
                    }));
        });

        return jwtFuture;
    }
}
