/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.wrappers;

import edu.kit.kastel.sdq.artemis4j.api.artemis.Exercise;

/**
 * An exercise that can be displayed in the UI by calling the toString method.
 */
public class DisplayableExercise extends Displayable<Exercise> {

    public DisplayableExercise(Exercise exercise) {
        super(exercise);
    }

    @Override
    public String toString() {
        return String.format(
                "%s (%s)",
                this.getWrappedValue().getTitle(), this.getWrappedValue().getShortName());
    }
}
