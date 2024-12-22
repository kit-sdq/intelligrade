/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.tree.DefaultMutableTreeNode;

import com.intellij.DynamicBundle;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.ColumnInfo;
import edu.kit.kastel.sdq.artemis4j.client.AnnotationSource;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import org.jetbrains.annotations.NotNull;

public abstract class AnnotationsTreeNode extends DefaultMutableTreeNode {
    private static final Locale LOCALE = DynamicBundle.getLocale();

    public static final int MISTAKE_TYPE_COLUMN = 0;
    public static final int LINES_COLUMN = 1;
    public static final int FILE_COLUMN = 2;
    public static final int SOURCE_COLUMN = 3;
    public static final int CUSTOM_MESSAGE_COLUMN = 4;
    public static final int CUSTOM_PENALTY_COLUMN = 5;

    private static final ColumnInfo[] COLUMN_INFOS = {
        new DefaultColumnInfo("Mistake type", String.class),
        new DefaultColumnInfo("Line(s)", Lines.class),
        new DefaultColumnInfo("File", String.class),
        new DefaultColumnInfo("Source", AnnotationSource.class),
        new DefaultColumnInfo("Custom Message", String.class),
        new DefaultColumnInfo("Custom Penalty", String.class)
    };

    protected AnnotationsTreeNode(boolean allowsChildren) {
        super(allowsChildren);
    }

    public static ColumnInfo[] columns() {
        return COLUMN_INFOS;
    }

    public static int numberOfColumns() {
        return columns().length;
    }

    public static String columnName(int column) {
        return columns()[column].getName();
    }

    public static Class<?> columnClass(int columnIndex) {
        // Note: If this line is missing, it will not work correctly.
        if (columnIndex == 0) {
            // one could return a different class here, I tested returning this class,
            // but the rendering was broken.
            return TreeTableModel.class;
        }

        return columns()[columnIndex].getColumnClass();
    }

    private static Object getValueOfAt(Annotation annotation, int column) {
        if (annotation == null) {
            return "";
        }

        return switch (column) {
            case MISTAKE_TYPE_COLUMN -> annotation
                    .getMistakeType()
                    .getButtonText()
                    .translateTo(LOCALE);
            case LINES_COLUMN -> Lines.fromAnnotation(annotation);
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
            default -> throw new IllegalStateException("No table data at column %d".formatted(column));
        };
    }

    /**
     * Creates a list over the children of this node.
     *
     * @return a {@link List} over the children of this node
     */
    public List<AnnotationsTreeNode> listChildren() {
        return Collections.list(children()).stream()
                .map(AnnotationsTreeNode.class::cast)
                .toList();
    }

    public abstract Object getValueAt(int column);

    public abstract List<Annotation> listAnnotations();

    public abstract boolean removeIf(Predicate<Annotation> shouldRemove);

    public static class AnnotationNode extends AnnotationsTreeNode {
        private final @NotNull Annotation annotation;

        public AnnotationNode(@NotNull Annotation annotation) {
            super(false);

            this.annotation = annotation;
        }

        public @NotNull Annotation getAnnotation() {
            return annotation;
        }

        @Override
        public Object getValueAt(int column) {
            return getValueOfAt(annotation, column);
        }

        @Override
        public final boolean equals(Object object) {
            if (!(object instanceof AnnotationNode annotationNode)) {
                return false;
            }

            return Objects.equals(annotation, annotationNode.annotation);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(annotation);
        }

        @Override
        public List<Annotation> listAnnotations() {
            return List.of(annotation);
        }

        @Override
        public boolean removeIf(Predicate<Annotation> shouldRemove) {
            // the annotation node can not remove itself, and it does not have children
            return false;
        }

        @Override
        public String toString() {
            return this.getValueAt(MISTAKE_TYPE_COLUMN).toString();
        }
    }

    public static class GroupNode extends AnnotationsTreeNode {
        public GroupNode() {
            super(true);
        }

        @Override
        public Object getValueAt(int column) {
            // For the group node, we need to display something in the row.
            //
            // This function first collects all the values of the children at the given column, and then
            // merges them into a single object. How they are merged depends on the column type.
            Set<Object> data = listAnnotations().stream()
                    .map(annotation -> getValueOfAt(annotation, column))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (data.size() == 1) {
                return data.iterator().next();
            }

            if (columnClass(column) == String.class) {
                return data.stream().map(element -> (String) element).collect(Collectors.joining(", "));
            } else if (columnClass(column) == Lines.class) {
                return Lines.fromLines(
                        data.stream().map(element -> (Lines) element).toList());
            } else {
                // fallback to prevent crash (in case the group node becomes empty and intellij tries to render it)
                if (data.isEmpty()) {
                    return "";
                }

                // if the column is an unhandled type, just return the first element
                return data.iterator().next();
            }
        }

        @Override
        public final boolean equals(Object object) {
            if (!(object instanceof GroupNode groupNode)) {
                return false;
            }

            return Objects.equals(listAnnotations(), groupNode.listAnnotations());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(listAnnotations());
        }

        @Override
        public List<Annotation> listAnnotations() {
            List<Annotation> result = new ArrayList<>();
            for (var child : listChildren()) {
                result.addAll(child.listAnnotations());
            }

            return result;
        }

        @Override
        public boolean removeIf(Predicate<Annotation> shouldRemove) {
            List<Integer> indices = new ArrayList<>();
            var children = listChildren();

            boolean hasAnyChanged = false;
            for (int i = 0; i < children.size(); i++) {
                var child = children.get(i);
                boolean hasChanged = child.removeIf(shouldRemove);

                if (hasChanged) {
                    hasAnyChanged = true;
                }

                if (hasChanged && child.isLeaf()) {
                    indices.add(i);
                    continue;
                }

                // mark the annotation for removal if it contains one of the annotations
                if (child instanceof AnnotationNode annotationNode
                        && shouldRemove.test(annotationNode.getAnnotation())) {
                    indices.add(i);
                }
            }

            for (int i = indices.size() - 1; i >= 0; i--) {
                remove(indices.get(i));
            }

            return hasAnyChanged || !indices.isEmpty();
        }

        @Override
        public String toString() {
            return this.getValueAt(MISTAKE_TYPE_COLUMN).toString();
        }
    }

    public static class RootNode extends AnnotationsTreeNode {
        public RootNode() {
            super(true);
        }

        @Override
        public Object getValueAt(int column) {
            return null;
        }

        @Override
        public List<Annotation> listAnnotations() {
            List<Annotation> result = new ArrayList<>();
            for (var child : listChildren()) {
                result.addAll(child.listAnnotations());
            }

            return result;
        }

        @Override
        public boolean removeIf(Predicate<Annotation> shouldRemove) {
            boolean hasAnyChanged = false;
            for (var child : listChildren()) {
                boolean hasChanged = child.removeIf(shouldRemove);
                if (hasChanged) {
                    hasAnyChanged = true;
                }

                // if a group has only one child, replace the group with the child
                if (child.listChildren().size() == 1) {
                    var onlyChild = child.listChildren().getFirst();
                    remove(child);
                    add(onlyChild);
                    hasAnyChanged = true;
                    continue;
                }

                // remove empty children (e.g. groups which have no annotations)
                //
                // Note: if annotations are removed one by one, a group will never be empty,
                //       because of the above code, but if multiple annotations are removed at once,
                //       a group could become empty.
                if (child.listChildren().isEmpty()) {
                    remove(child);
                    hasAnyChanged = true;
                }
            }

            return hasAnyChanged;
        }

        @Override
        public String toString() {
            return "Root";
        }
    }
}
