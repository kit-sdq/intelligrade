/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.util.Collection;
import java.util.List;

import javax.swing.text.JTextComponent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanel;
import edu.kit.kastel.sdq.artemis4j.client.AssessmentStatsDTO;
import edu.kit.kastel.sdq.artemis4j.grading.PackedAssessment;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.intelligrade.listeners.ExerciseListener;
import edu.kit.kastel.sdq.intelligrade.state.ProjectState;
import edu.kit.kastel.sdq.intelligrade.utils.LatestRequestRunner;
import edu.kit.kastel.sdq.intelligrade.widgets.TextBuilder;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class StatisticsPanel extends JBPanel<StatisticsPanel> {
    private final JTextComponent totalStatisticsLabel;
    private final JTextComponent userStatisticsLabel;

    private final LatestRequestRunner latestRequestRunner;

    StatisticsPanel(Disposable parentDisposable, @NonNull Project project) {
        super(new MigLayout("wrap 2", "[][grow]"));

        this.latestRequestRunner = new LatestRequestRunner(project);

        ProjectState.getInstance(project).subscribe(parentDisposable, new ExerciseListener() {
            @Override
            public void assessmentsChanged(@Nullable ProgrammingExercise exercise, List<PackedAssessment> assessments) {
                if (exercise == null) {
                    return;
                }

                latestRequestRunner
                        .fetchArtemis(exercise::fetchAssessmentStats)
                        .withErrorNotification("Failed to fetch statistics")
                        .thenIf(
                                () -> ProjectState.getInstance(project)
                                                .getActiveExercise()
                                                .orElse(null)
                                        == exercise,
                                stats -> {
                                    totalStatisticsLabel.setText(formatTotalStatistics(exercise, stats));
                                    userStatisticsLabel.setText(formatUserStatistics(assessments));
                                });
            }
        });

        add(TextBuilder.immutable("Submissions:").text());
        totalStatisticsLabel = TextBuilder.immutable("").text();
        add(totalStatisticsLabel);

        add(TextBuilder.immutable("Your Assessments:").text());
        userStatisticsLabel = TextBuilder.immutable("").text();
        add(userStatisticsLabel);
    }

    private static String formatTotalStatistics(ProgrammingExercise exercise, AssessmentStatsDTO stats) {
        if (exercise.hasSecondCorrectionRound()) {
            return "%d / %d / %d (%d locked)"
                    .formatted(
                            stats.numberOfAssessmentsOfCorrectionRounds()
                                    .getFirst()
                                    .inTime(),
                            stats.numberOfAssessmentsOfCorrectionRounds().get(1).inTime(),
                            stats.numberOfSubmissions().inTime(),
                            stats.totalNumberOfAssessmentLocks());
        }

        return "%d / %d (%d locked)"
                .formatted(
                        stats.numberOfAssessmentsOfCorrectionRounds().getFirst().inTime(),
                        stats.numberOfSubmissions().inTime(),
                        stats.totalNumberOfAssessmentLocks());
    }

    private static String formatUserStatistics(Collection<PackedAssessment> assessments) {
        int submittedSubmissions =
                (int) assessments.stream().filter(PackedAssessment::isSubmitted).count();
        int lockedSubmissions = assessments.size() - submittedSubmissions;
        return "%d (%d locked)".formatted(submittedSubmissions, lockedSubmissions);
    }
}
