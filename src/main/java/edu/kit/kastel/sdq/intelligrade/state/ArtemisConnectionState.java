/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.state;

import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnection;

public sealed interface ArtemisConnectionState {
    record Disconnected() implements ArtemisConnectionState {}

    record Connecting() implements ArtemisConnectionState {}

    record Connected(ArtemisConnection connection) implements ArtemisConnectionState {}

    record Failed(String message, FailureTarget target) implements ArtemisConnectionState {
        public Failed(String message) {
            this(message, FailureTarget.GENERAL);
        }
    }

    enum FailureTarget {
        GENERAL,
        URL,
        CREDENTIALS
    }
}
