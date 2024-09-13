package edu.kit.kastel.extensions.guis;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import edu.kit.kastel.state.PluginState;
import edu.kit.kastel.utils.EditorUtil;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

public class AnnotationsListPanel extends SimpleToolWindowPanel {
    private JBTable table;
    private AnnotationsTableModel model;

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

                    var assessment = PluginState.getInstance().getActiveAssessment().orElseThrow();
                    for (var annotation : annotationsToDelete) {
                        assessment.deleteAnnotation(annotation);
                    }
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0) {
                    var annotation = model.get(row);

                    var file = EditorUtil.getAnnotationFile(annotation);
                    var document = FileDocumentManager.getInstance().getDocument(file);
                    int offset = document.getLineStartOffset(annotation.getStartLine());
                    FileEditorManager.getInstance(EditorUtil.getActiveProject()).openTextEditor(
                            new OpenFileDescriptor(EditorUtil.getActiveProject(), file, offset),
                            true);
                }
            }
        });

        PluginState.getInstance()
                .registerAssessmentStartedListener(
                        assessment -> assessment.registerAnnotationsUpdatedListener(annotations -> {
                            AnnotationsTableModel model = ((AnnotationsTableModel) table.getModel());
                            model.setAnnotations(annotations);
                            table.updateUI();
                        }));

        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            AnnotationsTableModel model = ((AnnotationsTableModel) table.getModel());
            model.clearAnnotations();
            table.updateUI();
        });
    }
}
