/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ArtemisLoginValidatorTest {
    @Test
    void rejectsAnEmptyUsername() {
        assertEquals("Username must not be empty.", ArtemisLoginValidator.validateUsername(" "));
    }

    @Test
    void rejectsAnEmptyPassword() {
        assertEquals("Password must not be empty.", ArtemisLoginValidator.validatePassword(""));
    }

    @Test
    void acceptsNonEmptyCredentials() {
        assertNull(ArtemisLoginValidator.validateUsername("alice"));
        assertNull(ArtemisLoginValidator.validatePassword("secret"));
    }
}
