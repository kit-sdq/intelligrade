package edu.kit.kastel.utils;

import edu.kit.kastel.sdq.artemis4j.api.grading.IAnnotation;
import edu.kit.kastel.sdq.artemis4j.grading.config.ExerciseConfig;
import edu.kit.kastel.sdq.artemis4j.grading.config.GradingConfig;
import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.AnnotationException;
import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.AnnotationManagement;
import edu.kit.kastel.state.AssessmentModeHandler;
import edu.kit.kastel.wrappers.AnnotationWithTextSelection;
import edu.kit.kastel.wrappers.PlugInEventListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;

/**
 * Holds all information and meta on assessments that are required globally.
 * Some Information are:
 * - all assessment annotations
 * - whether assessment mode is currently enabled
 */
public final class AssessmentUtils {
  private static List<PlugInEventListener> assesmentListeners = new ArrayList<>();

  private static AnnotationManagement annotationManager = new AnnotationManagement();

  private static AnnotationWithTextSelection latestAnnotation;

  private static GradingConfig config;

  private static final String ERROR_GETTING_EXERCISE_CONFIG = "IO Error while obtaining an exercise config";

  private AssessmentUtils() {
    throw new IllegalAccessError("Utility Class constructor");
  }

  public static void initExerciseConfig(GradingConfig config) {
    AssessmentUtils.config = config;
  }

  /**
   * Get the exercise config from the saved grading config or Optional#empty if no lock is currently held
   *
   * @return the exercise config behind the current grading config or Empty if no lock is currently held
   */
  public static Optional<ExerciseConfig> getConfigAsExerciseCfg() {
    AtomicReference<Optional<ExerciseConfig>> returnValue = new AtomicReference<>(Optional.empty());
    //we can only obtain a config if a lock is currently held
    AssessmentModeHandler.getInstance().getAssessmentLock().ifPresent(extendedLockResult -> {
      try {
        returnValue.set(Optional.of(AssessmentUtils.config.getExerciseConfig(extendedLockResult.getExercise())));
      } catch (IOException e) {
        ArtemisUtils.displayGenericErrorBalloon(ERROR_GETTING_EXERCISE_CONFIG);
      }
    });

    return returnValue.get();
  }

  /**
   * add an annotation and update all listening components.
   *
   * @param annotation the annotation to be added
   * @throws AnnotationException if adding the annotation fails
   */
  public static void addAnnotation(@NotNull AnnotationWithTextSelection annotation) throws AnnotationException {

    AssessmentUtils.annotationManager.addAnnotation(
            annotation.getUUID(),
            annotation.getMistakeType(),
            annotation.getStartLine(),
            annotation.getEndLine(),
            annotation.getClassFilePath(),
            annotation.getCustomMessage().orElse(""),
            annotation.getCustomPenalty().orElse(0.0)
    );

    //add latest annotation so UI can use it
    latestAnnotation = annotation;

    //trigger each event so that all assessment views are updated
    assesmentListeners.forEach(PlugInEventListener::trigger);
  }

  public static void deleteAnnotation(@NotNull Annotation annotation) {
    AssessmentUtils.annotationManager.removeAnnotation(annotation.getUUID());
  }

  public static List<IAnnotation> getAllAnnotations() {
    return new ArrayList<>(AssessmentUtils.annotationManager.getAnnotations());
  }

  public static void resetAnnotations() {
    AssessmentUtils.annotationManager = new AnnotationManagement();
  }

  public static void registerAssessmentListener(PlugInEventListener assessmentListener) {
    AssessmentUtils.assesmentListeners.add(assessmentListener);
  }

  public static void resetAssessmentListeners() {
    assesmentListeners = new ArrayList<>();
  }

  public static AnnotationWithTextSelection getLatestAnnotation() {
    return latestAnnotation;
  }
}
