/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.widgets;

import java.awt.BorderLayout;

import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.messages.MessageDialog;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;

public final class MessageUtils {
    private MessageUtils() {}

    /**
     * Shows a warning dialog with the given title and content.
     *
     * @param title the title of the popup window
     * @param content the content to show inside the warning dialog
     */
    public static void showWarning(String title, JComponent content) {
        // FIXME: IntelliJ is not happy when a Project is used as parent for a window
        var dialog = new WarningDialog(IntellijUtil.getActiveProject(), title, content);
        dialog.setModal(false);

        dialog.show();
    }

    private static final class WarningDialog extends MessageDialog {
        private final JComponent content;

        private WarningDialog(Project project, String title, JComponent content) {
            super(
                    project,
                    null,
                    "",
                    title,
                    new String[] {CommonBundle.getOkButtonText()},
                    0,
                    0,
                    AllIcons.General.WarningDialog,
                    null,
                    true);

            this.content = content;
            this.init();
        }

        @Override
        protected JComponent doCreateCenterPanel() {
            JPanel panel = createIconPanel();

            // This might be called while this.content is still null
            if (this.content != null) {
                panel.add(this.content, BorderLayout.CENTER);
            }

            return panel;
        }
    }
}
