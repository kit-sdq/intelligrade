package edu.kit.kastel.wrappers;

import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.api.artemis.Exercise;
import edu.kit.kastel.sdq.artemis4j.api.artemis.assessment.LockResult;
import edu.kit.kastel.sdq.artemis4j.api.artemis.assessment.Submission;
import edu.kit.kastel.utils.ArtemisUtils;

public class ExtendedLockResult {

  private final LockResult lock;
  private final Exercise exercise;

  public ExtendedLockResult(LockResult lock, Exercise exercise) {
    this.lock = lock;
    this.exercise = exercise;
  }

  public LockResult getLock() {
    return lock;
  }

  public Exercise getExercise() {
    return exercise;
  }

  public Submission getSubmission() throws ArtemisClientException {
    return ArtemisUtils
            .getArtemisClientInstance()
            .getSubmissionArtemisClient()
            .getSubmissionById(exercise, lock.getSubmissionId());
  }
}
