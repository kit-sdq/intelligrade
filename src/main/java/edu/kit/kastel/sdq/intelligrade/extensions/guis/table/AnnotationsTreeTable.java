/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis.table;

import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SortOrder;
import javax.swing.UIManager;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.TreePath;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableCellRenderer;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.UIUtil;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import org.jetbrains.annotations.NotNull;

public class AnnotationsTreeTable extends TreeTable {
    private static final Logger LOG = Logger.getInstance(AnnotationsTreeTable.class);
    public static final Comparator<AnnotationsTreeNode> DEFAULT_NODE_COMPARATOR = delegatingColumnComparator(
                    AnnotationsTreeNode.FILE_COLUMN)
            .thenComparing(delegatingColumnComparator(AnnotationsTreeNode.LINES_COLUMN));

    private final AnnotationsTableModel model;
    private final Map<Integer, SortOrder> columnSortOrder = new HashMap<>();

    private static Comparator<AnnotationsTreeNode> getColumnComparator(int columnIdx, SortOrder sortOrder) {
        var comparator = delegatingColumnComparator(columnIdx);

        return switch (sortOrder) {
            case DESCENDING -> comparator;
            case ASCENDING -> comparator.reversed();
            case UNSORTED -> DEFAULT_NODE_COMPARATOR;
        };
    }

    private static SortOrder nextSortOrder(SortOrder current) {
        var order = List.of(SortOrder.DESCENDING, SortOrder.ASCENDING, SortOrder.UNSORTED);
        return order.get((order.indexOf(current) + 1) % order.size());
    }

    public AnnotationsTreeTable(AnnotationsTableModel model) {
        super(model);
        this.model = model;
        this.installListeners();

        getTableHeader().setReorderingAllowed(false);

        // The original row sorter is disabled, because it does not work with tree tables.
        //
        // This code hooks into the table header rendering and adds icons for the sort order,
        // depending on the above columnSortOrder map.
        setRowSorter(null);
        TableCellRenderer renderer = getTableHeader().getDefaultRenderer();
        getTableHeader().setDefaultRenderer((table, value, isSelected, hasFocus, row, column) -> {
            Component delegate =
                    renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!(delegate instanceof JLabel label)) {
                return delegate;
            }

            SortOrder sortOrder = columnSortOrder.computeIfAbsent(column, key -> SortOrder.UNSORTED);
            label.setIcon(
                    switch (sortOrder) {
                        case ASCENDING -> UIManager.getIcon("Table.ascendingSortIcon");
                        case DESCENDING -> UIManager.getIcon("Table.descendingSortIcon");
                        case UNSORTED -> UIManager.getIcon("Table.naturalSortIcon");
                    });

            return label;
        });

        // Register a mouse listener that is called when the user clicks on a column in the table header.
        getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                JTableHeader header = (JTableHeader) (e.getSource());
                JTable tableView = header.getTable();
                TableColumnModel columnModel = tableView.getColumnModel();
                int column = columnModel.getColumnIndexAtX(e.getX());

                // save the currently expanded paths (so they can be expanded again after sorting)
                Set<TreePath> expandedPaths = new HashSet<>(getTree().getExpandedPaths());

                // figure out the new sort order for the column:
                var sortOrder = nextSortOrder(columnSortOrder.computeIfAbsent(column, key -> SortOrder.UNSORTED));
                var columnComparator = getColumnComparator(column, sortOrder);

                // set the sort order for all columns to UNSORTED, except for the clicked column:
                for (int i = 0; i < header.getColumnModel().getColumnCount(); i++) {
                    columnSortOrder.put(i, i == column ? sortOrder : SortOrder.UNSORTED);
                }

                // this is necessary to make the updated icon visible:
                header.repaint();

                model.sort(columnComparator);

                revalidate();
                updateUI();

                getTree().expandPaths(expandedPaths);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Comparator<AnnotationsTreeNode> delegatingColumnComparator(int columnIdx) {
        if (AnnotationsTreeNode.columnClass(columnIdx) == Object.class) {
            // by default compare the strings of the column values:
            return Comparator.comparing(
                    (AnnotationsTreeNode node) -> node.getValueAt(columnIdx).toString());
        }

        // if the class is not Object, this `Comparator` is used that will
        // delegate to the `compareTo` method of the column values:
        return (left, right) -> {
            // The Objects.compare takes care of null values
            return Objects.compare(left.getValueAt(columnIdx), right.getValueAt(columnIdx), (a, b) -> {
                if (a instanceof Comparable<?> comparable) {
                    // the code should be able to assume that a column value implements Comparable,
                    // so that it can be compared with other values of the same column.
                    //
                    // If this is not the case, the code will crash here, indicating an implementation error.
                    return ((Comparable<Object>) comparable).compareTo(b);
                } else {
                    return a.toString().compareTo(b.toString());
                }
            });
        };
    }

    private void installListeners() {
        installDoubleClickListener();
        installKeyboardListener();
    }

    private void installDoubleClickListener() {
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                TreePath path = getTree().getLeadSelectionPath();
                if (path == null) {
                    return true;
                }

                var node = (AnnotationsTreeNode) path.getLastPathComponent();

                if (!(node instanceof AnnotationsTreeNode.AnnotationNode annotationNode)) {
                    return true;
                }

                int column = columnAtPoint(event.getPoint());

                Annotation annotation = annotationNode.getAnnotation();
                if (convertColumnIndexToModel(column) == AnnotationsTreeNode.CUSTOM_MESSAGE_COLUMN) {
                    // Edit the custom message
                    editCustomMessageOfSelection();
                } else {
                    // Jump to the line in the editor
                    var file = IntellijUtil.getAnnotationFile(annotation);
                    var document = FileDocumentManager.getInstance().getDocument(file);
                    int offset = document.getLineStartOffset(annotation.getStartLine());
                    FileEditorManager.getInstance(IntellijUtil.getActiveProject())
                            .openTextEditor(
                                    new OpenFileDescriptor(IntellijUtil.getActiveProject(), file, offset), true);
                }

                return true;
            }
        }.installOn(this);
    }

    private void installKeyboardListener() {
        // Delete annotation on delete key press
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() != KeyEvent.VK_DELETE) {
                    return;
                }

                deleteSelection();
            }
        });
    }

    private static String debugPath(TreePath path) {
        return Arrays.stream(path.getPath())
                .map(object -> "%s(%s)".formatted(object, System.identityHashCode(object)))
                .reduce((a, b) -> a + " -> " + b)
                .orElse("Empty path");
    }

    public void selectAnnotation(Annotation annotation) {
        clearSelection();
        getTree().clearSelection();
        TreePath path = model.getTreePathFor(annotation);
        LOG.debug("Selecting annotation: " + annotation + " at path: " + debugPath(path));

        addSelectedPath(path);

        scrollPathToVisible(path);
    }

    /**
     * Scrolls the tree to make the path visible.
     *
     * @param path the path to make visible
     */
    public void scrollPathToVisible(TreePath path) {
        // There exists a getTree().scrollPathToVisible(path) method, but it does not work.
        if (!getTree().isExpanded(path)) {
            getTree().expandPath(path);
        }

        scrollRectToVisible(getTree().getPathBounds(path));
    }

    public void editCustomMessageOfSelection() {
        TreePath path = getTree().getLeadSelectionPath();
        if (path == null) {
            return;
        }

        if (!(path.getLastPathComponent() instanceof AnnotationsTreeNode.AnnotationNode annotationNode)) {
            return;
        }

        // Edit the custom message
        PluginState.getInstance()
                .getActiveAssessment()
                .orElseThrow()
                .changeCustomMessage(annotationNode.getAnnotation());
    }

    public void deleteSelection() {
        TreePath[] paths = getTree().getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return;
        }

        // First collect all annotations to delete, then delete them
        // If delete them one by one, the row indices change and the wrong annotations are deleted
        var annotationsToDelete = Arrays.stream(paths)
                .map(TreePath::getLastPathComponent)
                .map(AnnotationsTreeNode.class::cast)
                .map(AnnotationsTreeNode::listAnnotations)
                .flatMap(List::stream)
                .toList();

        LOG.debug("Deleting annotations: " + annotationsToDelete);
        var assessment = PluginState.getInstance().getActiveAssessment().orElseThrow();
        for (var annotation : annotationsToDelete) {
            assessment.deleteAnnotation(annotation);
        }
    }

    @Override
    public TreeTableCellRenderer createTableRenderer(TreeTableModel treeTableModel) {
        TreeTableCellRenderer tableRenderer = new TreeTableCellRenderer(this, this.getTree()) {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                try {
                    return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                } catch (NullPointerException exception) {
                    // There is a bug somewhere in IntelliJ that causes a NPE when an element is removed from the
                    // tree table.
                    //
                    // I spent at least 15 hours debugging, trying out different things, and searching the internet.
                    // The bug is still there, and I do not want to spend more time on this.
                    //
                    // https://youtrack.jetbrains.com/issue/IJPL-106934/Branches-popup-NPE-on-scrolling-branches-list
                    //
                    // The code below seems to fix this issue.
                    var component = new SimpleColoredComponent();
                    var background = UIUtil.getTreeBackground();
                    UIUtil.changeBackGround(component, background);
                    var foreground = isSelected ? UIUtil.getTreeSelectionForeground(true) : UIUtil.getTreeForeground();

                    component.setForeground(foreground);

                    return component;
                }
            }
        };
        tableRenderer.setRootVisible(false);
        tableRenderer.setShowsRootHandles(true);

        return tableRenderer;
    }
}
