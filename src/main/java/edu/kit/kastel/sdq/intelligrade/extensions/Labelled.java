package edu.kit.kastel.sdq.intelligrade.extensions;

import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;

import java.awt.Color;
import java.awt.Component;

public class Labelled<T extends Component> extends JBPanel<JBPanel<?>> {
    private final T component;
    private final JBTextArea label;

    public Labelled(T component, String labelText, LabelKind kind) {
        super(new MigLayout("wrap 1", "[grow]", "[grow][]"));
        this.component = component;

        this.add(this.component, "grow");

        // Effectively a JLabel, but works with multi-line text
        this.label = new JBTextArea(labelText);
        this.label.setEditable(false);
        this.label.setLineWrap(true);
        this.label.setFocusable(false);
        this.label.setFont(JBFont.regular());
        this.label.setForeground(kind.color);

        // Without the explicit width and hmin constraints, the text area would not shrink
        // properly/and or ignore the border
        this.add(this.label, "grow, width 10:10, hmin 1");
    }

    public void changeLabelText(String newText, LabelKind kind) {
        this.label.setText(newText);
        this.label.setForeground(kind.color);
    }

    public T component() {
        return component;
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
