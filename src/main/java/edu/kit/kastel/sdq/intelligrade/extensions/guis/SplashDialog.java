/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.List;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JSeparator;

import com.intellij.codeInsight.hints.presentation.MouseButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import edu.kit.kastel.sdq.intelligrade.utils.KeyPress;
import edu.kit.kastel.sdq.intelligrade.widgets.TextBuilder;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class SplashDialog extends DialogWrapper {
    private static final Color SUBTEXT_COLOR = JBUI.CurrentTheme.ContextHelp.FOREGROUND;
    private static final TemporalAmount SPLASH_INTERVAL = Duration.ofMinutes(60);

    private static final List<TextBuilder.HtmlTextBuilder> REMINDER_LINES = List.of(
            TextBuilder.htmlText("Be fair. ")
                    .addText(
                            "Follow our official grading guidelines, not your own style preferences. Only deduct points if there is a matching button.",
                            SUBTEXT_COLOR),
            TextBuilder.htmlText("Be nice. ")
                    .addText(
                            "Nobody submits bad code on purpose or to upset you. Also - it's okay to deduct no points :)",
                            SUBTEXT_COLOR),
            TextBuilder.htmlText("Ask! ")
                    .addText(
                            "- if you are unsure about anything. Everyone is on Element. Also check out the ",
                            SUBTEXT_COLOR)
                    .addLink("Wiki", "https://s.kit.edu/wiki")
                    .addText(".", SUBTEXT_COLOR));

    private static final List<TextBuilder.HtmlTextBuilder> KEYBINDING_LINES = List.of(
            TextBuilder.htmlText("Add Annotation  ")
                    .addKeyShortcut(KeyPress.click(MouseButton.Left))
                    .addText(" on button or press ", SUBTEXT_COLOR)
                    .addKeyShortcut(KeyPress.of(KeyEvent.VK_ALT), KeyPress.of(KeyEvent.VK_A)),
            TextBuilder.htmlText("Add Custom Message  ")
                    .addKeyShortcut(KeyPress.of(KeyEvent.VK_CONTROL), KeyPress.click(MouseButton.Left))
                    .addText(" on button.", SUBTEXT_COLOR));

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
            panel.add(line.font(JBFont.regular().biggerOn(2.0f)).label(), "growx");
        }

        panel.add(new JSeparator(), "growx");

        for (var line : KEYBINDING_LINES) {
            panel.add(line.font(JBFont.regular().biggerOn(2.0f)).label(), "growx");
        }

        return panel;
    }

    @Override
    protected Action @NonNull [] createActions() {
        return new Action[] {this.myOKAction};
    }
}
