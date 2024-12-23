/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.tree.TreePath;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.table.AnnotationsTableModel;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.table.AnnotationsTreeTable;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import org.jetbrains.annotations.NotNull;

public class AnnotationsListPanel extends SimpleToolWindowPanel {
    private final AnnotationsTableModel model;
    private final AnnotationsTreeTable table;

    public static AnnotationsListPanel getPanel() {
        var toolWindow =
                ToolWindowManager.getInstance(IntellijUtil.getActiveProject()).getToolWindow("Annotations");
        return (AnnotationsListPanel)
                toolWindow.getContentManager().getContent(0).getComponent();
    }

    public AnnotationsListPanel() {
        super(true, true);

        model = new AnnotationsTableModel();
        table = new AnnotationsTreeTable(model);

        setContent(ScrollPaneFactory.createScrollPane(table));

        // Add the right-click menu
        addPopupMenu();

        PluginState.getInstance()
                .registerAssessmentStartedListener(
                        assessment -> assessment.registerAnnotationsUpdatedListener(annotations -> {
                            // save the currently expanded paths (so they stay open after the annotations change)
                            Set<TreePath> expandedPaths =
                                    new HashSet<>(table.getTree().getExpandedPaths());
                            model.setAnnotations(annotations);

                            table.revalidate();
                            table.updateUI();

                            // restore the expanded paths
                            table.getTree().expandPaths(expandedPaths);
                        }));

        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            model.setAnnotations(List.of());
            table.updateUI();
        });
    }

    public void selectAnnotation(Annotation annotation) {
        this.table.selectAnnotation(annotation);
    }

    private void addPopupMenu() {
        var group = new DefaultActionGroup();

        var editButton = new AnActionButton("Edit Custom Message/Score") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                table.editCustomMessageOfSelection();
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
                table.deleteSelection();
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
