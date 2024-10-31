/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.SwingUtilities;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import org.jetbrains.annotations.NotNull;

public class AnnotationsListPanel extends SimpleToolWindowPanel {
    private final List<Annotation> displayAnnotations = new ArrayList<>();
    private final AnnotationsTableModel model;
    private final JBTable table;

    public static AnnotationsListPanel getPanel() {
        var toolWindow =
                ToolWindowManager.getInstance(IntellijUtil.getActiveProject()).getToolWindow("Annotations");
        return (AnnotationsListPanel)
                toolWindow.getContentManager().getContent(0).getComponent();
    }

    public AnnotationsListPanel() {
        super(true, true);

        model = new AnnotationsTableModel();

        table = new JBTable(model);
        table.setAutoCreateRowSorter(true);

        setContent(ScrollPaneFactory.createScrollPane(table));

        // Delete annotation on delete key press
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    // First collect all annotations to delete, then delete them
                    // If delete them one by one, the row indices change and the wrong annotations are deleted
                    var annotationsToDelete = Arrays.stream(table.getSelectedRows())
                            .map(table::convertRowIndexToModel)
                            .filter(row -> row >= 0)
                            .mapToObj(model::get)
                            .toList();

                    var assessment =
                            PluginState.getInstance().getActiveAssessment().orElseThrow();
                    for (var annotation : annotationsToDelete) {
                        assessment.deleteAnnotation(annotation);
                    }
                }
            }
        });

        // Double-clicks on the table
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());

                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && row >= 0) {
                    var annotation = model.get(table.convertRowIndexToModel(row));

                    if (table.convertColumnIndexToModel(column) == AnnotationsTableModel.CUSTOM_MESSAGE_COLUMN) {
                        // Edit the custom message
                        PluginState.getInstance()
                                .getActiveAssessment()
                                .orElseThrow()
                                .changeCustomMessage(annotation);
                    } else {
                        // Jump to the line in the editor
                        var file = IntellijUtil.getAnnotationFile(annotation);
                        var document = FileDocumentManager.getInstance().getDocument(file);
                        int offset = document.getLineStartOffset(annotation.getStartLine());
                        FileEditorManager.getInstance(IntellijUtil.getActiveProject())
                                .openTextEditor(
                                        new OpenFileDescriptor(IntellijUtil.getActiveProject(), file, offset), true);
                    }
                }
            }
        });

        // Add the right-click menu
        addPopupMenu();

        PluginState.getInstance()
                .registerAssessmentStartedListener(
                        assessment -> assessment.registerAnnotationsUpdatedListener(annotations -> {
                            this.displayAnnotations.clear();
                            this.displayAnnotations.addAll(annotations);
                            this.displayAnnotations.sort(Comparator.comparing(Annotation::getFilePath)
                                    .thenComparing(Annotation::getStartLine)
                                    .thenComparing(Annotation::getEndLine));

                            AnnotationsTableModel tableModel = ((AnnotationsTableModel) table.getModel());
                            tableModel.setAnnotations(displayAnnotations);
                            table.revalidate();
                            table.updateUI();
                        }));

        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            this.displayAnnotations.clear();
            AnnotationsTableModel tableModel = ((AnnotationsTableModel) table.getModel());
            tableModel.clearAnnotations();
            table.updateUI();
        });
    }

    public void selectAnnotation(Annotation annotation) {
        var row = displayAnnotations.indexOf(annotation);
        if (row >= 0) {
            int viewRow = table.convertRowIndexToView(row);
            table.setRowSelectionInterval(viewRow, viewRow);
            table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
        }
    }

    private void addPopupMenu() {
        var group = new DefaultActionGroup();

        var editButton = new AnActionButton("Edit Custom Message/Score") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                var row = table.convertRowIndexToModel(table.getSelectedRow());
                if (row >= 0) {
                    PluginState.getInstance()
                            .getActiveAssessment()
                            .orElseThrow()
                            .changeCustomMessage(model.get(row));
                }
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
        group.addAction(editButton);

        var deleteButton = new AnActionButton("Delete") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                var row = table.convertRowIndexToModel(table.getSelectedRow());
                if (row >= 0) {
                    PluginState.getInstance()
                            .getActiveAssessment()
                            .orElseThrow()
                            .deleteAnnotation(model.get(row));
                }
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
        group.addAction(deleteButton);

        PopupHandler.installPopupMenu(table, group, "popup@AnnotationsListPanel");
    }
}
