package edu.kit.kastel.listeners;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.artemis4j.api.grading.IAnnotation;
import edu.kit.kastel.sdq.artemis4j.grading.model.MistakeType;
import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.AnnotationException;
import edu.kit.kastel.state.AssessmentModeHandler;
import edu.kit.kastel.utils.AnnotationUtils;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.AssessmentUtils;
import edu.kit.kastel.wrappers.AnnotationWithTextSelection;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.FilenameUtils;
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
