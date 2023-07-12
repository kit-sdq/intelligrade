package edu.kit.kastel.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import edu.kit.kastel.extensions.guis.DebugMenuContent;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.GridLayout;

public class MainMenuFactory implements ToolWindowFactory {
  private static final String WINDOW_TITLE = "Debug view";

  //set up automated GUI and generate necessary bindings
  private final JPanel contentPanel = new JPanel();
  private final DebugMenuContent generatedMenu = new DebugMenuContent();
  private final JPasswordField pwdInput = generatedMenu.getInputPwd();
  private final JTextField usernameField = generatedMenu.getInputUsername();

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    contentPanel.setLayout(new GridLayout());
    //add action listener to login Button
    //TODO: move this to a separate method
    generatedMenu.getBtnLogin().addActionListener(actionEvent ->
            System.out.printf("Username: %s Password: %s%n", usernameField.getText(), String.valueOf(pwdInput.getPassword()))
    );
    //add content to menu panel
    contentPanel.add(generatedMenu);
    Content content = ContentFactory.SERVICE.getInstance().createContent(
            this.contentPanel,
            WINDOW_TITLE,
            false
    );
    toolWindow.getContentManager().addContent(content);
  }

}
