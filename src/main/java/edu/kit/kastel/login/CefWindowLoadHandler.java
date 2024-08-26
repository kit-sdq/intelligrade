/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.login;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefCookie;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * Class to handle callbacks when a site in the JBCef Browser is loaded. Use to start retrieving cookies.
 */
public class CefWindowLoadHandler extends CefLoadHandlerAdapter {

    private final JBCefBrowser containingBrowser;

    private Optional<Future<JBCefCookie>> cookieFuture;

    CefWindowLoadHandler(JBCefBrowser browser) {
        this.containingBrowser = browser;
        this.cookieFuture = Optional.empty();
    }

    @Override
    public void onLoadEnd(@NotNull CefBrowser browser, CefFrame frame, int httpStatusCode) {

        // build and start a new Thread to retrieve the Cookies
        JwtRetriever cookieRetriever = new JwtRetriever(containingBrowser);
        // lock the Monitor
        synchronized (this) {
            // create future to retrieve Cookie
            // NOTE: using Callable<T> instead of Runnable<T>
            this.cookieFuture = Optional.of(Executors.newSingleThreadExecutor().submit(cookieRetriever));
            // wake up potentially waiting Threads that want to get the Cookie
            notifyAll();
        }
    }

    /**
     * Get the Future on the Cookie. Wait for it to become available if necessary.
     *
     * @return a Future on the JWT Cookie to be retrieved later
     * @throws InterruptedException if the call is interrupted while attempting to obtain the Cookie
     */
    public Future<JBCefCookie> getCookieFuture() throws InterruptedException {
        // lock monitor
        synchronized (this) {
            // wait for the cookie to become available.
            // necessary because this method might be called before onLoadEnd
            while (this.cookieFuture.isEmpty()) {
                wait();
            }
            // return content of Optional
            return cookieFuture.get();
        }
    }
}
