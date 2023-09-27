package edu.kit.kastel.utils;

import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.AnnotationManagement;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Holds all information and meta on assessments that are required globally.
 * Some Information are:
 * - all assessment annotations
 * - whether assessment mode is currently enabled
 */
public final class AssessmentUtils {
  private static boolean assesmentMode = false;

  private static AnnotationManagement annotationManager = new AnnotationManagement();

  private AssessmentUtils() {
    throw new IllegalAccessError("Utility Class constructor");
  }

  public static void enabeleAssessmentMode() {
    AssessmentUtils.assesmentMode = true;
    AssessmentUtils.resetAnnotations();
  }

  public static void disableAssessmentMode() {
    AssessmentUtils.assesmentMode = false;
  }

  public static boolean isAssesmentMode() {
    return AssessmentUtils.assesmentMode;
  }

  public static AnnotationManagement getAnnotationManager() {
    return annotationManager;
  }

  public static void resetAnnotations() {
    AssessmentUtils.annotationManager = new AnnotationManagement();
  }

}
