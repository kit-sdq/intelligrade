/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.extensions;

import java.awt.Color;

import javax.swing.JComponent;

import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import edu.kit.kastel.sdq.intelligrade.widgets.TextBuilder;
import net.miginfocom.swing.MigLayout;

public class Labelled extends JBPanel<JBPanel<?>> {
    private final JBTextArea label;

    public Labelled(JComponent component, String labelText, LabelKind kind) {
        super(new MigLayout("wrap 1", "[grow]", "[grow][]"));

        this.add(component, "grow");

        // Effectively a JLabel, but works with multi-line text
        this.label = TextBuilder.immutable(labelText).foreground(kind.color).textArea();

        // Without the explicit width and hmin constraints, the text area would not shrink
        // properly/and or ignore the border
        this.add(this.label, "grow, width 10:10, hmin 1");
    }

    public void changeLabelText(String newText, LabelKind kind) {
        this.label.setText(newText);
        this.label.setForeground(kind.color);
    }

    public enum LabelKind {
        HINT(JBUI.CurrentTheme.ContextHelp.FOREGROUND),
        ERROR(JBUI.CurrentTheme.Label.errorForeground()),
        WARNING(JBUI.CurrentTheme.Label.warningForeground());

        private final Color color;

        LabelKind(Color color) {
            this.color = color;
        }

        public Color color() {
            return color;
        }
    }
}
