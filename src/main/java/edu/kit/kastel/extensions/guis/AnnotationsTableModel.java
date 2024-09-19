/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.guis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.table.AbstractTableModel;

import com.intellij.DynamicBundle;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;

/**
 * The table model for the annotations table.
 */
public class AnnotationsTableModel extends AbstractTableModel {

    private static final String[] HEADINGS = {"Mistake type", "Line(s)", "File", "Source", "Message", "Custom Penalty"};

    private static final Locale LOCALE = DynamicBundle.getLocale();

    private final List<Annotation> annotations = new ArrayList<>();

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
    public Object getValueAt(int row, int column) {
        Annotation annotation = annotations.get(row);

        if (annotation == null) {
            return "";
        }

        return switch (column) {
            case 0 -> annotation.getMistakeType().getButtonText().translateTo(LOCALE);
            case 1 -> formatLines(annotation);
            case 2 -> annotation.getFilePath();
            case 3 -> annotation.getSource();
            case 4 -> annotation.getCustomMessage().orElse("");
            case 5 -> annotation.getCustomScore().map(String::valueOf).orElse("");
            default -> throw new IllegalStateException("No table data at index %d:%d".formatted(row, column));
        };
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations.clear();
        this.annotations.addAll(annotations);
    }

    public void clearAnnotations() {
        this.annotations.clear();
    }

    public Annotation get(int index) {
        return annotations.get(index);
    }

    private String formatLines(Annotation annotation) {
        int startLine = annotation.getStartLine() + 1;
        int endLine = annotation.getEndLine() + 1;

        if (startLine == endLine) {
            return String.valueOf(startLine);
        } else {
            return String.format("%d - %d", startLine, endLine);
        }
    }
}
