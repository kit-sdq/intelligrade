package edu.kit.kastel.listeners;

import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.api.artemis.Exercise;
import edu.kit.kastel.sdq.artemis4j.grading.config.ExerciseConfig;
import edu.kit.kastel.sdq.artemis4j.grading.config.JsonFileConfig;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.wrappers.DisplayableExercise;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * This listener handles selection of a new exercise.
 */
public class ExerciseSelectedListener implements ItemListener {
  public static @Nullable JsonFileConfig fileConfig;

  private static final String CONFIG_FILE_LOAD_ERROR_FORMATTER = "Error loading config at %s!";
  private static final String NO_EXERCISE_SELECTED_ERROR =
          "Please select an exercise to begin grading.";

  private static final String EXERCISE_INVALID_FORMATTER =
          "You are trying to grade \"%s\" with a config for \"%s\"!";


  @Override
  public void itemStateChanged(ItemEvent itemEvent) {
    //to avoid errors when Item is only deselected
    if (itemEvent.getStateChange() == ItemEvent.DESELECTED) {
      return;
    }

    ExerciseConfig configForExercise;
    Exercise selected = ((DisplayableExercise) Objects.requireNonNull(itemEvent.getItem()))
            .getWrappedValue();

    //we cannot load a config if no exercise is selected
    if (ExerciseSelectedListener.fileConfig == null) {
      ArtemisUtils.displayGenericErrorBalloon(NO_EXERCISE_SELECTED_ERROR);
      return;
    }

    //try loading the config for an exercise
    try {
      configForExercise = ExerciseSelectedListener.fileConfig.getExerciseConfig(selected);
    } catch (IOException e) {
      ArtemisUtils.displayGenericErrorBalloon(
              String.format(
                      ExerciseSelectedListener.CONFIG_FILE_LOAD_ERROR_FORMATTER,
                      ArtemisSettingsState.getInstance().getSelectedGradingConfigPath()
              )
      );
      //stop any further parse attempts if loading file failed
      return;
    }

    //if the exercise that is to be graded is invalid for this Config
    if (!configForExercise.getAllowedExercises().contains(selected.getExerciseId())) {
      ArtemisUtils.displayGenericErrorBalloon(
              String.format(
                      EXERCISE_INVALID_FORMATTER,
                      selected.getShortName(),
                      configForExercise.getShortName()
              )
      );
      return;
    }
  }

  public static synchronized void updateJsonConfig(JsonFileConfig jsonFileConfig) {
    ExerciseSelectedListener.fileConfig = jsonFileConfig;
  }
}
