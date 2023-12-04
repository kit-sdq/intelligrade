package edu.kit.kastel.wrappers;

import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.api.artemis.Exercise;
import edu.kit.kastel.sdq.artemis4j.api.artemis.assessment.LockResult;
import edu.kit.kastel.sdq.artemis4j.api.artemis.assessment.Submission;
import edu.kit.kastel.utils.ArtemisUtils;

public class ExtendedLockResult {

  private final Integer submissionId;
  private final Exercise exercise;

  private final LockResult submissionLock;

  public ExtendedLockResult(Integer submissionId, Exercise exercise, LockResult submissionLock) {
    this.submissionId = submissionId;
    this.exercise = exercise;
    this.submissionLock = submissionLock;
  }

  public Integer getLockedSubmissionId() {
    return submissionId;
  }

  public Exercise getExercise() {
    return exercise;
  }

  public Submission getSubmission() throws ArtemisClientException {
    return ArtemisUtils
            .getArtemisClientInstance()
            .getSubmissionArtemisClient()
            .getSubmissionById(exercise, submissionId);
  }

  public LockResult getSubmissionLock() {
    return submissionLock;
  }
}
