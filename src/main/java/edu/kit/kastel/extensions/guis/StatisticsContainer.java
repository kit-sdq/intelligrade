/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.guis;

import com.intellij.ui.components.JBLabel;
import edu.kit.kastel.sdq.artemis4j.grading.Exercise;

public class StatisticsContainer extends JBLabel {

    private static final String FETCH_STATS_FORMATTER = "Unable to fetch statistics for exercise %s";

    /**
     * Update the statistics label
     */
    public void triggerUpdate(Exercise selected) {
        // try {
        //     // ExerciseStats stats = ArtemisUtils.getArtemisClientInstance()
        //     //         .getAssessmentArtemisClient()
        //     //         .getStats(selected);
        //     // this.setText(String.format(
        //     //         "Your submissions: %d | corrected: %d/%d | locked: %d",
        //     //         stats.submittedByTutor(), stats.totalAssessments(), stats.totalSubmissions(),
        // stats.locked()));
        // } catch (ArtemisClientException e) {
        //     ArtemisUtils.displayGenericErrorBalloon(String.format(FETCH_STATS_FORMATTER, selected.getShortName()));
        //     Logger.getInstance(ExerciseSelectedListener.class).error(e);
        // }
    }
}
