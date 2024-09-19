/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.guis;

import com.intellij.DynamicBundle;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBFont;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.CustomPenaltyRule;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.RatingGroup;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.ThresholdPenaltyRule;
import edu.kit.kastel.state.ActiveAssessment;
import edu.kit.kastel.state.PluginState;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.LayerUI;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

public class AssessmentPanel extends SimpleToolWindowPanel {
    private static final Locale LOCALE = DynamicBundle.getLocale();

    private final JPanel content;
    private final JBLabel pointsLabel;
    private final Map<RatingGroup, TitledBorder> ratingGroupBorders = new IdentityHashMap<>();
    private final Map<MistakeType, MistakeTypeIconRenderer> mistakeTypeIcons = new IdentityHashMap<>();

    public AssessmentPanel() {
        super(true, true);

        content = new JBPanel<>(new MigLayout("wrap 1", "[grow]"));
        setContent(ScrollPaneFactory.createScrollPane(content));

        pointsLabel = new JBLabel();

        this.showNoActiveAssessment();
        PluginState.getInstance().registerAssessmentStartedListener(assessment -> {
            content.removeAll();

            content.add(pointsLabel, "alignx center");
            assessment.registerAnnotationsUpdatedListener(annotations -> {
                var a = assessment.getAssessment();
                var testPoints = a.calculateTotalPointsOfTests();
                var annotationPoints = a.calculateTotalPointsOfAnnotations();
                var totalPoints = a.calculateTotalPoints();
                var maxPoints = a.getMaxPoints();
                pointsLabel.setText(getAssessmentPointsTitle(testPoints, annotationPoints, totalPoints, maxPoints));
            });

            this.createMistakeButtons(assessment);
        });
        PluginState.getInstance().registerAssessmentClosedListener(this::showNoActiveAssessment);
    }

    private void createMistakeButtons(ActiveAssessment assessment) {
        int buttonsPerRow = ArtemisSettingsState.getInstance().getColumnsPerRatingGroup();
        for (var ratingGroup : assessment.getGradingConfig().getRatingGroups()) {
            if (ratingGroup.getMistakeTypes().isEmpty()) {
                continue;
            }

            var panel = new JBPanel<>(new MigLayout("wrap " + buttonsPerRow));

            var border = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(JBColor.border()),
                    String.format(getRatingGroupTitle(assessment.getAssessment(), ratingGroup)));
            border.setTitleFont(JBFont.h3().asBold());
            panel.setBorder(border);
            this.ratingGroupBorders.put(ratingGroup, border);

            for (var mistakeType : ratingGroup.getMistakeTypes()) {
                var button = new JButton(mistakeType.getButtonText().translateTo(LOCALE));
                var iconRenderer = new MistakeTypeIconRenderer();

                var layer = new LayerUI<>() {
                    @Override
                    public void paint(Graphics g, JComponent c) {
                        super.paint(g, c);
                        iconRenderer.paint((Graphics2D) g, c);
                    }
                };
                panel.add(new JLayer<>(button, layer));

                button.addActionListener(a -> assessment.addAnnotationAtCaret(
                        mistakeType, (a.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK));
                this.mistakeTypeIcons.put(mistakeType, iconRenderer);
            }

            this.content.add(panel, "growx");
        }

        assessment.registerAnnotationsUpdatedListener(annotations -> {
            var a = assessment.getAssessment();

            // Update rating group titles
            for (var entry : this.ratingGroupBorders.entrySet()) {
                var ratingGroup = entry.getKey();
                var border = entry.getValue();
                border.setTitle(getRatingGroupTitle(a, ratingGroup));
            }

            // Update button icons
            this.mistakeTypeIcons.forEach((mistakeType, renderer) -> {
                String iconText;
                if (mistakeType.getReporting().shouldScore()) {
                    int count = a.getAnnotations(mistakeType).size();
                    var rule = mistakeType.getRule();
                    if (rule instanceof ThresholdPenaltyRule thresholdRule) {
                        iconText = count + "/" + thresholdRule.getThreshold();
                    } else if (rule instanceof CustomPenaltyRule) {
                        iconText = "C";
                    } else {
                        iconText = String.valueOf(count);
                    }
                } else {
                    iconText = "R";
                }

                renderer.setText(iconText);
            });
        });

        this.updateUI();
    }

    private void showNoActiveAssessment() {
        this.ratingGroupBorders.clear();
        this.mistakeTypeIcons.clear();

        content.removeAll();
        content.add(new JBLabel("No active assessment"), "growx");
        this.updateUI();
    }

    private String getRatingGroupTitle(Assessment assessment, RatingGroup ratingGroup) {
        var points = assessment.calculatePointsForRatingGroup(ratingGroup);
        return "%s (%.1f of [%.1f, %.1f])"
                .formatted(
                        ratingGroup.getDisplayName().translateTo(LOCALE),
                        points.score(),
                        ratingGroup.getMinPenalty(),
                        ratingGroup.getMaxPenalty());
    }
    private static String getAssessmentPointsTitle(double testPoints, double annotationPoints, double totalPoints, double maxPoints) {
        if (annotationPoints > 0.0) {
            return """
                    <html> <h2><span style="color: %s">%.1f</span> <span style="color: %s">%.1f</span> = %.1f of %.1f</h2></html>""".formatted(
                    colorToCSS(JBColor.GREEN),
                    testPoints,
                    colorToCSS(JBColor.GREEN),
                    Math.abs(annotationPoints),
                    totalPoints, maxPoints);
        } else {
            return """ 
                    <html> <h2><span style="color: %s">%.1f</span> <span style="color: %s">- %.1f</span> = %.1f of %.1f</h2></html>""".formatted(
                    colorToCSS(JBColor.GREEN),
                    testPoints,
                    colorToCSS(JBColor.RED),
                    Math.abs(annotationPoints),
                    totalPoints, maxPoints);
        }
    }

    private static String colorToCSS(JBColor color) {
        return "rgb(%d, %d, %d)".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static class MistakeTypeIconRenderer {
        private String text;

        private int textWidth;
        private int baselineHeight;
        private int textHeight;

        public MistakeTypeIconRenderer() {
            this.setText("");
        }

        public void paint(Graphics2D g, JComponent component) {
            g.setFont(JBFont.small());
            if (textWidth < 0) {
                textWidth = g.getFontMetrics().stringWidth(text);
                baselineHeight = g.getFontMetrics().getMaxAscent();
                textHeight = g.getFontMetrics().getMaxAscent() + g.getFontMetrics().getMaxDescent();
            }

            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(JBColor.foreground());
            g.fillRoundRect(component.getWidth() - textWidth - 5, 0, textWidth + 2, textHeight, 2, 2);

            g.setColor(JBColor.background());
            // AllIcons.Actions.Back.paintIcon(c, g, c.getWidth() - textWidth, baselineHeight);
            g.drawString(text, component.getWidth() - textWidth - 4, baselineHeight);
        }

        private void setText(String text) {
            this.text = text;
            this.textWidth = -1;
        }
    }
}
