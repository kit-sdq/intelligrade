/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.login;

import java.awt.GridLayout;
import java.awt.Toolkit;
import java.util.concurrent.Future;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import com.intellij.ui.jcef.JBCefCookie;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import org.cef.handler.CefFocusHandler;
import org.jetbrains.annotations.NotNull;

public final class CefUtils {

    private static final double BROWSER_WINDOW_SCALE_FACTOR = 0.75f;
    private static final String BROWSER_LOGIN_TITLE = "Artemis log in";

    private CefUtils() {
        throw new IllegalAccessError("Utility Class");
    }

    /**
     * Create and display a Window containing a JBCef Window to request login.
     *
     * @return A future on the JWT Cookie to log in
     */
    public static @NotNull Future<JBCefCookie> jcefBrowserLogin() throws InterruptedException {

        // create browser and browser Client
        JBCefClient browserClient = JBCefApp.getInstance().createClient();
        JBCefBrowser browser = JBCefBrowser.createBuilder()
                .setClient(browserClient)
                .setUrl(ArtemisSettingsState.getInstance().getArtemisInstanceUrl())
                .build();

        // add a handler to the Browser to be run if a page is loaded
        CefWindowLoadHandler loadHandler = new CefWindowLoadHandler(browser);
        browserClient.addLoadHandler(loadHandler, browser.getCefBrowser());

        // set focus handler because it gets invoked sometimes and causes NullPE otherwise
        CefFocusHandler focusHandler = new CefWindowFocusHandler();
        browserClient.addFocusHandler(focusHandler, browser.getCefBrowser());

        // create window, display it and navigate to log in URL
        createWindow(browser);
        return loadHandler.getCookieFuture();
    }

    private static void createWindow(@NotNull JBCefBrowser browserToAdd) {
        // build and display browser window
        JFrame browserContainerWindow = new JFrame(BROWSER_LOGIN_TITLE);
        browserContainerWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        browserContainerWindow.setSize(
                (int) Math.ceil(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * BROWSER_WINDOW_SCALE_FACTOR),
                (int) Math.ceil(Toolkit.getDefaultToolkit().getScreenSize().getHeight() * BROWSER_WINDOW_SCALE_FACTOR));
        JPanel browserContainer = new JPanel(new GridLayout(1, 1));
        browserContainerWindow.add(browserContainer);
        browserContainer.add(browserToAdd.getComponent());
        browserContainerWindow.setVisible(true);
    }
}
