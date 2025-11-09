/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.utils;

import java.awt.event.KeyEvent;
import java.util.Locale;

import javax.swing.JPanel;

import com.intellij.DynamicBundle;
import com.intellij.codeInsight.hints.presentation.MouseButton;
import com.intellij.internal.statistic.eventLog.ShortcutDataProvider;
import org.jetbrains.annotations.Nullable;

public abstract class KeyPress {
    private static boolean isGerman(Locale locale) {
        return locale.getLanguage().equals(Locale.GERMANY.getLanguage());
    }

    public static KeyPress click(MouseButton button) {
        return new KeyPress() {
            @Override
            public String toString(Locale locale) {
                if (isGerman(locale)) {
                    return switch (button) {
                        case Left -> "Linksklick";
                        case Middle -> "Mittelklick";
                        case Right -> "Rechtsklick";
                    };
                }

                return switch (button) {
                    case Left -> "Left Click";
                    case Middle -> "Middle Click";
                    case Right -> "Right Click";
                };
            }
        };
    }

    public static KeyPress of(int keyCode) {
        return new KeyPress() {
            private static @Nullable String getGermanKeyName(int keyCode) {
                return switch (keyCode) {
                    case KeyEvent.VK_CONTROL -> "Strg";
                    case KeyEvent.VK_ALT -> "Alt";
                    case KeyEvent.VK_SHIFT -> "Umschalt";
                    case KeyEvent.VK_ENTER -> "Eingabetaste";
                    case KeyEvent.VK_BACK_SPACE -> "Rücktaste";
                    case KeyEvent.VK_DELETE -> "Entf";
                    case KeyEvent.VK_ESCAPE -> "Esc";
                    case KeyEvent.VK_SPACE -> "Leertaste";
                    default -> null;
                };
            }

            @Override
            @SuppressWarnings("UnstableApiUsage")
            public String toString(Locale locale) {
                KeyEvent event = new KeyEvent(
                        new JPanel(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, keyCode, (char) keyCode);
                String result = ShortcutDataProvider.getInputEventText(event, null);

                if (isGerman(locale)) {
                    String germanKeyName = getGermanKeyName(keyCode);
                    if (germanKeyName != null) {
                        result = germanKeyName;
                    }
                }

                // Handle double click shortcuts (for some reason it returns Control+Control instead of just Control)
                if (result != null && result.contains("+")) {
                    result = result.split("\\+", -1)[0].trim();
                }

                if ("Control".equalsIgnoreCase(result)) {
                    result = "Ctrl";
                }

                return result;
            }
        };
    }

    public abstract String toString(Locale locale);

    @Override
    public String toString() {
        return this.toString(DynamicBundle.getLocale());
    }
}
