/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.widgets;

import java.awt.Color;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.dsl.builder.components.DslLabel;
import com.intellij.ui.dsl.builder.components.DslLabelType;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import edu.kit.kastel.sdq.intelligrade.utils.KeyPress;
import org.jetbrains.annotations.Nullable;

/**
 * Texts are painful in swing. There are many different widgets, all supporting different capabilities.
 * <br>
 * This tries to abstract away the differences to build a mostly universally applicable text widget.
 */
public final class TextBuilder {
    private TextBuilder() {}

    public static HtmlTextBuilder html(String text) {
        return new HtmlTextBuilder(text);
    }

    public static HtmlTextBuilder htmlText(String text) {
        return htmlText(text, null);
    }

    public static HtmlTextBuilder htmlText(String text, Color color) {
        return new HtmlTextBuilder(spanText(text, color), color);
    }

    public static TextAreaBuilder immutable(String text) {
        return TextBuilder.immutable(text, 0);
    }

    public static TextAreaBuilder immutable(String text, int width) {
        return TextBuilder.textArea(text, 0, width)
                .editable(false)
                .border(false)
                .focusable(false);
    }

    public static TextAreaBuilder textArea(String initialText) {
        return textArea(initialText, 0, 0);
    }

    public static TextAreaBuilder textArea(String initialText, int rows, int columns) {
        return new TextAreaBuilder(initialText, rows, columns).editable(true).shouldWrapLines(true);
    }

    public static class TextAreaBuilder {
        private final JBTextArea textArea;
        private int maxLines;

        private TextAreaBuilder(String text, int rows, int columns) {
            this.textArea = new JBTextArea(text, rows, columns);
            this.maxLines = 0;

            this.textArea.setFont(JBFont.regular());
            // Wrap text to the next line:
            this.textArea.setLineWrap(true);
            // Wrap full words and not just letters:
            this.textArea.setWrapStyleWord(true);

            this.textArea.setEditable(true);

            this.border(true);
        }

        /**
         * Adds an empty border around the text, this is useful for text areas.
         * <br>
         * By default, a border is added.
         *
         * @param hasBorder true if it should have a border, false otherwise
         * @return the builder
         */
        public TextAreaBuilder border(boolean hasBorder) {
            if (hasBorder) {
                Border border = JBUI.Borders.empty(6, 12);
                this.textArea.setBorder(border);
            } else {
                this.textArea.setBorder(JBUI.Borders.empty());
            }

            return this;
        }

        public TextAreaBuilder installValidator(
                Disposable parentDisposable, Function<? super JBTextArea, ? extends ValidationInfo> validator) {
            // A ComponentValidator needs to be disposed of when the parent component in which `this` is,
            // is disposed of, otherwise intellij will complain about a memory leak/invalid parent.
            new ComponentValidator(parentDisposable)
                    .withValidator(() -> validator.apply(this.textArea))
                    .andRegisterOnDocumentListener(this.textArea)
                    .installOn(this.textArea);

            // trigger the validation of the initialMessage:
            ComponentValidator.getInstance(this.textArea).ifPresent(ComponentValidator::revalidate);

            return this;
        }

        /**
         * Updates the caret position based on the given function.
         *
         * @param positionFunction a function that takes the text area and returns the new caret position
         * @return the builder
         */
        public TextAreaBuilder updateCaretPosition(Function<JBTextArea, Integer> positionFunction) {
            this.textArea.setCaretPosition(positionFunction.apply(this.textArea));
            return this;
        }

        /**
         * Sets whether the text is editable.
         * <br>
         * By default, this is true.
         *
         * @param isEditable true if it should be editable, false otherwise
         * @return the builder
         */
        public TextAreaBuilder editable(boolean isEditable) {
            this.textArea.setEditable(isEditable);
            return this;
        }

        /**
         * Sets the maximum number of lines before a scrollbar should appear.
         * <br>
         * By default, this is 0, indicating that it should never scroll.
         * Use {@link #component()} to get the final component, which will be wrapped in a scroll pane if needed.
         *
         * @param maxLines the maximum number of lines
         * @return the builder
         */
        public TextAreaBuilder maxLines(int maxLines) {
            this.maxLines = maxLines;
            return this;
        }

        /**
         * Sets whether the text area is focusable. If it is not focusable, one cannot select the text with the mouse.
         * <br>
         * By default, this is true.
         *
         * @param isFocusable true if it should be focusable, false otherwise
         * @return the builder
         */
        public TextAreaBuilder focusable(boolean isFocusable) {
            this.textArea.setFocusable(isFocusable);
            return this;
        }

        /**
         * Sets the foreground color of the text.
         * <br>
         * By default, it uses the default foreground color.
         *
         * @param color the color to set
         * @return the builder
         */
        public TextAreaBuilder foreground(Color color) {
            this.textArea.setForeground(color);
            return this;
        }

        /**
         * Sets whether the text area should wrap lines.
         * <br>
         * By default, this is true.
         *
         * @param shouldWrap true if lines should be wrapped, false otherwise
         * @return the builder
         */
        public TextAreaBuilder shouldWrapLines(boolean shouldWrap) {
            this.textArea.setLineWrap(shouldWrap);
            return this;
        }

        public JComponent component() {
            if (this.maxLines > 0) {
                return wrapScrollPane(this.textArea, maxLines);
            }

            return this.textArea;
        }

        public JBTextArea textArea() {
            return this.textArea;
        }

        /**
         * This is a copy of {@link Messages#wrapToScrollPaneIfNeeded(JComponent, int, int, int)}, where the border
         * is not removed from the scrollbar and the max width is not set (prevents a horizontal scrollbar from appearing).
         *
         * @param component the component to wrap
         * @param maxLines the maximum number of lines to show without scrolling
         * @return the wrapped component
         */
        private static JScrollPane wrapScrollPane(JComponent component, int maxLines) {
            int lines = 4;
            int columns = 0;

            float fontSize = component.getFont().getSize2D();
            Dimension maxDim = new Dimension((int) (fontSize * columns), (int) (fontSize * maxLines));
            Dimension prefDim = component.getPreferredSize();

            JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(component);
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            int barWidth = UIUtil.getScrollBarWidth();
            Dimension preferredSize = new Dimension(
                    Math.min(prefDim.width, maxDim.width) + barWidth,
                    Math.min(prefDim.height, maxDim.height) + barWidth);
            if (prefDim.width > maxDim.width) { // Too wide a single-line message should be wrapped
                preferredSize.height = Math.max(preferredSize.height, (int) (lines * fontSize) + barWidth);
            }
            scrollPane.setPreferredSize(preferredSize);
            return scrollPane;
        }
    }

    private static String spanText(String text, @Nullable Color color) {
        if (color != null) {
            return "<span style=\"color: %s\">%s</span>".formatted(IntellijUtil.colorToCSS(color), text);
        } else {
            return "<span style=\"\">%s</span>".formatted(text);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    public record HtmlTextBuilder(DslLabel label, Color defaultTextColor) {
        private HtmlTextBuilder(String text) {
            this(text, JBUI.CurrentTheme.Label.foreground());
        }

        private HtmlTextBuilder(String text, Color defaultTextColor) {
            this(new DslLabel(DslLabelType.LABEL), defaultTextColor);
            this.label.setFont(JBFont.regular());
            this.label.setText(text);
            // Enable clickable links
            this.label.setAction(event -> BrowserUtil.browse(event.getURL()));
        }

        public HtmlTextBuilder maxLineLength(int maxLineLength) {
            this.label.setMaxLineLength(maxLineLength);
            return this;
        }

        public HtmlTextBuilder addText(String additionalText, Color color) {
            return this.addHtml(spanText(additionalText, color));
        }

        public HtmlTextBuilder addLink(String text, String link) {
            return this.addHtml("<a href=\"%s\">%s</a>".formatted(link, text));
        }

        public HtmlTextBuilder addKeyShortcut(KeyPress... keys) {
            return this.addText(
                    Arrays.stream(keys).map(KeyPress::toString).collect(Collectors.joining(" + ")),
                    JBUI.CurrentTheme.Link.Foreground.ENABLED);
        }

        public HtmlTextBuilder addText(String additionalText) {
            return this.addHtml(spanText(additionalText, this.defaultTextColor));
        }

        private HtmlTextBuilder addHtml(String text) {
            this.label.setText(this.label.getUserText() + text);
            return this;
        }

        public HtmlTextBuilder font(JBFont font) {
            this.label.setFont(font);
            return this;
        }

        public HtmlTextBuilder focusable(boolean isFocusable) {
            this.label.setFocusable(isFocusable);
            return this;
        }

        public HtmlTextBuilder limitPreferredSize(boolean shouldLimit) {
            this.label.setLimitPreferredSize(shouldLimit);
            return this;
        }
    }
}
