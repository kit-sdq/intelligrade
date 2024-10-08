/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.login;

import java.awt.GridLayout;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.jcef.JBCefBrowser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CefDialog extends DialogWrapper {
    private final JBCefBrowser browser;

    public CefDialog(JBCefBrowser browser) {
        super((Project) null);
        this.browser = browser;

        this.setTitle("Artemis Login");
        this.setModal(false);
        this.init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel browserContainer = new JPanel(new GridLayout(1, 1));
        browserContainer.add(this.browser.getComponent());
        return browserContainer;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[0];
    }
}
