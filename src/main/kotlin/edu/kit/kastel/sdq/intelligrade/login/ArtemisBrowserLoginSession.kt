// Licensed under EPL-2.0 2026.
package edu.kit.kastel.sdq.intelligrade.login

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefCookie
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import edu.kit.kastel.sdq.intelligrade.state.ArtemisConnectionService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<ArtemisBrowserLoginSession>()

internal class ArtemisBrowserLoginSession(
    private val project: Project,
    private val artemisUrl: String,
) {
    suspend fun requestJwtCookie(): JBCefCookie =
        coroutineScope {
            val result = CompletableDeferred<JBCefCookie>()
            val loginPopup =
                withContext(Dispatchers.EDT) {
                    open(result, this@coroutineScope)
                }
            val pollingJob =
                launch(Dispatchers.IO) {
                    loginPopup.cefReady.await()
                    while (isActive && !result.isCompleted) {
                        tryCompleteFromCookies(result, loginPopup.cefReady)
                        delay(COOKIE_CHECK_INTERVAL_MS.milliseconds)
                    }
                }

            try {
                result.await()
            } finally {
                pollingJob.cancelAndJoin()
                withContext(Dispatchers.EDT) {
                    if (!loginPopup.popup.isDisposed) {
                        loginPopup.popup.closeOk(null)
                    }

                    Disposer.dispose(loginPopup.browser)
                }
            }
        }

    private data class LoginPopup(
        val popup: JBPopup,
        val browser: JBCefBrowser,
        val cefReady: CompletableDeferred<Unit>,
    )

    @RequiresEdt
    private fun open(
        result: CompletableDeferred<JBCefCookie>,
        scope: CoroutineScope,
    ): LoginPopup {
        val browser =
            JBCefBrowser
                .createBuilder()
                // offscreen rendering is problematic on Linux
                .setOffScreenRendering(false)
                .build()

        val cefReady: CompletableDeferred<Unit> = CompletableDeferred()

        browser.jbCefClient.addLoadHandler(
            JwtCookieLoadHandler {
                scope.launch(Dispatchers.IO) {
                    tryCompleteFromCookies(result, cefReady)
                }
            },
            browser.cefBrowser,
        )

        CefApp.getInstance().onInitialization {
            cefReady.complete(Unit)
            scope.launch(Dispatchers.IO) {
                val deleteFuture = browser.jbCefCookieManager.deleteCookies(null, null)
                try {
                    deleteFuture.get(COOKIE_VISIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (exception: Exception) {
                    LOG.debug(exception)
                } finally {
                    deleteFuture.cancel(false)
                }

                withContext(Dispatchers.EDT) {
                    browser.loadURL(artemisUrl)
                }
            }
        }

        val browserComponent = browser.component
        browserComponent.preferredSize = JBUI.size(720, 1000)

        val popup =
            JBPopupFactory
                .getInstance()
                .createComponentPopupBuilder(browserComponent, browserComponent)
                .setTitle("Artemis Login")
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(true)
                .setCancelButton(IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered))
                .setCancelOnClickOutside(false)
                .setCancelKeyEnabled(true)
                .setNormalWindowLevel(true)
                .setDimensionServiceKey(project, javaClass.name, false)
                .addListener(
                    object : JBPopupListener {
                        override fun onClosed(event: LightweightWindowEvent) {
                            if (!event.isOk) {
                                result.completeExceptionally(LoginCanceledException("Artemis login was cancelled"))
                            }
                        }
                    },
                ).createPopup()

        popup.showCenteredInCurrentWindow(project)
        return LoginPopup(popup, browser, cefReady)
    }

    private suspend fun tryCompleteFromCookies(
        result: CompletableDeferred<JBCefCookie>,
        cefReady: CompletableDeferred<Unit>,
    ) {
        if (result.isCompleted) {
            return
        }

        cefReady.await()
        findJwtCookie()?.let {
            result.complete(it)
        }
    }

    private suspend fun findJwtCookie(): JBCefCookie? =
        withContext(Dispatchers.IO) {
            val cookieFuture =
                JBCefBrowserBase
                    .getGlobalJBCefCookieManager()
                    .getCookies(artemisUrl, true)

            try {
                cookieFuture
                    .get(COOKIE_VISIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .firstOrNull { it.name == ArtemisConnectionService.JWT_COOKIE_KEY }
            } catch (exception: Exception) {
                LOG.debug(exception)
                null
            } finally {
                cookieFuture.cancel(false)
            }
        }

    private class JwtCookieLoadHandler(
        private val onLoadEnd: () -> Unit,
    ) : CefLoadHandlerAdapter() {
        override fun onLoadEnd(
            browser: CefBrowser,
            frame: CefFrame,
            httpStatusCode: Int,
        ) {
            onLoadEnd()
        }
    }

    class LoginCanceledException(
        message: String,
    ) : Exception(message)

    private companion object {
        private const val COOKIE_CHECK_INTERVAL_MS = 500L
        private const val COOKIE_VISIT_TIMEOUT_MS = 250L
    }
}
