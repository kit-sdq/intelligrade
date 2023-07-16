package edu.kit.kastel.extensions.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "edu.kit.kastel.extensions.ArtemisSettingsState",
        storages = @Storage("IntelliGradeSettings.xml")
)
public class ArtemisSettingsState implements PersistentStateComponent<ArtemisSettingsState> {

  private static final String PASSWORD_STORE_KEY = "artemisPassword";
  private static final String CREDENTIALS_PATH = "edu.kit.kastel.intelligrade.artemisCredentials";

  private String username = "";
  private String artemisInstanceUrl = "https://artemis.praktomat.cs.kit.edu";
  private String selectedGradingConfigPath = "";


  public static ArtemisSettingsState getInstance() {
    return ApplicationManager.getApplication().getService(ArtemisSettingsState.class);
  }

  /**
   * @return a component state. All properties, public and annotated fields are serialized.
   * Only values which differ from the default (i.e. the value of newly instantiated class) are serialized.
   * {@code null} value indicates that the returned state won't be stored, as a result previously stored state will be used.
   * @see XmlSerializer
   */
  @Override
  public @Nullable ArtemisSettingsState getState() {
    return this;
  }

  /**
   * This method is called when a new component state is loaded.
   * The method can and will be called several times if config files are externally changed while the IDE is running.
   * <p>
   * State object should be used directly, defensive copying is not required.
   *
   * @param state loaded component state
   * @see XmlSerializerUtil#copyBean(Object, Object)
   */
  @Override
  public void loadState(@NotNull ArtemisSettingsState state) {
    XmlSerializerUtil.copyBean(state, this);
  }


  private CredentialAttributes createCredentialAttributes(String key) {
    return new CredentialAttributes(
            CredentialAttributesKt.generateServiceName(CREDENTIALS_PATH, key)
    );
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Get the password for the Artemis instance from the IDEs CredentialStore
   *
   * @return the Password stored under the key {@value PASSWORD_STORE_KEY}
   */
  public String getArtemisPassword() {
    CredentialAttributes credentialAttributes = createCredentialAttributes(PASSWORD_STORE_KEY);
    return PasswordSafe.getInstance().getPassword(credentialAttributes);
  }

  /**
   * Store the provided Password securely into the IDEs Credential Store under the key {@value PASSWORD_STORE_KEY}
   *
   * @param artemisPassword the password to be stored
   */
  public void setArtemisPassword(String artemisPassword) {
    CredentialAttributes credentialAttributes = createCredentialAttributes(PASSWORD_STORE_KEY);
    Credentials credentials = new Credentials(username, artemisPassword);
    PasswordSafe.getInstance().set(credentialAttributes, credentials);
  }

  public String getArtemisInstanceUrl() {
    return artemisInstanceUrl;
  }

  public void setArtemisInstanceUrl(String artemisInstanceUrl) {
    this.artemisInstanceUrl = artemisInstanceUrl;
  }

  public String getSelectedGradingConfigPath() {
    return selectedGradingConfigPath;
  }

  public void setSelectedGradingConfigPath(String selectedGradingConfigPath) {
    this.selectedGradingConfigPath = selectedGradingConfigPath;
  }
}
