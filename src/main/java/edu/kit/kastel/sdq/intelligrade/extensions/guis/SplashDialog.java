package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBFont;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JSeparator;
import java.util.List;

public class SplashDialog extends DialogWrapper {
    private static final List<String> REMINDER_LINES = List.of(
            "<html><span style=\"font-weight: bold\">Be fair.</span> Follow our official grading guidelines, not your own style preferences. Only deduct points if there is a matching button.</html>",
            "<html><span style=\"font-weight: bold\">Be nice.</span> Nobody submits bad code on purpose to upset you. Also - it's okay to deduct no points :)</html>",
            "<html><span style=\"font-weight: bold\">Ask!</span> - if you are unsure about something. Everyone is on Element. Also check out the <a href=\"https://sdq.kastel.kit.edu/programmieren/Hauptseite\">Wiki</a>.</html>"
    );

    private static final List<String> KEYBINDING_LINES = List.of(
            "<html>Add Annotation <span style=\"color: %s\">Alt + A</span></html>",
            "<html>Add Custom Message <span style=\"color: %s\">Hold Ctrl</span></html>"
    );

    public SplashDialog() {
        super((Project) null);

        this.setTitle("Just to Remind You...");
        this.setModal(true);
        this.init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        var panel = new JBPanel<>(new MigLayout("wrap 1, fill, gapy 15", "[grow]"));

        for (var line : REMINDER_LINES) {
            panel.add(new JBLabel(line).withFont(splashFont()), "growx");
        }

        panel.add(new JSeparator(), "growx");

        for (var line : KEYBINDING_LINES) {
            panel.add(new JBLabel(line.formatted(colorToCSS(JBColor.GRAY))).withFont(splashFont()), "growx");
        }

        return panel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{this.myOKAction};
    }

    private static String colorToCSS(JBColor color) {
        return "rgb(%d, %d, %d)".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static JBFont splashFont() {
        return JBFont.regular().biggerOn(2.0f);
    }
}
