/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.state;

import java.awt.Color;
import java.util.Optional;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import edu.kit.kastel.sdq.artemis4j.grading.config.ExerciseConfig;
import edu.kit.kastel.utils.AssessmentUtils;
import edu.kit.kastel.wrappers.ExtendedLockResult;

/**
 * Class to handle assessment mode state. This class is a Singleton.
 */
public class AssessmentModeHandler {

    private static AssessmentModeHandler assessmentModeHandler;
    private static final String ASSESSMENT_MODE_ENABLED = "✅";
    private static final String ASSESSMENT_MODE_DISABLED = "❌";

    private boolean assessmentMode = false;
    private Optional<ExerciseConfig> currentExerciseConfig = Optional.empty();
    private Optional<ExtendedLockResult> assessmentLock;

    private Optional<JBLabel> indicatorLabel = Optional.empty();

    private AssessmentModeHandler() {}

    public static AssessmentModeHandler getInstance() {
        if (assessmentModeHandler == null) {
            assessmentModeHandler = new AssessmentModeHandler();
        }
        return assessmentModeHandler;
    }

    public void enableAssessmentMode(ExtendedLockResult assLock) {
        this.assessmentLock = Optional.of(assLock);
        this.assessmentMode = true;
        AssessmentUtils.resetAnnotations();
        this.indicatorLabel.ifPresent(jbLabel -> {
            jbLabel.setText(ASSESSMENT_MODE_ENABLED);
            jbLabel.setBackground(new JBColor(new Color(54, 155, 54), new Color(54, 155, 54)));
        });
    }

    public void disableAssessmentMode() {
        this.assessmentMode = false;
        this.assessmentLock = Optional.empty();
        this.indicatorLabel.ifPresent(jbLabel -> {
            jbLabel.setText(ASSESSMENT_MODE_DISABLED);
            jbLabel.setBackground(new JBColor(new Color(155, 54, 54), new Color(155, 54, 54)));
        });
    }

    public boolean isInAssesmentMode() {
        return this.assessmentMode;
    }

    public Optional<ExtendedLockResult> getAssessmentLock() {
        return assessmentLock;
    }

    public void connectIndicatorLabel(JBLabel label) {
        this.indicatorLabel = Optional.of(label);
    }

    public Optional<ExerciseConfig> getCurrentExerciseConfig() {
        return currentExerciseConfig;
    }

    public void setCurrentExerciseConfig(ExerciseConfig currentExerciseConfig) {
        this.currentExerciseConfig = Optional.of(currentExerciseConfig);
    }
}
