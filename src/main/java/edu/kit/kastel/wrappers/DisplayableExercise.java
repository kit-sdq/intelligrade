package edu.kit.kastel.wrappers;

import edu.kit.kastel.sdq.artemis4j.api.artemis.Exercise;

public class DisplayableExercise extends Displayable<Exercise> {

  public DisplayableExercise(Exercise exercise) {
    super(exercise);
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", this.getWrappedValue().getTitle(), this.getWrappedValue().getShortName());
  }
}
