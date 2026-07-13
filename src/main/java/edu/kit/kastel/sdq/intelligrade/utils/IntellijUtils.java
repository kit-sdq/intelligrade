/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.utils;

import javax.swing.JButton;

/**
 * This is a utility class containing shared functions that simplify interactions
 * with the IntelliJ codebase.
 */
public final class IntellijUtils {
    private IntellijUtils() {}

    public static JButton createWrappingButton(String text) {
        return new JButton("<html><body style='text-align: center;'>" + text + "</body></html>");
    }
}
