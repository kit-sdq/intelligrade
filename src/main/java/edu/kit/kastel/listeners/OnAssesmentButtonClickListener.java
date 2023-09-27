package edu.kit.kastel.listeners;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import edu.kit.kastel.sdq.artemis4j.grading.model.MistakeType;
import edu.kit.kastel.sdq.artemis4j.util.Pair;
import edu.kit.kastel.utils.ArtemisUtils;
import edu.kit.kastel.utils.AssessmentUtils;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * This class represents a generic listener that is called if an assessment button
 * is clicked. It should create and save the new annotation.
 */
public class OnAssesmentButtonClickListener implements ActionListener {

  private static final String NO_ASSESSMENT_MSG = "Please start an essessment first";

  private final MistakeType mistakeType;


  public OnAssesmentButtonClickListener(MistakeType mistakeType) {
    this.mistakeType = mistakeType;
  }

  @Override
  public void actionPerformed(ActionEvent actionEvent) {
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


    //get the currently selected element
    PsiElement selectedElement = PsiDocumentManager
            .getInstance(currentProject)
            .getPsiFile(editor.getDocument())
            .findElementAt(editor.getCaretModel().getOffset())
            .getContext();

    //we only grade java files
    if (selectedElement.getContainingFile().getFileType().equals(JavaFileType.INSTANCE)) {
      System.err.println("Not a java class");
    }

    //TODO: get "fully qualified class name" at caret

//    System.out.println(
//            PsiTreeUtil.getParentOfType(selectedElement, PsiClass.class)
//                    .getContainingClass()
//                    .getQualifiedName()
//    );
//
//
//    PsiJavaFile containingFile = ((PsiJavaFile) selectedElement.getContainingFile()).;


//    AssessmentUtils.getAnnotationManager().addAnnotation(
//            IAnnotation.createID(),
//            this.mistakeType,
//            selectedLines.first().line,
//            selectedLines.second().line,
//            //get currently opened File name
//            ,
//            "",
//            0
//    );

  }
}
