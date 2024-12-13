/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.table.AbstractTableModel;

import com.intellij.DynamicBundle;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import org.jetbrains.annotations.NotNull;

/**
 * The table model for the annotations table.
 */
public class AnnotationsTableModel extends AbstractTableModel {

    public static final int MISTAKE_TYPE_COLUMN = 0;
    public static final int LINES_COLUMN = 1;
    public static final int FILE_COLUMN = 2;
    public static final int SOURCE_COLUMN = 3;
    public static final int CUSTOM_MESSAGE_COLUMN = 4;
    public static final int CUSTOM_PENALTY_COLUMN = 5;

    private static final String[] HEADINGS = {
        "Mistake type", "Line(s)", "File", "Source", "Custom Message", "Custom Penalty"
    };

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
            case MISTAKE_TYPE_COLUMN -> annotation
                    .getMistakeType()
                    .getButtonText()
                    .translateTo(LOCALE);
            case LINES_COLUMN -> LineLocation.fromAnnotation(annotation);
            case FILE_COLUMN -> annotation
                    .getFilePath()
                    // for display purposes, replace backslashes with forward slashes
                    .replace("\\", "/");
            case SOURCE_COLUMN -> annotation.getSource();
            case CUSTOM_MESSAGE_COLUMN -> annotation.getCustomMessage().orElse("");
            case CUSTOM_PENALTY_COLUMN -> annotation
                    .getCustomScore()
                    .map(String::valueOf)
                    .orElse("");
            default -> throw new IllegalStateException("No table data at index %d:%d".formatted(row, column));
        };
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations.clear();
        this.annotations.addAll(annotations);
        fireTableDataChanged();
    }

    public void clearAnnotations() {
        this.annotations.clear();
        fireTableDataChanged();
    }

    public Annotation get(int index) {
        return annotations.get(index);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        // This must be overridden, otherwise LineLocation#compareTo will not be called.
        if (columnIndex == LINES_COLUMN) {
            return LineLocation.class;
        } else {
            return Object.class;
        }
    }

    public record LineLocation(int startLine, int endLine) implements Comparable<LineLocation> {
        public static LineLocation fromAnnotation(Annotation annotation) {
            return new LineLocation(annotation.getStartLine() + 1, annotation.getEndLine() + 1);
        }

        @Override
        public String toString() {
            return startLine == endLine ? "%d".formatted(startLine) : "%d - %s".formatted(startLine, endLine);
        }

        @Override
        public int compareTo(@NotNull LineLocation other) {
            // It is not necessary to override equals and hashCode, because
            // this is already done by the record keyword.
            if (this.equals(other)) {
                return 0;
            }

            if (startLine != other.startLine) {
                return Integer.compare(startLine, other.startLine);
            }

            return Integer.compare(endLine, other.endLine);
        }
    }
}
