package edu.kit.kastel.highlighter;

import com.intellij.ui.WidthBasedLayout;

import javax.swing.*;
import java.awt.*;

final class WrapperPanel extends JPanel implements WidthBasedLayout {

    WrapperPanel(JComponent content) {
        super(new BorderLayout());
        setBorder(null);
        setContent(content);
    }

    void setContent(JComponent content) {
        removeAll();
        add(content, BorderLayout.CENTER);
    }

    private JComponent getComponent() {
        return (JComponent)getComponent(0);
    }

    @Override
    public int getPreferredWidth() {
        return WidthBasedLayout.getPreferredWidth(getComponent());
    }

    @Override
    public int getPreferredHeight(int width) {
        return WidthBasedLayout.getPreferredHeight(getComponent(), width);
    }
}