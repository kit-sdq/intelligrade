/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.guis;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.LayerUI;

import com.intellij.DynamicBundle;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBFont;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.*;
import edu.kit.kastel.state.ActiveAssessment;
import edu.kit.kastel.state.PluginState;
import net.miginfocom.swing.MigLayout;

public class AssessmentPanel extends SimpleToolWindowPanel {
    private static final Locale LOCALE = DynamicBundle.getLocale();

    private final JPanel content;
    private final JBLabel pointsLabel;
    private final Map<RatingGroup, TitledBorder> ratingGroupBorders = new IdentityHashMap<>();
    private final List<AssessmentButton> assessmentButtons = new ArrayList<>();

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

            var panel = new JBPanel<>(new MigLayout("fill, gap 0, wrap " + buttonsPerRow));

            var border = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(JBColor.border()),
                    String.format(getRatingGroupTitle(assessment.getAssessment(), ratingGroup)));
            border.setTitleFont(JBFont.h3().asBold());
            panel.setBorder(border);
            this.ratingGroupBorders.put(ratingGroup, border);

            for (var mistakeType : ratingGroup.getMistakeTypes()) {
                var button = new JButton(mistakeType.getButtonText().translateTo(LOCALE));
                button.setToolTipText(mistakeType.getMessage().translateTo(LOCALE));

                var iconRenderer = new MistakeTypeIconRenderer();
                var layer = new LayerUI<>() {
                    @Override
                    public void paint(Graphics g, JComponent c) {
                        super.paint(g, c);
                        iconRenderer.paint((Graphics2D) g, c);
                    }
                };
                JPanel buttonPanel = new JPanel(new MigLayout("fill"));
                buttonPanel.add(button, "growx");
                panel.add(new JLayer<>(buttonPanel, layer), "growx, sizegroup main");

                button.addActionListener(a -> assessment.addAnnotationAtCaret(
                        mistakeType, (a.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK));
                this.assessmentButtons.add(new AssessmentButton(mistakeType, button, iconRenderer));
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
            for (AssessmentButton assessmentButton : this.assessmentButtons) {
                var settings = ArtemisSettingsState.getInstance();
                var mistakeType = assessmentButton.mistakeType();

                String iconText;
                Color color;
                Font font = JBFont.regular();

                if (mistakeType.getReporting().shouldScore()) {
                    int count = a.getAnnotations(mistakeType).size();
                    var rule = mistakeType.getRule();

                    switch (rule) {
                        case ThresholdPenaltyRule thresholdRule -> {
                            iconText = count + "/" + thresholdRule.getThreshold();
                            if (count >= thresholdRule.getThreshold()) {
                                color = settings.getFinishedAssessmentButtonColor();
                                font = font.deriveFont(
                                        Map.of(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON));
                            } else {
                                color = settings.getActiveAssessmentButtonColor();
                            }
                        }
                        case CustomPenaltyRule customPenaltyRule -> {
                            iconText = "C";
                            color = settings.getActiveAssessmentButtonColor();
                        }
                        case StackingPenaltyRule stackingPenaltyRule -> {
                            iconText = String.valueOf(count);
                            color = settings.getActiveAssessmentButtonColor();
                        }
                    }
                } else {
                    iconText = "R";
                    color = settings.getReportingAssessmentButtonColor();
                }

                assessmentButton.iconRenderer().update(iconText, color);
                assessmentButton.button().setForeground(color);
                assessmentButton.button().setFont(font);
            }
        });

        this.updateUI();
    }

    private void showNoActiveAssessment() {
        this.ratingGroupBorders.clear();
        this.assessmentButtons.clear();

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

    private static String getAssessmentPointsTitle(
            double testPoints, double annotationPoints, double totalPoints, double maxPoints) {
        if (annotationPoints > 0.0) {
            return """
                    <html> <h2><span style="color: %s">%.1f</span> <span style="color: %s">%.1f</span> = %.1f of %.1f</h2></html>"""
                    .formatted(
                            colorToCSS(JBColor.GREEN),
                            testPoints,
                            colorToCSS(JBColor.GREEN),
                            Math.abs(annotationPoints),
                            totalPoints,
                            maxPoints);
        } else {
            return """
                    <html> <h2><span style="color: %s">%.1f</span> <span style="color: %s">- %.1f</span> = %.1f of %.1f</h2></html>"""
                    .formatted(
                            colorToCSS(JBColor.GREEN),
                            testPoints,
                            colorToCSS(JBColor.RED),
                            Math.abs(annotationPoints),
                            totalPoints,
                            maxPoints);
        }
    }

    private static String colorToCSS(JBColor color) {
        return "rgb(%d, %d, %d)".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static class MistakeTypeIconRenderer {
        private String text;
        private Color bgColor;

        private int textWidth;
        private int baselineHeight;
        private int textHeight;

        public MistakeTypeIconRenderer() {
            this.update("", JBColor.foreground());
        }

        public void paint(Graphics2D g, JComponent component) {
            g.setFont(JBFont.regular());
            if (textWidth < 0) {
                textWidth = g.getFontMetrics().stringWidth(text);
                baselineHeight = g.getFontMetrics().getMaxAscent();
                textHeight =
                        g.getFontMetrics().getMaxAscent() + g.getFontMetrics().getMaxDescent();
            }

            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(bgColor);
            g.fillRoundRect(component.getWidth() - textWidth - 5, 0, textWidth + 2, textHeight, 2, 2);

            g.setColor(JBColor.background());
            // AllIcons.Actions.Back.paintIcon(c, g, c.getWidth() - textWidth, baselineHeight);
            g.drawString(text, component.getWidth() - textWidth - 4, baselineHeight);
        }

        private void update(String text, Color bgColor) {
            this.text = text;
            this.bgColor = bgColor;
            this.textWidth = -1;
        }
    }

    private record AssessmentButton(MistakeType mistakeType, JButton button, MistakeTypeIconRenderer iconRenderer) {}
}
