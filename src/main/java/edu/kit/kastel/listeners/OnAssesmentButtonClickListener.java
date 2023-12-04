package edu.kit.kastel.listeners;

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
import edu.kit.kastel.sdq.artemis4j.api.grading.IAnnotation;
import edu.kit.kastel.sdq.artemis4j.grading.model.MistakeType;
import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.AnnotationException;
import edu.kit.kastel.state.AssessmentModeHandler;
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

  private static final String NO_ASSESSMENT_MSG = "Please start an assessment first";

  private static final String ANNOT_ADD_ERR = "Error adding annotation.";

  private final MistakeType mistakeType;


  public OnAssesmentButtonClickListener(MistakeType mistakeType) {
    this.mistakeType = mistakeType;
  }

  @Override
  public void actionPerformed(@NotNull ActionEvent actionEvent) {

    if (!AssessmentModeHandler.getInstance().isInAssesmentMode()) {
      ArtemisUtils.displayGenericErrorBalloon(NO_ASSESSMENT_MSG);
      return;
    }

    Project currentProject = ProjectManager.getInstance().getOpenProjects()[0];

    Editor editor = FileEditorManager
            .getInstance(currentProject)
            .getSelectedTextEditor();

    if (editor == null || !editor.getSelectionModel().hasSelection()) {
      //no editor open or no selection made
      return;
    }

    //get editor Selection
    TextRange selectedText = editor.getCaretModel().getPrimaryCaret().getSelectionRange();

    //only annotate if a selection has been made
    //get the currently selected element and the containing file
    PsiElement selectedElement = PsiDocumentManager
            .getInstance(currentProject)
            .getPsiFile(editor.getDocument())
            .findElementAt(editor.getCaretModel().getOffset())
            .getContext();


    Path subtracted = Paths.get(
            selectedElement.getProject().getBasePath()
    ).relativize(
            selectedElement.getContainingFile().getVirtualFile().toNioPath()
    );


    //Add highlight in Editor
    //TODO: Create config entry for color
    TextAttributes annotationMarkup = new TextAttributes(
            null,
            new JBColor(new Color(155, 54, 54), new Color(155, 54, 54)),
            null,
            EffectType.ROUNDED_BOX,
            Font.PLAIN

    );

    RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(
            selectedText.getStartOffset(),
            selectedText.getEndOffset(),
            HighlighterLayer.SELECTION - 1,
            annotationMarkup,
            HighlighterTargetArea.EXACT_RANGE
    );

    //create and add the annotation
    var annotation = new AnnotationWithTextSelection(IAnnotation.createID(),
            this.mistakeType,
            //lines are 0 indexed
            editor.getCaretModel().getPrimaryCaret().getSelectionStartPosition().getLine() + 1,
            editor.getCaretModel().getPrimaryCaret().getSelectionEndPosition().getLine() + 1,
            FilenameUtils.removeExtension(subtracted.toString()),
            "",
            0.0,
            highlighter
    );

    try {
      AssessmentUtils.addAnnotation(annotation);
    } catch (AnnotationException e) {
      ArtemisUtils.displayGenericErrorBalloon(ANNOT_ADD_ERR);
      System.err.println(e.getMessage());
      //if an adding the annotation occurs, we remove the highlighter
      editor.getMarkupModel().removeHighlighter(highlighter);
    }


  }
}
