/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.extensions;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import org.jetbrains.annotations.NotNull;

public class CustomCommentField extends Labelled<JScrollPane> {
    private static final String LABEL_PROGRESS_TEMPLATE = "%d/%d";
    private static final double WARNING_THRESHOLD = 0.8;
    private static final double ERROR_THRESHOLD = 1.0;
    // Artemis4J has an upper limit of 5000 characters set for the deserialized annotation,
    // this includes the comment and the associated location.
    //
    // This should be sufficient for most cases:
    private static final int MAXIMUM_TEXT_LENGTH = 3500;
    private final JBTextArea commentField;

    private CustomCommentField(JScrollPane scrollPane, JBTextArea commentField, String labelText, LabelKind kind) {
        super(scrollPane, labelText, kind);
        this.commentField = commentField;
    }

    public ValidationInfo validator() {
        LabelKind kind = this.refreshTextLength();
        String value = this.commentField().getText();
        int maximumLength = MAXIMUM_TEXT_LENGTH;

        return switch (kind) {
            case ERROR -> new ValidationInfo(
                    "Message must be %d characters shorter".formatted(value.length() - maximumLength),
                    this.commentField());
            case WARNING -> new ValidationInfo(
                            "Message length %d is close to the limit %d".formatted(value.length(), maximumLength),
                            this.commentField())
                    .asWarning();
            case HINT -> null;
        };
    }

    public static CustomCommentField with(String initialMessage) {
        var customMessage = new JBTextArea(initialMessage);
        customMessage.setFont(JBFont.regular());
        customMessage.setLineWrap(true);
        // This adds a bit of padding between the border and the text:
        customMessage.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border()), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        customMessage.setCaretPosition(initialMessage.length());

        return new CustomCommentField(
                ScrollPaneFactory.createScrollPane(customMessage), customMessage, "?/?", LabelKind.HINT);
    }

    public void registerValidator(@NotNull Disposable parentDisposable) {
        // A ComponentValidator needs to be disposed of when the parent component in which `this` is,
        // is disposed of, otherwise intellij will complain about a memory leak/invalid parent.
        new ComponentValidator(parentDisposable)
                .withValidator(this::validator)
                .andRegisterOnDocumentListener(this.commentField)
                .installOn(this.commentField);

        // trigger the initial validation of the initialMessage:
        ComponentValidator.getInstance(this.commentField).ifPresent(ComponentValidator::revalidate);
    }

    public JBTextArea commentField() {
        return commentField;
    }

    public String text() {
        return commentField.getText().trim();
    }

    private LabelKind refreshTextLength() {
        // The text() will trim trailing whitespaces. It looks buggy when the character counter
        // does not increase after typing spaces at the end of the text.
        // -> The counter uses the raw text length

        int length = this.commentField.getText().length();
        int maximumLength = MAXIMUM_TEXT_LENGTH;
        LabelKind kind = LabelKind.HINT;
        if (length > maximumLength * ERROR_THRESHOLD) {
            kind = LabelKind.ERROR;
        } else if (length > maximumLength * WARNING_THRESHOLD) {
            kind = LabelKind.WARNING;
        }

        String labelText = LABEL_PROGRESS_TEMPLATE.formatted(length, maximumLength);
        super.changeLabelText(labelText, kind);

        return kind;
    }
}
