/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis.table;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;

import com.intellij.ui.treeStructure.treetable.ListTreeTableModel;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;

/**
 * The table model for the annotations table.
 */
public class AnnotationsTableModel extends ListTreeTableModel {
    private final List<Annotation> annotations = new ArrayList<>();
    private final AnnotationsTreeNode root;

    private static MutableTreeNode createNode(List<Annotation> annotations) {
        if (annotations.size() == 1) {
            return new AnnotationsTreeNode.AnnotationNode(annotations.getFirst());
        }

        var result = new AnnotationsTreeNode.GroupNode();
        for (var annotation : annotations) {
            result.add(new AnnotationsTreeNode.AnnotationNode(annotation));
        }

        return result;
    }

    // This function is quite similar to the one in artemis4j, but I don't think it is worth using a shared function.
    // There might be some differences in the grouping logic between what is grouped for the tutor and what is grouped
    // for the student.

    // The annotations are grouped by their classifiers
    private List<MutableTreeNode> groupAnnotations(List<Annotation> annotations) {
        // first group all problems by the first classifier:
        Map<String, List<Annotation>> groupedAnnotations = annotations.stream()
                .collect(Collectors.groupingBy(
                        annotation ->
                                annotation.getClassifiers().stream().findFirst().orElse(annotation.getUUID()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<MutableTreeNode> result = new ArrayList<>();
        for (List<Annotation> annotationsForClassifier : groupedAnnotations.values()) {
            if (annotationsForClassifier.size() <= 1) {
                for (Annotation annotation : annotationsForClassifier) {
                    result.add(createNode(List.of(annotation)));
                }
                continue;
            }

            // Further partition the annotations by their remaining classifiers:
            Map<String, List<Annotation>> map = annotationsForClassifier.stream()
                    .collect(Collectors.groupingBy(
                            annotation -> {
                                List<String> classifiers = annotation.getClassifiers();
                                if (classifiers.size() <= 1) {
                                    return annotation.getUUID();
                                } else {
                                    // to simplify the grouping code, we merge the remaining classifiers
                                    // into a single string
                                    return String.join(" ", classifiers.subList(1, classifiers.size()));
                                }
                            },
                            LinkedHashMap::new,
                            Collectors.toList()));

            for (List<Annotation> list : map.values()) {
                result.add(createNode(list));
            }
        }

        return result;
    }

    // Don't ask, I couldn't think of a better solution.
    private static AnnotationsTreeNode tempRootNode = null;

    private static AnnotationsTreeNode storeRootNode(AnnotationsTreeNode node) {
        tempRootNode = node;
        return node;
    }

    public AnnotationsTableModel() {
        super(storeRootNode(new AnnotationsTreeNode.RootNode()), AnnotationsTreeNode.columns());
        this.root = tempRootNode;
    }

    private void refreshNodes() {
        this.root.removeAllChildren();

        for (var child : groupAnnotations(this.annotations)) {
            this.root.add(child);
        }

        this.sort(AnnotationsTreeTable.DEFAULT_NODE_COMPARATOR);
    }

    public void sort(Comparator<? super AnnotationsTreeNode> comparator) {
        // The root node and all its children are sorted here.
        // An alternative approach would be to sort the list of annotations
        // and then rebuild the tree, but this would destroy the tree structure
        // and invalidate all the TreePaths.

        this.root.sort(comparator);
        this.reload();
    }

    @Override
    public int getColumnCount() {
        return AnnotationsTreeNode.numberOfColumns();
    }

    @Override
    public String getColumnName(int column) {
        return AnnotationsTreeNode.columnName(column);
    }

    @Override
    public Object getValueAt(Object node, int column) {
        return ((AnnotationsTreeNode) node).getValueAt(column);
    }

    public void setAnnotations(List<Annotation> annotations) {
        // the annotations can change in the following ways:
        // - annotations are added
        // - annotations are removed
        // - annotations are changed (e.g. the custom message is changed)
        ///  ^ for these the identity of the annotation is unchanged -> we don't have to change anything

        // Why all this hassle? Otherwise, the currently expanded node would collapse, which is annoying.
        List<Annotation> oldAnnotations = new ArrayList<>(this.annotations);
        List<Annotation> newAnnotations = new ArrayList<>(annotations);

        List<Annotation> addedAnnotations = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (!oldAnnotations.contains(annotation)) {
                addedAnnotations.add(annotation);
            }
        }

        List<Annotation> removedAnnotations = new ArrayList<>();
        for (Annotation annotation : this.annotations) {
            if (!newAnnotations.contains(annotation)) {
                removedAnnotations.add(annotation);
            }
        }

        boolean wasEmpty = this.annotations.isEmpty();

        this.annotations.clear();
        this.annotations.addAll(annotations);

        if (wasEmpty) {
            // this will build the tree from scratch:
            this.refreshNodes();
        } else {
            this.removeAnnotations(removedAnnotations);
            this.addAnnotations(addedAnnotations);
            this.reload();
        }
    }

    private void removeAnnotations(List<Annotation> annotations) {
        this.root.removeIf(annotations::contains);
    }

    private void addAnnotations(List<Annotation> annotations) {
        for (var annotation : annotations) {
            this.root.add(createNode(List.of(annotation)));
        }
    }

    public TreePath getTreePathFor(Predicate<AnnotationsTreeNode> isMatching) {
        Queue<AnnotationsTreeNode> queue = new LinkedList<>(List.of(this.root));

        while (!queue.isEmpty()) {
            var node = queue.poll();

            if (node instanceof AnnotationsTreeNode annotationNode && isMatching.test(annotationNode)) {
                return new TreePath(node.getPath());
            }

            queue.addAll(node.listChildren());
        }

        throw new IllegalStateException("Annotation not found");
    }

    public TreePath getTreePathFor(Annotation annotation) {
        return getTreePathFor(node -> node instanceof AnnotationsTreeNode.AnnotationNode annotationNode
                && annotationNode.getAnnotation() == annotation);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return AnnotationsTreeNode.columnClass(columnIndex);
    }
}
