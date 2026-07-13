/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.listeners;

import java.util.EventListener;
import java.util.List;

import com.intellij.openapi.Disposable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import edu.kit.kastel.sdq.artemis4j.grading.PackedAssessment;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.penalty.GradingConfig;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Project-level listener that is notified when the config or exercise changes.
 *
 * <p>The plugin publishes these events on the EDT because most subscribers update UI state.
 *
 * @see edu.kit.kastel.sdq.intelligrade.state.ProjectState#subscribe(Disposable, ExerciseListener)
 */
public interface ExerciseListener extends EventListener {
    @Topic.ProjectLevel
    Topic<ExerciseListener> TOPIC = Topic.create("Exercise might have changed", ExerciseListener.class);

    /**
     * This is called when the exercise has changed.
     *
     * @param exercise the current exercise or null if none has been selected
     */
    @RequiresEdt
    default void exerciseChanged(@Nullable ProgrammingExercise exercise) {}

    /**
     * This is called when the has config changed.
     * <p>
     * For example a new config is selected or none is selected.
     *
     * @param config the new config or null if there isn't one or an invalid one is currently selected.
     */
    @RequiresEdt
    default void configChanged(GradingConfig.@Nullable GradingConfigDTO config) {}

    /**
     * This is called when the assessments for an exercise change. For example a new assessment is added, because
     * the user started one or one has been removed, because the user canceled it.
     * <p>
     * This is a way to be notified when the {@link edu.kit.kastel.sdq.intelligrade.extensions.guis.BacklogPanel} changes.
     *
     * @param exercise the exercise the assessments belong to
     * @param assessments the assessments
     */
    @RequiresEdt
    default void assessmentsChanged(@NonNull ProgrammingExercise exercise, List<PackedAssessment> assessments) {}
}
