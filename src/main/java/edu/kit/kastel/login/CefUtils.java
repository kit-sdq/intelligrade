/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.login;

import java.awt.GridLayout;
import java.awt.Toolkit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import com.intellij.ui.jcef.JBCefCookie;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import org.cef.CefApp;
import org.cef.handler.CefFocusHandler;
import org.jetbrains.annotations.NotNull;

public final class CefUtils {

    private static final double BROWSER_WINDOW_SCALE_FACTOR = 0.75f;
    private static final String BROWSER_LOGIN_TITLE = "Artemis log in";

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
     * Create and display a Window containing a JBCef Window to request login. Call this on the EDT!
     *
     * @return A future on the JWT Cookie to log in. Don't await it on the EDT.
     */
    public static CompletableFuture<JBCefCookie> jcefBrowserLogin() {

        if (browserClient == null) {
            browserClient = JBCefApp.getInstance().createClient();
        }

        JBCefBrowser browser = JBCefBrowser.createBuilder()
                .setClient(browserClient)
                .setOffScreenRendering(false)
                .setUrl(ArtemisSettingsState.getInstance().getArtemisInstanceUrl())
                .build();

        // TODO the following code deletes the jwt cookie, which is needed for a "fresh" login
        // TODO add this somewhere where it is useful
        // CefApp.getInstance().onInitialization(state -> {
        //     browser.getJBCefCookieManager().deleteCookies(null, null);
        // });

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
            CefApp.getInstance().onInitialization(state -> {
                jwtFuture.completeAsync(() -> {
                    try {
                        return jwtRetriever.getJwtCookie();
                    } catch (Exception ex) {
                        throw new CompletionException(ex);
                    }
                });
            });
        });

        return jwtFuture;
    }

    private static JFrame createWindow(@NotNull JBCefBrowser browserToAdd) {
        // Make sure to do all the GUI work on the EDT
        JFrame browserContainerWindow = new JFrame(BROWSER_LOGIN_TITLE);
        browserContainerWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        browserContainerWindow.setSize(
                (int) Math.ceil(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * BROWSER_WINDOW_SCALE_FACTOR),
                (int) Math.ceil(Toolkit.getDefaultToolkit().getScreenSize().getHeight() * BROWSER_WINDOW_SCALE_FACTOR));
        JPanel browserContainer = new JPanel(new GridLayout(1, 1));
        browserContainer.add(browserToAdd.getComponent());
        browserContainerWindow.add(browserContainer);
        browserContainerWindow.setAlwaysOnTop(true);
        browserContainerWindow.setVisible(true);
        new CefDialog(browserToAdd).show();
        return browserContainerWindow;
    }
}
