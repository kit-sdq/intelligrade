/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.guis;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.intellij.DynamicBundle;
import edu.kit.kastel.sdq.artemis4j.grading.model.annotation.Annotation;
import edu.kit.kastel.utils.AssessmentUtils;
import edu.kit.kastel.wrappers.AnnotationWithTextSelection;

/**
 * The table model for the annotations table.
 */
public class AnnotationsTableModel extends AbstractTableModel {

    private static final String[] HEADINGS = {
        "Mistake type", "Start Line", "End Line", "File", "Message", "Custom Penalty"
    };

    private static final String LOCALE = DynamicBundle.getLocale().getLanguage();

    private final List<AnnotationWithTextSelection> annotations = new ArrayList<>();

    @Override
    public int getRowCount() {
        return annotations.size();
    }

    @Override
    public int getColumnCount() {
        return HEADINGS.length;
    }

    @Override
    public String getColumnName(int column) {
        return HEADINGS[column];
    }

    @Override
    public Object getValueAt(int i, int i1) {
        Annotation queriedAnnotation = annotations.get(i);

        if (queriedAnnotation == null) {
            return "";
        }

        return switch (i1) {
            case 0 -> queriedAnnotation.getMistakeType().getButtonText(LOCALE);
            case 1 -> queriedAnnotation.getStartLine();
            case 2 -> queriedAnnotation.getEndLine();
            case 3 -> queriedAnnotation.getClassFilePath();
            case 4 -> queriedAnnotation.getCustomMessage().orElse("");
            case 5 -> queriedAnnotation.getCustomPenalty().orElse(0.0);
            default -> {
                System.err.printf("No table data at index %d:%d\n", i, i1);
                yield "n.A.";
            }
        };
    }

    /**
     * Add an annotation to the Table model.
     *
     * @param annotation the Annotation to be added
     */
    public void addAnnotation(AnnotationWithTextSelection annotation) {
        this.annotations.add(annotation);
    }

    /**
     * Delete an annotation with a specified index from the Table, its model and the AnnotationManager
     *
     * @param itemIndex the index of the item to be deleted TODO: not necesarrily the same as
     *                  the selected item if a TableSorter is used. Implement sorting.
     */
    public void deleteItem(int itemIndex) {
        AnnotationWithTextSelection annotation = this.annotations.get(itemIndex);
        annotation.deleteHighlighter();
        AssessmentUtils.deleteAnnotation(annotation);
        this.annotations.remove(annotation);
    }
}
