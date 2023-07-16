package edu.kit.kastel.wrappers;

import edu.kit.kastel.sdq.artemis4j.api.artemis.exam.Exam;
import org.jetbrains.annotations.Nullable;

/**
 * An exam that can be displayed in the UI by calling the toString method.
 * The wrapped value is Nullable. If exam is null its String representation
 * will be {@value EMPTY_EXAM_REPRESENTATION}.
 */
public class DisplayableExam extends Displayable<Exam> {

  private static final String EMPTY_EXAM_REPRESENTATION = "No exam selected";

  public DisplayableExam(@Nullable Exam exam) {
    super(exam);
  }


  @Override
  public String toString() {
    if (this.getWrappedValue() != null) {
      return this.getWrappedValue().getTitle();
    } else {
      return EMPTY_EXAM_REPRESENTATION;
    }
  }
}
