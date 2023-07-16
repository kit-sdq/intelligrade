package edu.kit.kastel.listeners;

import com.intellij.ide.AppLifecycleListener;
import edu.kit.kastel.utils.ArtemisUtils;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Class that handles the events called if the PlugIn is loaded
 * such as creating a new Artemis Client.
 */
public class OnPlugInLoad implements AppLifecycleListener {

  @Override
  public void appFrameCreated(@NotNull List<String> commandLineArgs) {
    AppLifecycleListener.super.appFrameCreated(commandLineArgs);
    ArtemisUtils.getArtemisClientInstance();
  }
}
