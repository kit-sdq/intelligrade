/* Licensed under EPL-2.0 2024-2026. */
package edu.kit.kastel.sdq.intelligrade.extensions.tool_windows;

import java.util.List;

import javax.swing.Icon;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.ExercisePanel;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.GradingPanel;
import edu.kit.kastel.sdq.intelligrade.extensions.guis.TestCasePanel;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisCredentialsProvider;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.state.ArtemisConnectionService;
import edu.kit.kastel.sdq.intelligrade.state.ArtemisConnectionState;
import edu.kit.kastel.sdq.intelligrade.state.ProjectState;
import org.jspecify.annotations.NonNull;

/**
 * This class handles all logic for the main grading UI.
 * It does not handle any other logic, that should be factored out.
 */
public class MainToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NonNull Project project, @NonNull ToolWindow toolWindow) {
        toolWindow.setTitleActions(List.of(new ReconnectAction(project), new LogoutAction(project)));
        toolWindow.getContentManager().addContent(createExerciseContent(project, toolWindow));
        toolWindow.getContentManager().addContent(createGradingContent(project));
        toolWindow.getContentManager().addContent(createTestResultsContent(project));
    }

    private static Content createExerciseContent(@NonNull Project project, ToolWindow toolWindow) {
        var disposable = Disposer.newDisposable("IntelliGrade Exercise Panel");
        var content = ContentFactory.getInstance()
                .createContent(new ExercisePanel(disposable, project, toolWindow), "Exercise", false);
        content.setDisposer(disposable);
        return content;
    }

    private static Content createGradingContent(@NonNull Project project) {
        var disposable = Disposer.newDisposable("IntelliGrade Grading Panel");
        var content =
                ContentFactory.getInstance().createContent(new GradingPanel(disposable, project), "Grading", false);
        content.setDisposer(disposable);
        return content;
    }

    private static Content createTestResultsContent(@NonNull Project project) {
        var disposable = Disposer.newDisposable("IntelliGrade Test Results Panel");
        var content = ContentFactory.getInstance()
                .createContent(new TestCasePanel(disposable, project), "Test Results", false);
        content.setDisposer(disposable);
        return content;
    }

    // These are the buttons in the right corner to reconnect and logout

    private abstract static class ConnectionAction extends DumbAwareAction {
        ConnectionAction(String text, String description, Icon icon) {
            super(text, description, icon);
        }

        @Override
        public @NonNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    private static final class ReconnectAction extends ConnectionAction {
        private final Project project;

        private ReconnectAction(@NonNull Project project) {
            super("Reconnect", "Reconnect to Artemis", AllIcons.Actions.Refresh);

            this.project = project;
        }

        @Override
        public void update(@NonNull AnActionEvent event) {
            var state = ArtemisConnectionService.getInstance(project).getState();
            event.getPresentation()
                    .setEnabled(state instanceof ArtemisConnectionState.Connected
                            || state instanceof ArtemisConnectionState.Failed);
        }

        @Override
        public void actionPerformed(@NonNull AnActionEvent event) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                var settings = ArtemisSettingsState.getInstance();
                var credentials = ArtemisCredentialsProvider.getInstance();

                ArtemisConnectionService.LoginMethod loginMethod = settings.isUseTokenLogin()
                        ? new ArtemisConnectionService.TokenLogin()
                        : new ArtemisConnectionService.PasswordLogin(
                                settings.getUsername(), credentials.getArtemisPassword());

                if (settings.getArtemisInstanceUrl() != null) {
                    ArtemisConnectionService.getInstance(this.project)
                            .connectInBackground(settings.getArtemisInstanceUrl(), loginMethod);
                }
            });
        }
    }

    private static final class LogoutAction extends ConnectionAction {
        private final Project project;

        private LogoutAction(@NonNull Project project) {
            super("Logout", "Log out from Artemis", AllIcons.Actions.Exit);
            this.project = project;
        }

        @Override
        public void update(@NonNull AnActionEvent event) {
            var state = ArtemisConnectionService.getInstance(project).getState();
            event.getPresentation()
                    .setEnabled(state instanceof ArtemisConnectionState.Connected
                            || state instanceof ArtemisConnectionState.Failed);
        }

        @Override
        public void actionPerformed(@NonNull AnActionEvent event) {
            if (ProjectState.getInstance(this.project).isAssessing()) {
                boolean hasConfirmed = MessageDialogBuilder.okCancel(
                                "Logging out while assessing!",
                                "Logging out while assessing will discard current changes. Continue?")
                        .guessWindowAndAsk();

                if (!hasConfirmed) {
                    return;
                }
            }

            ArtemisConnectionService.getInstance(project).logout();
        }
    }
}
