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
    private final JPanel content;

    public TestCasePanel() {
        super(true, true);

        this.content = new JBPanel<>(new MigLayout("wrap 3, gapx 10px, gapy 5px", "[][][]"));

        setContent(ScrollPaneFactory.createScrollPane(content));

        PluginState.getInstance().registerAssessmentStartedListener(assessment -> {
            this.content.removeAll();

            var testResults = assessment.getAssessment().getTestResults();
            for (var result : testResults) {
                String tooltip = result.getDetailText().orElse("No details available");

                // isPositive() is true if the test passed, regardless of its points
                // (which may be zero for mandatory tests)
                var icon = result.getPositive()
                        .map(p -> p ? AllIcons.RunConfigurations.TestPassed : AllIcons.RunConfigurations.TestFailed)
                        .orElse(AllIcons.RunConfigurations.TestUnknown);
                var iconLabel = new JBLabel(icon);
                iconLabel.setToolTipText(tooltip);
                this.content.add(iconLabel);

                var testName = new JBLabel(result.getTestName());
                testName.setToolTipText(tooltip);
                this.content.add(testName);

                String points = result.getPoints() != 0.0 ? String.format("%.3fP", result.getPoints()) : "";
                this.content.add(new JBLabel(points));
            }

            updateUI();
        });

        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            this.content.removeAll();
            this.content.add(new JBLabel("No active assessment"), "spanx 3, alignx center");
            updateUI();
        });
    }
}
