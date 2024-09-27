/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.login;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefFocusHandler;

/**
 * This Class exists solely because having a focus handler seems to be required
 * We do not want to do anything on focus nor do we care about focus.
 */
public class CefWindowFocusHandler implements CefFocusHandler {
    @Override
    public void onTakeFocus(CefBrowser cefBrowser, boolean b) {
        // Nothing to do
    }

    @Override
    public boolean onSetFocus(CefBrowser cefBrowser, FocusSource focusSource) {
        return false;
    }

    @Override
    public void onGotFocus(CefBrowser cefBrowser) {
        // Nothing to do
    }
}
