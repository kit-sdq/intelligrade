package edu.kit.kastel.listeners;

import com.intellij.ide.AppLifecycleListener;
import edu.kit.kastel.utils.ArtemisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class OnPlugInLoad implements AppLifecycleListener {

  @Override
  public void appFrameCreated(@NotNull List<String> commandLineArgs) {
    AppLifecycleListener.super.appFrameCreated(commandLineArgs);
      ArtemisUtils.getArtemisClientInstance();
  }
}
