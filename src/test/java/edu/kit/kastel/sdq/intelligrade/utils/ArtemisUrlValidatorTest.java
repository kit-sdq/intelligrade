/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

class ArtemisUrlValidatorTest {
    @Test
    void rejectsAnEmptyUrlWithAUsefulMessage() {
        var result = ArtemisUrlValidator.validate(" ");

        assertEquals("Artemis URL must not be empty.", invalid(result).message());
    }

    @Test
    void explainsWhenTheUrlHasNoScheme() {
        var result = ArtemisUrlValidator.validate("artemis.example.edu");

        assertEquals(
                "Artemis URL must include a scheme, such as https://.",
                invalid(result).message());
    }

    @Test
    void explainsWhenTheUrlUsesAnUnsupportedScheme() {
        var result = ArtemisUrlValidator.validate("ftp://artemis.example.edu");

        assertEquals(
                "Artemis URL must use the http:// or https:// scheme.",
                invalid(result).message());
    }

    @Test
    void explainsWhenTheUrlHasNoHostname() {
        var result = ArtemisUrlValidator.validate("https://");

        assertEquals(
                "Artemis URL must contain a valid hostname.", invalid(result).message());
    }

    @Test
    void acceptsAndNormalizesAValidUrl() {
        var result = ArtemisUrlValidator.validate("  https://artemis.example.edu///  ");

        var valid = assertInstanceOf(ArtemisUrlValidator.Result.Valid.class, result);
        assertEquals("https://artemis.example.edu", valid.normalizedUrl());
    }

    private static ArtemisUrlValidator.Result.Invalid invalid(ArtemisUrlValidator.Result result) {
        return assertInstanceOf(ArtemisUrlValidator.Result.Invalid.class, result);
    }
}
