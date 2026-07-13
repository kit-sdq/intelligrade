/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.listeners;

import java.util.EventListener;

import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import edu.kit.kastel.sdq.intelligrade.state.ArtemisConnectionState;

/**
 * Can be subscribed to, to be notified when the artemis connection changes.
 */
@FunctionalInterface
public interface ArtemisConnectionListener extends EventListener {
    @Topic.ProjectLevel
    Topic<ArtemisConnectionListener> TOPIC =
            Topic.create("Artemis connection state changed", ArtemisConnectionListener.class);

    @RequiresEdt
    void connectionStateChanged(ArtemisConnectionState state);
}
