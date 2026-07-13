/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.utils;

import org.jspecify.annotations.Nullable;

/** Shared validation rules for credentials used to log in to Artemis. */
public final class ArtemisLoginValidator {
    private ArtemisLoginValidator() {}

    public static @Nullable String validateUsername(@Nullable String username) {
        return username == null || username.isBlank() ? "Username must not be empty." : null;
    }

    public static @Nullable String validatePassword(@Nullable String password) {
        return password == null || password.isEmpty() ? "Password must not be empty." : null;
    }
}
