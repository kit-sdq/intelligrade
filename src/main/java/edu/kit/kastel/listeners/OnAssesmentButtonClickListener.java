package edu.kit.kastel.listeners;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import edu.kit.kastel.sdq.artemis4j.api.grading.IAnnotation;
import edu.kit.kastel.sdq.artemis4j.grading.model.MistakeType;
import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.AnnotationException;
import edu.kit.kastel.sdq.artemis4j.util.Pair;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.AssessmentUtils;
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

    if (!AssessmentUtils.isAssesmentMode()) {
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

    //selection may be a bit funky...
    Pair<VisualPosition, VisualPosition> selectedLines =
            new Pair<>(editor.getSelectionModel().getSelectionStartPosition(),
                    editor.getSelectionModel().getSelectionEndPosition()
            );


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


    //create and add the annotation
    Annotation annotation = new Annotation(IAnnotation.createID(),
            this.mistakeType,
            selectedLines.first().line,
            selectedLines.second().line,
            FilenameUtils.removeExtension((subtracted.toString())),
            "",
            0.0
    );

    try {
      AssessmentUtils.addAnnotation(annotation);
    } catch (AnnotationException e) {
      ArtemisUtils.displayGenericErrorBalloon(ANNOT_ADD_ERR);
      System.err.println(e.getMessage());
    }

    //TODO: highlight text in editor
  }
}
