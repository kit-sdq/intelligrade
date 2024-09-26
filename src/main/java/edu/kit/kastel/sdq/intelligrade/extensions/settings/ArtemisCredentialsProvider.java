/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

@Service
public final class ArtemisCredentialsProvider {
    private static final String CREDENTIALS_PATH = "edu.kit.kastel.intelligrade.artemisCredentials";
    private static final String PASSWORD_STORE_KEY = "artemisPassword";
    private static final String JWT_STORE_KEY = "artemisAuthJWT";

    private boolean initialized = false;
    private String artemisPassword;
    private String jwt;

    public static ArtemisCredentialsProvider getInstance() {
        return ApplicationManager.getApplication().getService(ArtemisCredentialsProvider.class);
    }

    public void initialize() {
        // Prefetches the credentials, since the password store is very slow
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            var safe = PasswordSafe.getInstance();
            artemisPassword = safe.getPassword(createCredentialAttributes(PASSWORD_STORE_KEY));
            jwt = safe.getPassword(createCredentialAttributes(JWT_STORE_KEY));
            synchronized (this) {
                initialized = true;
                this.notifyAll();
            }
        });
    }

    public String getArtemisPassword() {
        waitForInitialization();
        return artemisPassword;
    }

    public void setArtemisPassword(String artemisPassword) {
        this.artemisPassword = artemisPassword;
        ApplicationManager.getApplication().executeOnPooledThread(() -> PasswordSafe.getInstance()
                .setPassword(createCredentialAttributes(PASSWORD_STORE_KEY), artemisPassword));
    }

    public String getJwt() {
        waitForInitialization();
        return jwt;
    }

    public void setJwt(String jwt) {
        this.jwt = jwt;
        ApplicationManager.getApplication().executeOnPooledThread(() -> PasswordSafe.getInstance()
                .setPassword(createCredentialAttributes(JWT_STORE_KEY), jwt));
    }

    private CredentialAttributes createCredentialAttributes(String key) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName(CREDENTIALS_PATH, key));
    }

    private void waitForInitialization() {
        synchronized (this) {
            while (!initialized) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
