package edu.kit.kastel.login;

import com.intellij.ui.jcef.JBCefBrowser;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * Class to handle callbacks when a site in the JBCef Browser is loaded. Use to start retrieving cookies.
 */
public class CefWindowLoadHandler extends CefLoadHandlerAdapter {

  private final JBCefBrowser containingBrowser;

  CefWindowLoadHandler(JBCefBrowser browser) {
    this.containingBrowser = browser;
  }

  @Override
  public void onLoadEnd(@NotNull CefBrowser browser, CefFrame frame, int httpStatusCode) {

    //build and start a new Thread to retrieve the Cookies
    JwtRetriever cookieRetriever = new JwtRetriever(containingBrowser);
    cookieRetriever.start();
  }
}
