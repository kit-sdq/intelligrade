/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.settings;

import java.awt.Color;
import java.util.Objects;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.util.xmlb.Converter;
import org.jspecify.annotations.NonNull;

/**
 * This class represents a color in a format that is supported by the plugin.
 * <p>
 * A dedicated class is used instead of the {@link JBColor} to allow for future changes
 * and extensions like additional color formats.
 */
public class ThemeColor {
    private final Color brightColor;
    private final Color darkColor;

    public ThemeColor(Color brightColor, Color darkColor) {
        this.brightColor = brightColor;
        this.darkColor = darkColor;
    }

    public static ThemeColor yellow() {
        return new ThemeColor(Color.yellow, new Color(138, 138, 0));
    }

    public static ThemeColor green() {
        return new ThemeColor(Color.green, new Color(98, 150, 85));
    }

    public static ThemeColor magenta() {
        return new ThemeColor(Color.magenta, new Color(151, 118, 169));
    }

    public Color getBrightColor() {
        return brightColor;
    }

    public Color getDarkColor() {
        return darkColor;
    }

    /**
     * Returns the color that should be used based on the current theme.
     *
     * @return the color for the current theme
     */
    public Color toColor() {
        // Will choose the color based on the current theme.
        if (JBColor.isBright()) {
            return this.brightColor;
        } else {
            return this.darkColor;
        }
    }

    public JBColor toJBColor() {
        return new JBColor(brightColor, darkColor);
    }

    @Override
    public final boolean equals(Object object) {
        if (!(object instanceof ThemeColor that)) return false;

        return Objects.equals(brightColor, that.brightColor) && Objects.equals(darkColor, that.darkColor);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(brightColor);
        result = 31 * result + Objects.hashCode(darkColor);
        return result;
    }

    /**
     * By default, the ThemeColor class can not be serialized by IntelliJ's serialization mechanism.
     * <p>
     * This class provides a way to serialize and deserialize ThemeColor instances.
     */
    public static class ThemeColorConverter extends Converter<ThemeColor> {
        private static final Logger LOG = Logger.getInstance(ThemeColorConverter.class);
        private static final String SERIALIZED_DELIMITER = ";";
        private static final int REGULAR_COLOR_INDEX = 0;
        private static final int DARK_COLOR_INDEX = 1;
        private static final int NUMBER_OF_FIELDS = 2;

        @Override
        public ThemeColor fromString(@NonNull String value) {
            String[] serializedValues = value.split(SERIALIZED_DELIMITER, -1);

            // In previous versions colors were stored as a single int color.
            //
            // When an old setting is loaded, it will use the below as fallback:
            if (serializedValues.length == 1) {
                Color regularColor = parseColor(serializedValues[0]);
                LOG.warn("An old color format was provided: " + regularColor);
                return new ThemeColor(regularColor, regularColor);
            }

            if (serializedValues.length != NUMBER_OF_FIELDS) {
                throw new IllegalArgumentException("Invalid serialized value: " + value);
            }

            Color regularColor = parseColor(serializedValues[REGULAR_COLOR_INDEX]);
            Color darkColor = parseColor(serializedValues[DARK_COLOR_INDEX]);

            return new ThemeColor(regularColor, darkColor);
        }

        private static @NonNull Color parseColor(String string) {
            return new Color(Integer.parseInt(string), true);
        }

        @Override
        public String toString(@NonNull ThemeColor color) {
            String[] serializedValues = new String[NUMBER_OF_FIELDS];

            serializedValues[REGULAR_COLOR_INDEX] = String.valueOf(color.brightColor.getRGB());
            serializedValues[DARK_COLOR_INDEX] = String.valueOf(color.darkColor.getRGB());

            return String.join(SERIALIZED_DELIMITER, serializedValues);
        }
    }
}
