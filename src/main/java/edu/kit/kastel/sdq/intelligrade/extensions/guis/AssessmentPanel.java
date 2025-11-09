/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.plaf.LayerUI;

import com.intellij.DynamicBundle;
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import edu.kit.kastel.sdq.artemis4j.grading.Assessment;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.CustomPenaltyRule;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.Points;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.RatingGroup;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.StackingPenaltyRule;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.ThresholdPenaltyRule;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ThemeColor;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import edu.kit.kastel.sdq.intelligrade.utils.KeyPress;
import edu.kit.kastel.sdq.intelligrade.widgets.FlowWrapLayout;
import edu.kit.kastel.sdq.intelligrade.widgets.TextBuilder;
import net.miginfocom.swing.MigLayout;

public class AssessmentPanel extends SimpleToolWindowPanel {
    private static final Locale LOCALE = DynamicBundle.getLocale();

    private final JPanel content;
    private final JBLabel pointsLabel;
    private final Map<RatingGroup, TitledSeparator> ratingGroupBorders = new IdentityHashMap<>();
    private final List<AssessmentButton> assessmentButtons = new ArrayList<>();

    public AssessmentPanel() {
        super(true, true);

        content = new ScrollablePanel(new MigLayout("wrap 1", "[grow]"));
        setContent(ScrollPaneFactory.createScrollPane(
                content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));

        pointsLabel = new JBLabel();

        this.showNoActiveAssessment();
        PluginState.getInstance().registerAssessmentStartedListener(assessment -> {
            content.removeAll();

            content.add(pointsLabel, "alignx center");

            var infoLabel = TextBuilder.immutable("Hold ")
                    .foreground(JBColor.GRAY)
                    .addColoredText(
                            KeyPress.of(KeyEvent.VK_CONTROL).toString(), JBUI.CurrentTheme.Link.Foreground.ENABLED)
                    .addText(", while pressing a button, to add a custom message")
                    .horizontalAlignment(TextBuilder.Alignment.CENTER)
                    .text();
            content.add(infoLabel, "alignx center");

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

    private Component addGroupPanel(
            ActiveAssessment assessment, RatingGroup ratingGroup, List<MistakeType> mistakeTypes) {
        var separator = new TitledSeparator(getRatingGroupTitle(assessment.getAssessment(), ratingGroup));
        separator.setTitleFont(JBFont.h3().asBold());
        this.ratingGroupBorders.put(ratingGroup, separator);
        this.content.add(separator, "growx");

        var panel = new JBPanel<>(new FlowWrapLayout(5, "fill, gap 0"));
        for (var mistakeType : mistakeTypes) {
            var button = ExercisePanel.createWrappingButton(
                    mistakeType.getButtonText().translateTo(LOCALE));

            // no tooltip for custom comment
            if (!mistakeType.isCustomAnnotation()) {
                button.setToolTipText(mistakeType.getMessage().translateTo(LOCALE));
            }
            button.setMargin(JBUI.emptyInsets());

            var iconRenderer = new MistakeTypeIconRenderer();
            var layer = new LayerUI<>() {
                @Override
                public void paint(Graphics g, JComponent c) {
                    super.paint(g, c);
                    iconRenderer.paint((Graphics2D) g, c);
                }
            };
            JPanel buttonPanel = new JPanel(new MigLayout("fill, insets 0"));
            buttonPanel.add(button, "grow");
            panel.add(new JLayer<>(buttonPanel, layer), "grow, sizegroup main");

            button.addActionListener(a -> assessment.addAnnotationAtCaret(
                    mistakeType, (a.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK));

            // All buttons are disabled in review mode
            button.setEnabled(!assessment.isReview());

            this.assessmentButtons.add(new AssessmentButton(mistakeType, button, iconRenderer));
        }

        return panel;
    }

    private static List<MistakeType> getDirectMistakeTypes(RatingGroup ratingGroup) {
        // Mistake types that are not in a subgroup
        List<MistakeType> directMistakeTypes = new ArrayList<>();
        for (var mistakeType : ratingGroup.getAllMistakeTypes()) {
            if (mistakeType.getRatingGroup().equals(ratingGroup)) {
                directMistakeTypes.add(mistakeType);
            }
        }

        return directMistakeTypes;
    }

    private static List<RatingGroup> getAllRatingGroups(RatingGroup ratingGroup) {
        List<RatingGroup> groups = new ArrayList<>(List.of(ratingGroup));
        for (var group : ratingGroup.listSubGroups()) {
            groups.addAll(getAllRatingGroups(group));
        }

        return groups;
    }

    private void createMistakeButtons(ActiveAssessment assessment) {
        for (var ratingGroup : assessment.getGradingConfig().getRatingGroups()) {
            if (ratingGroup.getAllMistakeTypes().isEmpty()) {
                continue;
            }

            for (RatingGroup group : getAllRatingGroups(ratingGroup)) {
                List<MistakeType> mistakeTypes = getDirectMistakeTypes(group);
                if (mistakeTypes.isEmpty()) {
                    continue;
                }

                var panel = addGroupPanel(assessment, group, mistakeTypes);
                this.content.add(panel, "growx");
            }
        }

        assessment.registerAnnotationsUpdatedListener(annotations -> {
            var a = assessment.getAssessment();
            updateRatingGroupTitles(a);
            updateButtonIcons(a);
        });

        this.updateUI();
    }

    private void updateRatingGroupTitles(Assessment assessment) {
        for (var entry : this.ratingGroupBorders.entrySet()) {
            var ratingGroup = entry.getKey();
            var separator = entry.getValue();
            separator.setText(getRatingGroupTitle(assessment, ratingGroup));
        }
    }

    private void updateButtonIcons(Assessment assessment) {
        for (AssessmentButton assessmentButton : this.assessmentButtons) {
            var settings = ArtemisSettingsState.getInstance();
            var mistakeType = assessmentButton.mistakeType();

            StringBuilder iconText = new StringBuilder();
            ThemeColor color;
            Font font = JBFont.regular();

            if (mistakeType.getReporting().shouldScore()) {
                int count = (int)
                        assessment.streamAllAnnotations(mistakeType, false).count();
                var rule = mistakeType.getRule();

                switch (rule) {
                    case ThresholdPenaltyRule thresholdRule -> {
                        iconText.append(count).append("/").append(thresholdRule.getThreshold());
                        if (count >= thresholdRule.getThreshold()) {
                            color = settings.getFinishedAssessmentButtonColor();
                            font = font.deriveFont(Map.of(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON));
                        } else {
                            color = settings.getActiveAssessmentButtonColor();
                        }
                    }
                    case CustomPenaltyRule customPenaltyRule -> {
                        iconText.append("C");
                        color = settings.getActiveAssessmentButtonColor();
                    }
                    case StackingPenaltyRule stackingPenaltyRule -> {
                        iconText.append(count);
                        color = settings.getActiveAssessmentButtonColor();
                    }
                }

                // find out how many points this button subtracts
                Optional<Points> pointsSubtractedByButton = assessment.calculatePointsForMistakeType(mistakeType);

                // annotate the amount of points subtracted by this button
                pointsSubtractedByButton.ifPresentOrElse(
                        points -> iconText.append(" | ").append(points.score()).append("P"),
                        () -> iconText.append(" | 0P"));

            } else {
                iconText.append("R");
                color = settings.getReportingAssessmentButtonColor();
            }

            assessmentButton.iconRenderer().update(iconText.toString(), color.toColor());
            assessmentButton.button().setForeground(color.toColor());
            assessmentButton.button().setFont(font);
        }
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
                            IntellijUtil.colorToCSS(JBColor.GREEN),
                            testPoints,
                            IntellijUtil.colorToCSS(JBColor.GREEN),
                            Math.abs(annotationPoints),
                            totalPoints,
                            maxPoints);
        } else {
            return """
                    <html> <h2><span style="color: %s">%.1f</span> <span style="color: %s">- %.1f</span> = %.1f of %.1f</h2></html>"""
                    .formatted(
                            IntellijUtil.colorToCSS(JBColor.GREEN),
                            testPoints,
                            IntellijUtil.colorToCSS(JBColor.RED),
                            Math.abs(annotationPoints),
                            totalPoints,
                            maxPoints);
        }
    }

    private static class MistakeTypeIconRenderer {
        private final JBFont font;

        private String text;
        private Color bgColor;

        private int textWidth;
        private int baselineHeight;
        private int textHeight;

        public MistakeTypeIconRenderer() {
            this.font = JBFont.regular();
            this.update("", JBColor.foreground());
        }

        public void paint(Graphics2D g, JComponent component) {
            g.setFont(this.font);

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
