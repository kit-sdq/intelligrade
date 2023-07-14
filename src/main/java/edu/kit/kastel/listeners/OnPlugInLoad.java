package edu.kit.kastel.listeners;

import com.intellij.ide.AppLifecycleListener;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.utils.ArtemisUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.JOptionPane;
import java.util.List;


public class OnPlugInLoad implements AppLifecycleListener {

  private static final String LOGIN_ERROR_DIALOG_TITLE = "Error logging in!";


  @Override
  public void appFrameCreated(@NotNull List<String> commandLineArgs) {
    AppLifecycleListener.super.appFrameCreated(commandLineArgs);

    try {
      ArtemisUtils.getNewArtemisInstance();
    } catch (ArtemisClientException clientException) {
      JOptionPane.showMessageDialog(
              null,
              "Error logging in to Artemis: " + clientException.getMessage(),
              LOGIN_ERROR_DIALOG_TITLE,
              JOptionPane.ERROR_MESSAGE
      );
    }
  }
}
