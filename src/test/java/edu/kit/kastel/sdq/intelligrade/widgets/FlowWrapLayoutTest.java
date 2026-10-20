/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.LayerUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;

import net.miginfocom.swing.MigLayout;
import org.junit.jupiter.api.Test;

class FlowWrapLayoutTest {
    private static final int MINIMUM_WRAPPED_ASSESSMENT_BUTTON_WIDTH = 190;

    @Test
    void usesMaxColumnsWhenFullRowFitsAtPreferredWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new JPanel(new FlowWrapLayout(3, "gap 0"));

            for (int i = 0; i < 3; i++) {
                panel.add(new ResizableComponent(), "grow");
            }

            panel.setSize(400, 100);
            panel.doLayout();

            assertEquals(panel.getComponent(0).getY(), panel.getComponent(1).getY());
            assertEquals(panel.getComponent(1).getY(), panel.getComponent(2).getY());
        });
    }

    @Test
    void singleComponentUsesFullWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new JPanel(new FlowWrapLayout(5, "fill, gap 0, insets 0"));
            panel.add(new ResizableComponent(), "grow");

            panel.setSize(220, 100);
            panel.doLayout();

            assertTrue(panel.getComponent(0).getWidth() > 200);
        });
    }

    @Test
    void singleColumnLayoutShrinksToAvailableWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new JPanel(new FlowWrapLayout(3, "fill, gap 0"));

            for (int i = 0; i < 3; i++) {
                panel.add(new ResizableComponent(), "grow");
            }

            panel.setSize(40, 100);
            panel.doLayout();

            assertEquals(panel.getComponent(0).getX(), panel.getComponent(1).getX());
            assertEquals(panel.getComponent(1).getX(), panel.getComponent(2).getX());
            assertTrue(panel.getComponent(0).getY() < panel.getComponent(1).getY());
            assertTrue(panel.getComponent(1).getY() < panel.getComponent(2).getY());
            assertFitsWithinParent(panel, 0);
            assertFitsWithinParent(panel, 1);
            assertFitsWithinParent(panel, 2);
        });
    }

    @Test
    void assessmentButtonsResizeDownToOneColumn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = createAssessmentButtonPanel(
                    "Missing or wrong method call",
                    "Incorrect exception handling",
                    "Wrong visibility modifier",
                    "Wrong return value",
                    "Missing null check");

            panel.setSize(1600, 200);
            panel.doLayout();
            assertEquals(1, rowCount(panel));

            panel.setSize(140, 600);
            panel.doLayout();
            assertEquals(panel.getComponentCount(), rowCount(panel));

            for (int i = 0; i < panel.getComponentCount(); i++) {
                assertFitsWithinParent(panel, i);
            }
        });
    }

    @Test
    void assessmentButtonsKeepColumnsByWrappingTextBeforeReducingColumns() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = createAssessmentButtonPanel(
                    "Missing or wrong method call",
                    "Incorrect exception handling",
                    "Wrong visibility modifier",
                    "Wrong return value",
                    "Missing null check");

            layoutAtWidth(panel, 1600);
            assertEquals(1, rowCount(panel));
            assertAllComponentsUseColumnWidth(panel, 5);

            layoutAtWidth(panel, 1000);
            assertEquals(1, rowCount(panel));
            assertAllComponentsUseColumnWidth(panel, 5);

            for (var component : panel.getComponents()) {
                assertTrue(component.getWidth() < component.getPreferredSize().width);
            }
        });
    }

    @Test
    void assessmentButtonsRecomputeColumnsWhenAvailableSpaceShrinksAndGrows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = createAssessmentButtonPanel(
                    "Missing or wrong method call",
                    "Incorrect exception handling",
                    "Wrong visibility modifier",
                    "Wrong return value",
                    "Missing null check");

            layoutAtWidth(panel, 1600);
            assertEquals(1, rowCount(panel));
            assertAllComponentsUseColumnWidth(panel, 5);

            layoutAtWidth(panel, 1000);
            assertEquals(1, rowCount(panel));
            assertAllComponentsUseColumnWidth(panel, 5);

            layoutAtWidth(panel, 140);
            assertEquals(panel.getComponentCount(), rowCount(panel));
            assertAllComponentsUseColumnWidth(panel, 1);
            assertAllButtonsShowWrappedHtmlText(panel);

            layoutAtWidth(panel, 1000);
            assertEquals(1, rowCount(panel));
            assertAllComponentsUseColumnWidth(panel, 5);
        });
    }

    @Test
    void assessmentButtonsReduceColumnsWhenWrappedTextWouldBeClipped() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = createAssessmentButtonPanel(
                    "Difficult Code",
                    "Uniform language",
                    "JavaDoc trivial",
                    "Magic Literal",
                    "Bad identifiers",
                    "Comments",
                    "Visibility",
                    "Information hiding",
                    "Code Copies Auxiliary method",
                    "Enum/inheritance",
                    "IO/UI",
                    "Object",
                    "Operations instead of Domain",
                    "Final",
                    "String references",
                    "Feature Reimplement (JavaAPI)",
                    "Incorrect loop type",
                    "Enum/Sets",
                    "unnecessary complexity",
                    "Error message",
                    "Packages");

            layoutAtWidth(panel, 540);

            assertTrue(rowCount(panel) > panel.getComponentCount() / 3);
            assertAllComponentsUseColumnWidth(panel, 2);
            assertAllButtonsShowWrappedHtmlText(panel);
        });
    }

    private static JPanel createAssessmentButtonPanel(String... buttonTexts) {
        var sizeGroups = new FlowWrapLayout.SharedSizeGroups();
        var panel =
                new JPanel(new FlowWrapLayout(5, "fill, gap 0", sizeGroups, MINIMUM_WRAPPED_ASSESSMENT_BUTTON_WIDTH));

        for (String buttonText : buttonTexts) {
            var button = new JButton("<html><body style='text-align: center;'>" + buttonText + "</body></html>");
            var buttonPanel = new JPanel(new MigLayout("fill, insets 0"));
            buttonPanel.add(button, "grow");
            panel.add(new JLayer<>(buttonPanel, new LayerUI<>()), "grow, sizegroup mistakeButtons");
        }

        return panel;
    }

    private static void layoutAtWidth(JPanel panel, int width) {
        panel.setSize(width, 600);
        layoutTree(panel);
    }

    private static void layoutTree(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (var child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static int rowCount(JPanel panel) {
        int rows = 0;
        Integer previousY = null;
        for (var component : panel.getComponents()) {
            if (previousY == null || component.getY() != previousY) {
                rows++;
                previousY = component.getY();
            }
        }

        return rows;
    }

    private static void assertFitsWithinParent(JPanel panel, int componentIndex) {
        var component = panel.getComponent(componentIndex);
        assertTrue(component.getX() >= 0);
        assertTrue(component.getX() + component.getWidth() <= panel.getWidth());
    }

    private static void assertAllComponentsUseColumnWidth(JPanel panel, int columns) {
        int leftEdge = panel.getWidth();
        int rightEdge = 0;
        for (var component : panel.getComponents()) {
            leftEdge = Math.min(leftEdge, component.getX());
            rightEdge = Math.max(rightEdge, component.getX() + component.getWidth());
        }

        int expectedWidth = (rightEdge - leftEdge) / columns;
        for (int i = 0; i < panel.getComponentCount(); i++) {
            int width = panel.getComponent(i).getWidth();
            assertTrue(width == expectedWidth || width == expectedWidth + 1);
            assertFitsWithinParent(panel, i);
        }
    }

    private static void assertAllButtonsShowWrappedHtmlText(Container container) {
        for (var component : container.getComponents()) {
            if (component instanceof JButton button) {
                assertHtmlTextFits(button);
            }

            if (component instanceof Container childContainer) {
                assertAllButtonsShowWrappedHtmlText(childContainer);
            }
        }
    }

    private static void assertHtmlTextFits(JButton button) {
        var htmlRenderer = (View) button.getClientProperty(BasicHTML.propertyKey);
        if (htmlRenderer == null) {
            return;
        }

        var insets = button.getInsets();
        htmlRenderer.setSize((float) (button.getWidth() - insets.left - insets.right), 0);
        int requiredHeight = (int) Math.ceil(htmlRenderer.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
        assertTrue(requiredHeight <= button.getHeight());
    }

    private static class ResizableComponent extends JComponent {
        @Override
        public Dimension getMinimumSize() {
            return new Dimension(50, 20);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, 20);
        }
    }
}
