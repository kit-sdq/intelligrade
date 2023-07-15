package edu.kit.kastel.wrappers;

import edu.kit.kastel.sdq.artemis4j.api.artemis.Course;

public class DisplayableCourse extends Displayable<Course> {

  public DisplayableCourse(Course item) {
    super(item);
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", this.getWrappedValue().getTitle(), this.getWrappedValue().getShortName());
  }
}
