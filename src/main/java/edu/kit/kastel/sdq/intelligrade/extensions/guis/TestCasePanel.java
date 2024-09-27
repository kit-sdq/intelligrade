/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import javax.swing.JPanel;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import net.miginfocom.swing.MigLayout;

public class TestCasePanel extends SimpleToolWindowPanel {
    private final JPanel testCasePanel;

    public TestCasePanel() {
        super(true, true);

        var content = new JBPanel<>(new MigLayout("wrap 1", "[grow]"));

        testCasePanel = new JBPanel<>(new MigLayout("wrap 3, gapx 10px, gapy 5px", "[][][]"));
        content.add(testCasePanel);

        setContent(ScrollPaneFactory.createScrollPane(content));

        PluginState.getInstance().registerAssessmentStartedListener(assessment -> {
            testCasePanel.removeAll();

            var testResults = assessment.getAssessment().getTestResults();
            for (var result : testResults) {
                String tooltip = result.getDetailText().orElse("No details available");

                var icon = result.getPoints() != 0.0
                        ? AllIcons.RunConfigurations.TestPassed
                        : AllIcons.RunConfigurations.TestFailed;
                var iconLabel = new JBLabel(icon);
                iconLabel.setToolTipText(tooltip);
                testCasePanel.add(iconLabel);

                var testName = new JBLabel(result.getTestName());
                testName.setToolTipText(tooltip);
                testCasePanel.add(testName);

                String points = result.getPoints() != 0.0 ? String.format("%.3fP", result.getPoints()) : "";
                testCasePanel.add(new JBLabel(points));
            }

            updateUI();
        });

        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            testCasePanel.removeAll();
            testCasePanel.add(new JBLabel("No active assessment"), "spanx 3, alignx center");
            updateUI();
        });
    }
}
