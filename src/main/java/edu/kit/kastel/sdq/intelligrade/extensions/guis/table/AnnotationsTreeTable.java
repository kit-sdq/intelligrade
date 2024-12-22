/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis.table;

import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

import javax.swing.JTable;
import javax.swing.table.TableRowSorter;
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

    private final AnnotationsTableModel model;

    public AnnotationsTreeTable(AnnotationsTableModel model) {
        super(model);
        this.model = model;
        this.installListeners();

        getTableHeader().setReorderingAllowed(false);

        // TODO: make sorting work for treetables
        setRowSorter(new TableRowSorter<>(getModel()) {});
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
