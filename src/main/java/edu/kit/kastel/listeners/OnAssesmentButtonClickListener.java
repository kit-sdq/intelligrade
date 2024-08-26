/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.listeners;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import edu.kit.kastel.sdq.artemis4j.grading.model.MistakeType;
import edu.kit.kastel.utils.AnnotationUtils;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents a generic listener that is called if an assessment button
 * is clicked. It should create and save the new annotation.
 */
public class OnAssesmentButtonClickListener implements ActionListener {

    private final MistakeType mistakeType;

    public OnAssesmentButtonClickListener(MistakeType mistakeType) {
        this.mistakeType = mistakeType;
    }

    @Override
    public void actionPerformed(@NotNull ActionEvent actionEvent) {
        AnnotationUtils.addAnnotationByMistakeType(this.mistakeType);
    }
}
