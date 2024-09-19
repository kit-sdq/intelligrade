/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.extensions.guis;

import java.awt.EventQueue;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.state.PluginState;
import edu.kit.kastel.utils.EditorUtil;

public class AnnotationsListPanel extends SimpleToolWindowPanel {
    private final List<Annotation> displayAnnotations = new ArrayList<>();
    private final AnnotationsTableModel model;
    private final JBTable table;

    public static AnnotationsListPanel getPanel() {
        var toolWindow =
                ToolWindowManager.getInstance(EditorUtil.getActiveProject()).getToolWindow("Annotations");
        return (AnnotationsListPanel)
                toolWindow.getContentManager().getContent(0).getComponent();
    }

    public AnnotationsListPanel() {
        super(true, true);

        model = new AnnotationsTableModel();
        table = new JBTable(model);
        setContent(ScrollPaneFactory.createScrollPane(table));

        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    // First collect all annotations to delete, then delete them
                    // If delete them one by one, the row indices change and the wrong annotations are deleted
                    var annotationsToDelete = Arrays.stream(table.getSelectedRows())
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

        table.setComponentPopupMenu(getPopupMenu());

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());

                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && row >= 0) {
                    var annotation = model.get(row);
                    var file = EditorUtil.getAnnotationFile(annotation);
                    var document = FileDocumentManager.getInstance().getDocument(file);
                    int offset = document.getLineStartOffset(annotation.getStartLine());
                    FileEditorManager.getInstance(EditorUtil.getActiveProject())
                            .openTextEditor(new OpenFileDescriptor(EditorUtil.getActiveProject(), file, offset), true);
                }
            }
        });

        PluginState.getInstance()
                .registerAssessmentStartedListener(
                        assessment -> assessment.registerAnnotationsUpdatedListener(annotations -> {
                            this.displayAnnotations.clear();
                            this.displayAnnotations.addAll(annotations);
                            this.displayAnnotations.sort(Comparator.comparing(Annotation::getFilePath)
                                    .thenComparing(Annotation::getStartLine)
                                    .thenComparing(Annotation::getEndLine));

                            AnnotationsTableModel model = ((AnnotationsTableModel) table.getModel());
                            model.setAnnotations(displayAnnotations);
                            table.updateUI();
                        }));

        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            this.displayAnnotations.clear();
            AnnotationsTableModel model = ((AnnotationsTableModel) table.getModel());
            model.clearAnnotations();
            table.updateUI();
        });
    }

    public void selectAnnotation(Annotation annotation) {
        var index = displayAnnotations.indexOf(annotation);
        if (index >= 0) {
            table.setRowSelectionInterval(index, index);
        }
    }

    private JBPopupMenu getPopupMenu() {
        var popupMenu = new JBPopupMenu();

        var editCustomText = new JBMenuItem("Edit custom message/score");
        editCustomText.setEnabled(false);
        editCustomText.addActionListener(e -> {
            var row = table.getSelectedRow();
            if (row >= 0) {
                PluginState.getInstance().getActiveAssessment().orElseThrow().changeCustomMessage(model.get(row));
            }
        });
        popupMenu.add(editCustomText);

        // Select the row under the cursor when opening the popup menu, and enable/disable the popup menu items
        popupMenu.addPopupMenuListener(new PopupMenuListenerAdapter() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                int row = table.rowAtPoint(((MouseEvent) EventQueue.getCurrentEvent()).getPoint());
                if (row >= 0) {
                    editCustomText.setEnabled(true);
                    table.setRowSelectionInterval(row, row);
                } else {
                    editCustomText.setEnabled(false);
                }
            }
        });
        return popupMenu;
    }
}
