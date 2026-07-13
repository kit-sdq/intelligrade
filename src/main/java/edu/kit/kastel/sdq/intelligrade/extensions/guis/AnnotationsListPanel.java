/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis;

import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.tree.TreePath;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnectionHolder;
import edu.kit.kastel.sdq.artemis4j.grading.UserIdentifier;
import edu.kit.kastel.sdq.intelligrade.AssessmentTracker;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.table.AnnotationsTableModel;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.table.AnnotationsTreeTable;
import edu.kit.kastel.sdq.intelligrade.listeners.AssessmentStateListener;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import edu.kit.kastel.sdq.intelligrade.state.AnnotationSelectionService;
import edu.kit.kastel.sdq.intelligrade.state.ProjectState;
import edu.kit.kastel.sdq.intelligrade.utils.LatestRequestRunner;
import net.miginfocom.swing.MigLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class AnnotationsListPanel extends SimpleToolWindowPanel {
    private static final Logger LOG = Logger.getInstance(AnnotationsListPanel.class);

    private final Project project;
    private final LatestRequestRunner requestRunner;

    private final AnnotationsTableModel model;
    private final AnnotationsTreeTable table;

    private final Disposable parentDisposable;

    public AnnotationsListPanel(Disposable parentDisposable, @NonNull Project project) {
        super(true, true);
        AnnotationSelectionService.getInstance(project).registerPanel(this, parentDisposable);
        this.project = project;
        this.requestRunner = new LatestRequestRunner(project);
        this.parentDisposable = parentDisposable;

        this.model = new AnnotationsTableModel();
        this.table = new AnnotationsTreeTable(model, project);

        setContent(ScrollPaneFactory.createScrollPane(table));

        // Add the right-click menu
        addPopupMenu();

        AssessmentTracker.getInstance(project).subscribe(parentDisposable, new AssessmentStateListener() {
            @Override
            public void activeAssessmentChanged(@Nullable ActiveAssessment assessment) {
                if (assessment == null) {
                    clearAnnotations();
                } else {
                    showAnnotations(assessment.getAssessment().getAnnotations(true));
                }
            }

            @Override
            public void annotationsChanged(
                    @NonNull ActiveAssessment assessment, @NonNull List<Annotation> annotations) {
                showAnnotations(annotations);
            }
        });
    }

    private void showAnnotations(List<Annotation> annotations) {
        // save the currently expanded paths (so they stay open after the annotations change)
        Set<TreePath> expandedPaths = new HashSet<>(table.getTree().getExpandedPaths());
        model.setAnnotations(annotations);

        table.revalidate();
        table.updateUI();

        // restore the expanded paths
        table.getTree().expandPaths(expandedPaths);
    }

    private void clearAnnotations() {
        model.setAnnotations(List.of());
        table.updateUI();
    }

    public void selectAnnotation(Annotation annotation) {
        this.table.selectAnnotation(annotation);
    }

    private void addPopupMenu() {
        AnActionButton restoreButton;
        var group = new DefaultActionGroup();

        var editButton = new AnActionButton("Edit Custom Message/Score") {
            @Override
            public void actionPerformed(@NonNull AnActionEvent e) {
                table.editCustomMessageOfSelection();
            }

            @Override
            public @NonNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
        group.addAction(editButton);

        var deleteButton = new AnActionButton("Delete") {
            @Override
            public void actionPerformed(@NonNull AnActionEvent e) {
                table.deleteSelection();
            }

            @Override
            public @NonNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
        group.addAction(deleteButton);

        restoreButton = new AnActionButton("Restore") {
            @Override
            public void actionPerformed(@NonNull AnActionEvent e) {
                table.restoreSelection();
            }

            @Override
            public @NonNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
        AssessmentTracker.getInstance(project).subscribe(parentDisposable, new AssessmentStateListener() {
            @Override
            public void activeAssessmentChanged(@Nullable ActiveAssessment assessment) {
                // only show the restore button in review mode
                if (assessment != null && assessment.isReview()) {
                    group.addAction(restoreButton);
                } else {
                    group.remove(restoreButton);
                }
            }
        });

        // Adds a debug button to the right-click menu in the table.
        //
        // There is some data regarding the annotations that is not visible in the table,
        // like what exact location the annotation refers to or which problem type in the autograder
        // emitted the annotation.
        var debugButton = new AnActionButton("Debug Information") {
            private record AnnotationUsers(String creator, String suppressor) {}

            @Override
            public void actionPerformed(@NonNull AnActionEvent e) {
                var annotations = table.getSelectedAnnotations();
                if (annotations.isEmpty()) {
                    return;
                }

                // we only emit information about the first selected annotation
                var annotation = annotations.getFirst();
                requestRunner
                        .fetchArtemis(() -> {
                            String creator = mapAssessor(annotation.getCreator().orElse(null));
                            String suppressor =
                                    mapAssessor(annotation.getSuppressor().orElse(null));

                            return new AnnotationUsers(creator, suppressor);
                        })
                        .thenIf(() -> true, data -> {
                            showDebugDialog(annotation, data.creator(), data.suppressor());
                        });
            }

            @Override
            public @NonNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
        group.addAction(debugButton);

        PopupHandler.installPopupMenu(table, group, "popup@AnnotationsListPanel");
    }

    @RequiresBackgroundThread
    private String mapAssessor(UserIdentifier id) {
        return Optional.ofNullable(id)
                .map(uid -> {
                    try {
                        var connection = ProjectState.getInstance(project)
                                .getActiveExercise()
                                .map(ArtemisConnectionHolder::getConnection)
                                .orElse(null);

                        if (connection == null) {
                            return null;
                        }

                        return connection
                                .findUserByUserIdentifier(uid)
                                .map(value -> "%s (%d)".formatted(value.getLogin(), value.getId()))
                                .orElse(null);

                    } catch (ArtemisNetworkException exception) {
                        LOG.warn("failed to fetch user for id %s".formatted(id), exception);
                        return null;
                    }
                })
                .orElse("?");
    }

    private void showDebugDialog(Annotation annotation, String creator, String suppressor) {
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
                Map.entry("Created By", creator),
                Map.entry("Suppressed", annotation.isSuppressed() ? "Yes" : "No"),
                Map.entry("Suppressed By", suppressor),
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

        okButton.addActionListener(_ -> popup.closeOk((InputEvent) EventQueue.getCurrentEvent()));

        popup.showCenteredInCurrentWindow(project);
    }
}
