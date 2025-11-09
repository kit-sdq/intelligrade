/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.widgets;

import java.awt.Color;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.JTextComponent;
import javax.swing.text.LabelView;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.ParagraphView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

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

    public static BaseTextBuilder immutable(String text) {
        return TextBuilder.text(text).editable(false).border(false).focusable(false);
    }

    public static BaseTextBuilder textBox(String text) {
        return new TextBoxBuilder(text, 0, 0);
    }

    public static BaseTextBuilder text(String initialText) {
        return new RegularTextBuilder(initialText).editable(true);
    }

    public abstract static class BaseTextBuilder {
        private final JTextComponent text;
        private int maxLines;
        protected Alignment horizontalAlignment;

        protected BaseTextBuilder(JTextComponent text, int maxLines) {
            this.text = text;
            this.maxLines = maxLines;
        }

        /**
         * Adds an empty border around the text, this is useful for text areas.
         * <br>
         * By default, a border is added.
         *
         * @param hasBorder true if it should have a border, false otherwise
         * @return the builder
         */
        public BaseTextBuilder border(boolean hasBorder) {
            if (hasBorder) {
                Border border = JBUI.Borders.empty(6, 12);
                this.text.setBorder(border);
            } else {
                this.text.setBorder(JBUI.Borders.empty());
            }

            return this;
        }

        public BaseTextBuilder installValidator(
                Disposable parentDisposable, Function<? super JTextComponent, ? extends ValidationInfo> validator) {
            // A ComponentValidator needs to be disposed of when the parent component in which `this` is,
            // is disposed of, otherwise intellij will complain about a memory leak/invalid parent.
            new ComponentValidator(parentDisposable)
                    .withValidator(() -> validator.apply(this.text))
                    .andRegisterOnDocumentListener(this.text)
                    .installOn(this.text);

            // trigger the validation of the initialMessage:
            ComponentValidator.getInstance(this.text).ifPresent(ComponentValidator::revalidate);

            return this;
        }

        /**
         * Updates the caret position based on the given function.
         *
         * @param positionFunction a function that takes the text area and returns the new caret position
         * @return the builder
         */
        public BaseTextBuilder updateCaretPosition(ToIntFunction<? super JTextComponent> positionFunction) {
            this.text.setCaretPosition(positionFunction.applyAsInt(this.text));
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
        public BaseTextBuilder editable(boolean isEditable) {
            this.text.setEditable(isEditable);
            return this;
        }

        /**
         * Sets the horizontal alignment of the text.
         * <br>
         * If the text component does not support alignment, this is ignored.
         *
         * @param alignment the alignment to set
         * @return the builder
         */
        public BaseTextBuilder horizontalAlignment(Alignment alignment) {
            this.horizontalAlignment = alignment;
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
        public BaseTextBuilder maxLines(int maxLines) {
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
        public BaseTextBuilder focusable(boolean isFocusable) {
            this.text.setFocusable(isFocusable);
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
        public BaseTextBuilder foreground(Color color) {
            this.text.setForeground(color);
            return this;
        }

        public JComponent component() {
            if (this.maxLines > 0) {
                return wrapScrollPane(this.text, maxLines);
            }

            return this.text;
        }

        public BaseTextBuilder debugBorder(Color color) {
            this.text.setBorder(BorderFactory.createLineBorder(color, 2));
            return this;
        }

        public JTextComponent text() {
            return this.text;
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

    public static class TextWidget extends JTextPane {
        private static final String VERTICAL_ALIGNMENT_ATTR = "VerticalAlignment";
        private final MutableAttributeSet attributes = new SimpleAttributeSet();

        TextWidget(String text) {
            super();

            this.setContentType(UIUtil.HTML_MIME);
            this.setEditorKit(new StyledEditorKit() {
                @Override
                public ViewFactory getViewFactory() {
                    return elem -> {
                        // Copy-paste from default StyledEditorKit.ViewFactory, which is of course not public...
                        // (Adjusted the awful code a bit for readability)

                        String kind = elem.getName();
                        if (kind == null) {
                            // default to text display
                            return new LabelView(elem);
                        }

                        return switch (kind) {
                            case AbstractDocument.ParagraphElementName -> new ParagraphView(elem);
                            case AbstractDocument.SectionElementName -> new AlignedBoxView(elem, View.Y_AXIS);
                            case StyleConstants.ComponentElementName -> new ComponentView(elem);
                            case StyleConstants.IconElementName -> new IconView(elem);
                            default -> new LabelView(elem);
                        };
                    };
                }
            });
            this.setText(text);
        }

        void setAlignment(Alignment horizontalAlignment, Alignment verticalAlignment) {
            this.attributes.addAttribute(VERTICAL_ALIGNMENT_ATTR, verticalAlignment.getStyleAlignment());

            StyleConstants.setAlignment(this.attributes, horizontalAlignment.getStyleAlignment());
            this.updateAlignmentAttrs();
        }

        @Override
        public void setText(String text) {
            super.setText(text);

            this.updateAlignmentAttrs();
        }

        private void updateAlignmentAttrs() {
            StyledDocument doc = (StyledDocument) this.getDocument();
            doc.setParagraphAttributes(0, doc.getLength() - 1, this.attributes, false);
        }
    }

    private static class AlignedBoxView extends BoxView {
        public AlignedBoxView(Element elem, int axis) {
            super(elem, axis);
        }

        private int getVerticalAlignment() {
            AttributeSet attr = getAttributes();
            if (attr != null) {
                var res = attr.getAttribute(TextWidget.VERTICAL_ALIGNMENT_ATTR);
                if (res != null) {
                    return (int) res;
                }
            }

            return StyleConstants.ALIGN_CENTER;
        }

        @Override
        protected void layoutMajorAxis(int targetSpan, int axis, int[] offsets, int[] spans) {
            super.layoutMajorAxis(targetSpan, axis, offsets, spans);
            int textBlockHeight = Arrays.stream(spans).sum();

            int offset =
                    switch (getVerticalAlignment()) {
                        case StyleConstants.ALIGN_CENTER -> (targetSpan - textBlockHeight) / 2;
                        case StyleConstants.ALIGN_LEFT -> 0;
                        case StyleConstants.ALIGN_RIGHT -> targetSpan - textBlockHeight;
                        default -> throw new IllegalStateException("Unknown alignment: " + getVerticalAlignment());
                    };

            for (int i = 0; i < offsets.length; i++) {
                offsets[i] += offset;
            }
        }
    }

    public static class RegularTextBuilder extends BaseTextBuilder {
        private RegularTextBuilder(String text) {
            super(new TextWidget(text), 0);

            this.text().setFont(JBFont.regular());
            this.text().setEditable(true);
            this.border(true);
        }

        @Override
        public RegularTextBuilder horizontalAlignment(Alignment alignment) {
            super.horizontalAlignment(alignment);

            this.text().setAlignment(this.horizontalAlignment, Alignment.CENTER);
            return this;
        }

        @Override
        public TextWidget text() {
            return (TextWidget) super.text();
        }
    }

    private static final class TextBoxBuilder extends BaseTextBuilder {
        private TextBoxBuilder(String text, int rows, int columns) {
            super(createText(text, rows, columns), 0);

            this.border(true);
        }

        private static JTextComponent createText(String text, int rows, int columns) {
            var result = new JBTextArea(text, rows, columns) {
                @Override
                public Dimension getMinimumSize() {
                    // For some reason the minimum size is not computed correctly by the parent.
                    //
                    // For example the preferred size is (343, 17) and the minimum size is
                    // reported as (565, 17)...
                    //
                    // I think this is, because JBTextArea (which extends JTextArea) overrides
                    // getPreferredSize but not getMinimumSize.
                    //
                    // -> we override it here to set it to a sensible value.
                    //
                    // While trying to set something other than (0, 0) here, I noticed that the minimum size
                    // will influence the layout and preferred size calculations in weird ways.
                    //
                    // For example, when you set the minimum size to the height of one line and the width to
                    // a single character, the preferred height becomes the height of text.length() * lineHeight
                    // (as if every character is on its own line), but it will use the full width anyway, resulting
                    // in a very tall window, with a lot of empty space.
                    if (isMinimumSizeSet()) {
                        return super.getMinimumSize();
                    }

                    return new Dimension(0, 0);
                }
            };

            result.setText(text);

            result.setFont(JBFont.regular());
            // Wrap text to the next line:
            result.setLineWrap(true);
            // Wrap full words and not just letters:
            result.setWrapStyleWord(true);

            result.setEditable(true);

            return result;
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

    public enum Alignment {
        LEFT(StyleConstants.ALIGN_LEFT),
        CENTER(StyleConstants.ALIGN_CENTER),
        RIGHT(StyleConstants.ALIGN_RIGHT);

        private final int value;

        Alignment(int value) {
            this.value = value;
        }

        private int getStyleAlignment() {
            return this.value;
        }
    }
}
