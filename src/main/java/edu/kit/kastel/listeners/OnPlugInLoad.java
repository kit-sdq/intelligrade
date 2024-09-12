/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.listeners;

import java.util.List;

import com.intellij.ide.AppLifecycleListener;
import edu.kit.kastel.highlighter.HighlighterManager;
import edu.kit.kastel.state.PluginState;
import org.jetbrains.annotations.NotNull;

/**
 * Class that handles the events called if the PlugIn is loaded
 * such as creating a new Artemis Client.
 */
public class OnPlugInLoad implements AppLifecycleListener {

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        AppLifecycleListener.super.appFrameCreated(commandLineArgs);
        PluginState.getInstance().connect();
    }
}
