package edu.kit.kastel.utils;

import com.intellij.ui.JBColor;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.client.RestClientManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.JOptionPane;

public final class ArtemisUtils {
  private ArtemisUtils() {
    throw new IllegalAccessError("Utility Class Constructor");
  }

  public static @NotNull RestClientManager getNewArtemisInstance() throws ArtemisClientException {
    //retrieve settings
    ArtemisSettingsState settings = ArtemisSettingsState.getInstance();

    //create new Artemis Instance
    var artemisInstance = new RestClientManager(
            settings.getArtemisInstanceUrl(),
            settings.getUsername(),
            settings.getArtemisPassword()
    );

    //try logging in
    artemisInstance.login();

    return artemisInstance;
  }
}
