/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.List;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JSeparator;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.dsl.builder.components.DslLabel;
import com.intellij.ui.dsl.builder.components.DslLabelType;
import com.intellij.util.ui.JBFont;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SplashDialog extends DialogWrapper {
    private static final TemporalAmount SPLASH_INTERVAL = Duration.ofMinutes(60);

    private static final List<String> REMINDER_LINES = List.of(
            "<span style=\"\">Be fair.</span> <span style=\"color: %s\">Follow our official grading guidelines, not your own style preferences. Only deduct points if there is a matching button.</span>",
            "<span style=\"\">Be nice.</span> <span style=\"color: %s\">Nobody submits bad code on purpose or to upset you. Also - it's okay to deduct no points :)</span>",
            "<span style=\"\">Ask!</span> <span style=\"color: %1$s\">- if you are unsure about anything. Everyone is on Element. Also check out the </span><a href=\"https://sdq.kastel.kit.edu/programmieren/Hauptseite\">Wiki</a><span style=\"color: %1$s\">.</span>");

    private static final List<String> KEYBINDING_LINES = List.of(
            "Add Annotation  <span style=\"color: %s\">Press Button or Alt + A</span>",
            "Add Custom Message  <span style=\"color: %s\">Hold Ctrl</span>");

    private static Instant lastShown = Instant.EPOCH;

    public static void showMaybe() {
        if (Instant.now().isAfter(lastShown.plus(SPLASH_INTERVAL))) {
            lastShown = Instant.now();
            ApplicationManager.getApplication().invokeLater(() -> new SplashDialog().show());
        }
    }

    private SplashDialog() {
        super((Project) null);

        this.setTitle("Before You Start");
        this.setModal(true);
        this.init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        var panel = new JBPanel<>(new MigLayout("wrap 1, fill, gapy 15", "[grow]"));

        for (var line : REMINDER_LINES) {
            panel.add(createHtmlLabel(line.formatted(IntellijUtil.colorToCSS(JBColor.GRAY))), "growx");
        }

        panel.add(new JSeparator(), "growx");

        for (var line : KEYBINDING_LINES) {
            panel.add(createHtmlLabel(line.formatted(IntellijUtil.colorToCSS(JBColor.GRAY))), "growx");
        }

        return panel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[] {this.myOKAction};
    }

    @SuppressWarnings("UnstableApiUsage")
    private static JComponent createHtmlLabel(String content) {
        // Using DslLabel since it has proper HTML link support
        var label = new DslLabel(DslLabelType.LABEL);
        label.setFont(JBFont.regular().biggerOn(2.0f));
        label.setText(content);
        label.setAction(e -> BrowserUtil.browse(e.getURL()));
        return label;
    }
}
