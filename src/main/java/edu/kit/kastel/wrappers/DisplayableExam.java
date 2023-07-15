package edu.kit.kastel.wrappers;

import edu.kit.kastel.sdq.artemis4j.api.artemis.exam.Exam;
import org.jetbrains.annotations.Nullable;

public class DisplayableExam extends Displayable<Exam> {

  public DisplayableExam(@Nullable Exam exam) {
    super(exam);
  }


  @Override
  public String toString() {
    if (this.getWrappedValue() != null) {
      return this.getWrappedValue().getTitle();
    } else {
      return "No exam selected";
    }
  }
}
