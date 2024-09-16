package edu.kit.kastel.extensions.guis;

import com.intellij.DynamicBundle;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBFont;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.MistakeType;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.RatingGroup;
import edu.kit.kastel.state.ActiveAssessment;
import edu.kit.kastel.state.PluginState;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import java.awt.event.ActionEvent;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

public class AssessmentPanel extends SimpleToolWindowPanel {
    private static final Locale LOCALE = DynamicBundle.getLocale();

    private final JPanel content;
    private final Map<RatingGroup, TitledBorder> ratingGroupBorders = new IdentityHashMap<>();
    private final Map<MistakeType, JButton> mistakeTypeButtons = new IdentityHashMap<>();

    public AssessmentPanel() {
        super(true, true);

        content = new JBPanel<>(new MigLayout("wrap 1", "[grow]"));
        setContent(ScrollPaneFactory.createScrollPane(content));

        this.showNoActiveAssessment();
        PluginState.getInstance().registerAssessmentStartedListener(this::createMistakeButtons);
        PluginState.getInstance().registerAssessmentClosedListener(this::showNoActiveAssessment);
    }

    private void createMistakeButtons(ActiveAssessment assessment) {
        content.removeAll();

        int buttonsPerRow = ArtemisSettingsState.getInstance().getColumnsPerRatingGroup();
        for (var ratingGroup : assessment.getGradingConfig().getRatingGroups()) {
            if (ratingGroup.getMistakeTypes().isEmpty()) {
                continue;
            }

            var panel = new JBPanel<>(new MigLayout("wrap " + buttonsPerRow));

            var border = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(JBColor.border()),
                    String.format(getRatingGroupTitle(assessment, ratingGroup))
            );
            border.setTitleFont(JBFont.h3().asBold());
            panel.setBorder(border);
            this.ratingGroupBorders.put(ratingGroup, border);

            for (var mistakeType : ratingGroup.getMistakeTypes()) {
                var button = new JButton(mistakeType.getButtonText().translateTo(LOCALE));

                if (mistakeType.getReporting().shouldScore()) {
                    button.setForeground(JBColor.foreground());
                } else {
                    button.setForeground(JBColor.LIGHT_GRAY);
                }

                button.addActionListener(a -> assessment.addAnnotationAtCaret(mistakeType, (a.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK));
                panel.add(button);
                this.mistakeTypeButtons.put(mistakeType, button);
            }

            this.content.add(panel, "growx");
        }

        assessment.registerAnnotationsUpdatedListener(annotations -> {
            // Update rating group titles
            for (var entry : this.ratingGroupBorders.entrySet()) {
                var ratingGroup = entry.getKey();
                var border = entry.getValue();
                border.setTitle(getRatingGroupTitle(assessment, ratingGroup));
            }
        });

        this.updateUI();
    }

    private void showNoActiveAssessment() {
        this.ratingGroupBorders.clear();
        this.mistakeTypeButtons.clear();

        content.removeAll();
        content.add(new JBLabel("No active assessment"), "growx");
        this.updateUI();
    }

    private String getRatingGroupTitle(ActiveAssessment assessment, RatingGroup ratingGroup) {
        var points = assessment.getAssessment().calculatePointsForRatingGroup(ratingGroup);
        return "%s (%.1f of [%.1f, %.1f])".formatted(
                ratingGroup.getDisplayName().translateTo(LOCALE),
                points.score(),
                ratingGroup.getMinPenalty(),
                ratingGroup.getMaxPenalty()
        );
    }
}
