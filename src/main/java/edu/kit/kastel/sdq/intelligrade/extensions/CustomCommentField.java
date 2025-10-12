package edu.kit.kastel.sdq.intelligrade.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;

public class CustomCommentField extends Labelled<JScrollPane> {
    private static final String LABEL_PROGRESS_TEMPLATE = "%d/%d";
    private static final double WARNING_THRESHOLD = 0.8;
    private static final double ERROR_THRESHOLD = 1.0;
    private final JBTextArea commentField;

    private CustomCommentField(JScrollPane scrollPane, JBTextArea commentField, String labelText, LabelKind kind) {
        super(scrollPane, labelText, kind);
        this.commentField = commentField;
    }

    public ValidationInfo validator() {
        LabelKind kind = this.refreshTextLength();
        String value = this.commentField().getText();
        int maximumLength = maximumTextLength();

        return switch (kind) {
            case ERROR ->
                    new ValidationInfo("Message must be %d characters shorter".formatted(value.length() - maximumLength), this.commentField());
            case WARNING ->
                    new ValidationInfo("Message length %d is close to the limit %d".formatted(value.length(), maximumLength), this.commentField()).asWarning();
            case HINT -> null;
        };
    }

    public static CustomCommentField with(String initialMessage, @Nullable Disposable parentDisposable) {
        var customMessage = new JBTextArea(initialMessage);
        customMessage.setFont(JBFont.regular());
        customMessage.setLineWrap(true);
        // This adds a bit of padding between the border and the text:
        customMessage.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        customMessage.setCaretPosition(initialMessage.length());

        var scrollPane = ScrollPaneFactory.createScrollPane(customMessage);
        var result = new CustomCommentField(scrollPane, customMessage, "?/?", LabelKind.HINT);

        new ComponentValidator(parentDisposable == null ? Disposer.newDisposable() : parentDisposable)
                .withValidator(result::validator)
                //.andStartOnFocusLost()
                .andRegisterOnDocumentListener(customMessage)
                .installOn(customMessage);

        // trigger the initial validation of the initialMessage:
        ComponentValidator.getInstance(customMessage).ifPresent(ComponentValidator::revalidate);

        return result;
    }

    public JBTextArea commentField() {
        return commentField;
    }

    private static int maximumTextLength() {
        // Artemis4J has an upper limit of 5000 characters set for the deserialized annotation,
        // this includes the comment and the associated location.
        //
        // This should be sufficient for most cases:
        return 3500;
    }

    public String text() {
        return commentField.getText().trim();
    }

    private LabelKind refreshTextLength() {
        // The text() will trim trailing whitespaces. It looks buggy when the character counter
        // does not increase after typing spaces at the end of the text.
        // -> The counter uses the raw text length

        int length = this.commentField.getText().length();
        int maximumLength = maximumTextLength();
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
