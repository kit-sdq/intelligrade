package edu.kit.kastel.login;

import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import java.awt.GridLayout;
import java.awt.Toolkit;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import org.jetbrains.annotations.NotNull;

public final class CefUtils {

  private static final double BROWSER_WINDOW_SCALE_FACTOR = 0.75f;
  private static final String BROWSER_LOGIN_TITLE = "Artemis log in";


  private CefUtils() {
    throw new IllegalAccessError("Utility Class");
  }

  /**
   * Create and display a Window containing a JBCef Window to request login.
   */
  public static void jcefBrowserLogin() {

    //create browser and browser Client
    JBCefClient browserClient = JBCefApp.getInstance().createClient();
    JBCefBrowser browser = JBCefBrowser.createBuilder()
            .setClient(browserClient)
            .setUrl(ArtemisSettingsState.getInstance().getArtemisInstanceUrl())
            .build();


    //add a handler to the Browser to be run if a page is loaded
    browserClient.addLoadHandler(new CefWindowLoadHandler(browser), browser.getCefBrowser());

    //create window, display it and navigate to log in URL
    createWindow(browser);
  }

  private static void createWindow(@NotNull JBCefBrowser browserToAdd) {
    //build and display browser window
    JFrame browserContainerWindow = new JFrame(BROWSER_LOGIN_TITLE);
    browserContainerWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    browserContainerWindow.setSize(
            (int) Math.ceil(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * BROWSER_WINDOW_SCALE_FACTOR),
            (int) Math.ceil(Toolkit.getDefaultToolkit().getScreenSize().getHeight() * BROWSER_WINDOW_SCALE_FACTOR)
    );
    JPanel browserContainer = new JPanel(new GridLayout(1, 1));
    browserContainerWindow.add(browserContainer);
    browserContainer.add(browserToAdd.getComponent());
    browserContainerWindow.setVisible(true);
  }
}
