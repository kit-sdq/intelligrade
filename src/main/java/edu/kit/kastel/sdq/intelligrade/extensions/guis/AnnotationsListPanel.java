/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.tree.TreePath;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.table.AnnotationsTableModel;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.table.AnnotationsTreeTable;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;
import net.miginfocom.swing.MigLayout;
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

        var restoreButton = new AnActionButton("Restore") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                table.restoreSelection();
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
        group.addAction(restoreButton);
        PluginState.getInstance()
                .registerAssessmentStartedListener(assessment -> restoreButton.setEnabled(assessment.isReview()));

        // Adds a debug button to the right-click menu in the table.
        //
        // There is some data regarding the annotations that is not visible in the table,
        // like what exact location the annotation refers to or which problem type in the autograder
        // emitted the annotation.
        var debugButton = new AnActionButton("Debug Information") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                var annotations = table.getSelectedAnnotations();
                if (annotations.isEmpty()) {
                    return;
                }

                // we only emit information about the first selected annotation
                var annotation = annotations.getFirst();
                showDebugDialog(annotation);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
        group.addAction(debugButton);

        PopupHandler.installPopupMenu(table, group, "popup@AnnotationsListPanel");
    }

    private void showDebugDialog(Annotation annotation) {
        var panel = new JBPanel<>(new MigLayout("wrap 2", "[] [grow]"));

        var location = annotation.getLocation();

        // This is a list instead of a map, so that the order of the entries is stable.
        var data = List.of(
                Map.entry("UUID", annotation.getUUID()),
                Map.entry("MistakeType", annotation.getMistakeType().getId()),
                Map.entry(
                        "RatingGroup",
                        annotation.getMistakeType().getRatingGroup().getId()),
                Map.entry("Path", location.filePath()),
                Map.entry("Start", location.start().toString()),
                Map.entry("End", location.end().toString()),
                Map.entry(
                        "Created By",
                        annotation.getCreatorId().map(Object::toString).orElse("?")),
                Map.entry("Suppressed", annotation.isSuppressed() ? "Yes" : "No"),
                Map.entry(
                        "Suppressed By",
                        annotation.getSuppressorId().map(Object::toString).orElse("?")),
                Map.entry("Classifiers", annotation.getClassifiers().toString()));

        for (var entry : data) {
            panel.add(new JBLabel(entry.getKey()));
            // Uses a text field here, because one can not select/copy text from a label.
            var field = new JBTextField(entry.getValue());
            field.setEditable(false);
            panel.add(field, "growx");
        }

        var okButton = new JButton("Ok");
        panel.add(okButton, "skip 1, tag ok");

        var popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(ScrollPaneFactory.createScrollPane(panel), panel)
                .setTitle("Debug Information")
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(true)
                .setCancelOnClickOutside(false)
                .setNormalWindowLevel(true)
                .createPopup();

        okButton.addActionListener(a -> popup.closeOk((InputEvent) EventQueue.getCurrentEvent()));

        popup.showCenteredInCurrentWindow(IntellijUtil.getActiveProject());
    }
}
