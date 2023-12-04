package edu.kit.kastel.wrappers;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import edu.kit.kastel.sdq.artemis4j.api.grading.IMistakeType;
import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.Annotation;

public class AnnotationWithTextSelection extends Annotation {

  RangeHighlighter mistakeHighlighter;

  public AnnotationWithTextSelection(String uuid,
                                     IMistakeType mistakeType,
                                     int startLine,
                                     int endLine,
                                     String fullyClassifiedClassName,
                                     String customMessage,
                                     Double customPenalty,
                                     RangeHighlighter pMistakeHighlighter) {
    super(uuid, mistakeType, startLine, endLine, fullyClassifiedClassName, customMessage, customPenalty);
    this.mistakeHighlighter = pMistakeHighlighter;
  }

  /**
   * Deletes the mistake Highlighter associated with this Annotation
   */
  public void deleteHighlighter() {
    Project currentProject = ProjectManager.getInstance().getOpenProjects()[0];
    Editor editor = FileEditorManager
            .getInstance(currentProject)
            .getSelectedTextEditor();

    editor.getMarkupModel().removeHighlighter(mistakeHighlighter);
  }
}
