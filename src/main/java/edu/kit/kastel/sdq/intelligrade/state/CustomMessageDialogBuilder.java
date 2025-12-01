/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.state;

import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.BadLocationException;

import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.JBPanel;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.sdq.intelligrade.extensions.CustomCommentField;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import net.miginfocom.swing.MigLayout;

public class CustomMessageDialogBuilder {
    private final ComponentPopupBuilder builder;
    private final JBPanel<JBPanel<?>> mainPanel;
    private final CustomCommentField field;
    private JBPopup popup;
    private JSpinner customScore;

    private boolean canExit() {
        var info = this.field.validator();
        // Don't allow to close if there is a validation error
        return info == null || info.warning;
    }

    private CustomMessageDialogBuilder(String initialMessage) {
        this.mainPanel = new JBPanel<>(new MigLayout("wrap 2, fill", "[250lp] []"));
        this.field = CustomCommentField.with(initialMessage);

        // The second parameter is the text area, on which the popup will focus.
        // This allows to immediately start typing without having to use the mouse
        this.builder = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(this.mainPanel, this.field.commentField())
                .setTitle("Custom Comment")
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(true)
                .setNormalWindowLevel(true)
                .setBelongsToGlobalPopupStack(true)
                .setCancelKeyEnabled(true)
                .setMayBeParent(true)
                .setDimensionServiceKey(
                        IntellijUtil.getActiveProject(), this.getClass().getCanonicalName(), false)
                .setCancelCallback(this::canExit);

        this.field.commentField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() != KeyEvent.VK_ENTER) {
                    return;
                }

                if (event.isControlDown()) {
                    // Enter will be used as a short-cut for ok, Ctrl+Enter is used to insert a new line:
                    try {
                        field.commentField()
                                .getDocument()
                                .insertString(field.commentField().getCaretPosition(), "\n", null);
                    } catch (BadLocationException e) {
                        throw new IllegalArgumentException(e);
                    }
                } else if (popup != null && canExit()) {
                    popup.closeOk((InputEvent) EventQueue.getCurrentEvent());
                }
            }
        });

        this.mainPanel.add(this.field, "spanx 2, grow, hmin 100lp, gp 300");
    }

    public static CustomMessageDialogBuilder create(String initialMessage) {
        return new CustomMessageDialogBuilder(initialMessage);
    }

    public CustomMessageDialogBuilder onSubmit(Consumer<MessageWithPoints> onSubmit) {
        this.builder.setOkHandler(() -> onSubmit.accept(new MessageWithPoints(
                this.field.text(), this.customScore == null ? 0.0 : (Double) this.customScore.getValue())));

        return this;
    }

    public CustomMessageDialogBuilder allowCustomScore(MistakeType mistakeType, double initialPoints) {
        double maxValue = mistakeType.getRatingGroup().getMaxPenalty();
        double minValue = mistakeType.getRatingGroup().getMinPenalty();
        this.customScore = new JSpinner(new SpinnerNumberModel(initialPoints, minValue, maxValue, 0.5));

        var okButton = new JButton("Ok");
        okButton.addActionListener(a -> {
            if (this.popup != null && canExit()) {
                this.popup.closeOk((InputEvent) EventQueue.getCurrentEvent());
            }
        });

        var southPanel = new JBPanel<>(new MigLayout("fill, wrap 2", "[grow] []"));
        southPanel.add(this.customScore, "grow");
        southPanel.add(okButton, "skip 1, grow");

        this.mainPanel.add(southPanel, "dock south, span 2, growx, aligny center");

        return this;
    }

    public void showNotModal() {
        showMaybeModal(false);
    }

    public void show() {
        showMaybeModal(true);
    }

    private void showMaybeModal(boolean modal) {
        this.builder.setModalContext(modal);
        this.popup = this.builder.createPopup();
        // Attach the validator to the lifecycle of the popup:
        this.field.registerValidator(this.popup);
        this.popup.showCenteredInCurrentWindow(IntellijUtil.getActiveProject());
    }

    public record MessageWithPoints(String message, double points) {}
}
