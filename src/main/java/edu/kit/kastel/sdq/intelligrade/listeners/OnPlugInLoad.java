/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.listeners;

import java.util.List;

import com.intellij.ide.AppLifecycleListener;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisCredentialsProvider;
import org.jspecify.annotations.NonNull;

/**
 * Will be called when the plugin is loaded to do initialization.
 */
public class OnPlugInLoad implements AppLifecycleListener {

    @Override
    public void appFrameCreated(@NonNull List<String> commandLineArgs) {
        AppLifecycleListener.super.appFrameCreated(commandLineArgs);
        // Fetching the credentials seems to be slow, therefore this is done on plugin load to reduce delays.
        ArtemisCredentialsProvider.getInstance().initialize();
    }
}
