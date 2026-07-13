// Licensed under EPL-2.0 2026.
package edu.kit.kastel.sdq.intelligrade.login

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefCookie
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import edu.kit.kastel.sdq.artemis4j.client.ArtemisInstance
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisCredentialsProvider
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState
import kotlinx.coroutines.runBlocking
import java.time.Instant

@Service(Service.Level.APP)
class ArtemisTokenLoginService {
    companion object {
        @JvmStatic
        fun getInstance(): ArtemisTokenLoginService = ApplicationManager.getApplication().getService(ArtemisTokenLoginService::class.java)
    }

    fun isBrowserLoginSupported(): Boolean = JBCefApp.isSupported()

    @RequiresBackgroundThread
    fun requestToken(
        instance: ArtemisInstance,
        project: Project,
    ): TokenLoginResult =
        runBlocking {
            check(isBrowserLoginSupported()) { "Unable to open browser window, JCEF not supported" }

            ArtemisBrowserLoginSession(project, instance.browserUrl)
                .requestJwtCookie()
                .toTokenLoginResult()
        }

    fun storeJwt(result: TokenLoginResult) {
        ArtemisCredentialsProvider.getInstance().jwt = result.token
        ArtemisSettingsState.getInstance().jwtExpiry = result.expiry
    }

    fun clearStoredJwt() {
        ArtemisCredentialsProvider.getInstance().jwt = null
        ArtemisSettingsState.getInstance().jwtExpiry = null
    }

    private fun JBCefCookie.toTokenLoginResult(): TokenLoginResult {
        val expiry = expires?.toInstant()?.takeIf { hasExpires() }
        return TokenLoginResult(value, expiry)
    }

    private val ArtemisInstance.browserUrl: String
        get() = protocol + domain

    @JvmRecord
    data class TokenLoginResult(
        val token: String,
        val expiry: Instant?,
    )
}
