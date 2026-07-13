/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import javax.swing.JPanel;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltipKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import edu.kit.kastel.sdq.intelligrade.AssessmentTracker;
import edu.kit.kastel.sdq.intelligrade.listeners.AssessmentStateListener;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.Nullable;

public class TestCasePanel extends SimpleToolWindowPanel {
    private final JPanel content;

    public TestCasePanel(Disposable parentDisposable, Project project) {
        super(true, true);

        this.content = new JBPanel<>(new MigLayout("wrap 3, gapx 10px, gapy 5px", "[][][]"));

        setContent(ScrollPaneFactory.createScrollPane(content));

        AssessmentTracker.getInstance(project).subscribe(parentDisposable, new AssessmentStateListener() {
            @Override
            public void activeAssessmentChanged(@Nullable ActiveAssessment assessment) {
                content.removeAll();

                if (assessment == null) {
                    content.add(new JBLabel("No active assessment"), "spanx 3, alignx center");
                    updateUI();
                    return;
                }

                var testResults = assessment.getAssessment().getTestResults();
                for (var result : testResults) {
                    String tooltip = result.getDetailText().orElse("No details available");

                    // isPositive() is true if the test passed, regardless of its points
                    // (which may be zero for mandatory tests)
                    var icon = result.getPositive()
                            .map(p -> p ? AllIcons.RunConfigurations.TestPassed : AllIcons.RunConfigurations.TestFailed)
                            .orElse(AllIcons.RunConfigurations.TestUnknown);
                    var iconLabel = new JBLabel(icon);
                    HelpTooltipKt.setToolTipText(iconLabel, HtmlChunk.text(tooltip));
                    content.add(iconLabel);

                    var testName = new JBLabel(result.getTestName());
                    HelpTooltipKt.setToolTipText(testName, HtmlChunk.text(tooltip));
                    content.add(testName);

                    String points = result.getPoints() == 0.0 ? "" : String.format("%.3fP", result.getPoints());
                    content.add(new JBLabel(points));
                }
                updateUI();
            }
        });
    }
}
