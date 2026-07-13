/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.listeners;

import java.util.EventListener;
import java.util.List;

import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Project-level listener that is notified when the assessment changes.
 *
 * <p>The plugin publishes these events on the EDT because most subscribers update UI state.
 */
public interface AssessmentStateListener extends EventListener {
    @Topic.ProjectLevel
    Topic<AssessmentStateListener> TOPIC = Topic.create("Assessment state changed", AssessmentStateListener.class);

    @RequiresEdt
    default void activeAssessmentChanged(@Nullable ActiveAssessment assessment) {}

    @RequiresEdt
    default void annotationsChanged(@NonNull ActiveAssessment assessment, @NonNull List<Annotation> annotations) {}
}
