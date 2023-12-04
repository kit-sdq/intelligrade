package edu.kit.kastel.listeners;

import com.intellij.DynamicBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import edu.kit.kastel.extensions.guis.AssessmentViewContent;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.api.artemis.Exercise;
import edu.kit.kastel.sdq.artemis4j.api.artemis.ExerciseStats;
import edu.kit.kastel.sdq.artemis4j.grading.config.ExerciseConfig;
import edu.kit.kastel.sdq.artemis4j.grading.config.JsonFileConfig;
import edu.kit.kastel.sdq.artemis4j.grading.model.MistakeType;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.wrappers.DisplayableExercise;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This listener handles selection of a new exercise.
 */
public class ExerciseSelectedListener implements ItemListener {
  private static @Nullable JsonFileConfig fileConfig;

  private static final String CONFIG_FILE_LOAD_ERROR_FORMATTER = "Error loading config at %s!";
  private static final String NO_EXERCISE_SELECTED_ERROR =
          "Please select an exercise to begin grading.";

  private static final String EXERCISE_INVALID_FORMATTER =
          "You are trying to grade \"%s\" with a config for \"%s\"!";

  private static final String LOCALE = DynamicBundle.getLocale().getLanguage();

  /**
   * Hold a reference to the UI, so we can dynamically modify components.
   */
  private final AssessmentViewContent gui;

  public ExerciseSelectedListener(AssessmentViewContent gui) {
    this.gui = gui;
  }

  @Override
  public void itemStateChanged(@NotNull ItemEvent itemEvent) {
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
      //display error message
      ArtemisUtils.displayGenericErrorBalloon(
              String.format(
                      EXERCISE_INVALID_FORMATTER,
                      selected.getShortName(),
                      configForExercise.getShortName()
              )
      );
      //grey out assessment buttons
      gui.getBtnGradingRound1().setEnabled(false);
      return;
    }
    //enable assessment button because it may still be greyed out
    gui.getBtnGradingRound1().setEnabled(true);
    addRatingGroups(configForExercise);

    //update statistics
    ArtemisUtils.updateStats(selected, gui.getStatisticsContainer());

  }

  private void addRatingGroups(@NotNull ExerciseConfig configForExercise) {

    //clear content before adding new
    gui.getRatingGroupContainer().removeAll();

    //add all rating groups
    configForExercise.getRatingGroups().stream()
            //only add assessment group if it is non-empty
            .filter(ratingGroup -> !ratingGroup.getMistakeTypes().isEmpty())
            .forEach(ratingGroup -> {

              //calculate grid size
              int colsPerRatingGroup = ArtemisSettingsState
                      .getInstance()
                      .getColumnsPerRatingGroup();
              int numRows = ratingGroup.getMistakeTypes().size() / colsPerRatingGroup;

              //create a panel of appropriate size for each rating group
              JPanel ratingCroupContainer = new JPanel(
                      new GridLayout(numRows + 1, colsPerRatingGroup)
              );

              ratingCroupContainer.setBorder(
                      BorderFactory.createTitledBorder(
                              BorderFactory.createLineBorder(JBColor.LIGHT_GRAY),
                              String.format("%s [%.2f of %.2f]",
                                      ratingGroup.getDisplayName(LOCALE),
                                      ratingGroup.getRange().second(),
                                      ratingGroup.getRange().first()
                              )
                      )

              );

              //add buttons to rating group
              ratingGroup.getMistakeTypes().stream()
                      //sort buttons alphabetically
                      //TODO: for some reason this is broken
                      .sorted(Comparator.comparing(mistake -> mistake.getButtonText(LOCALE)))
                      .forEach(mistakeType -> {
                        //create button, add listener and add it to the container
                        JButton assessmentButton = new JButton(mistakeType.getButtonText(LOCALE));
                        assessmentButton.addActionListener(
                                new OnAssesmentButtonClickListener((MistakeType) mistakeType)
                        );
                        ratingCroupContainer.add(assessmentButton);
                      });

              gui.getRatingGroupContainer().add(ratingCroupContainer);

            });

  }

  public static synchronized void updateJsonConfig(JsonFileConfig jsonFileConfig) {
    ExerciseSelectedListener.fileConfig = jsonFileConfig;
  }
}
