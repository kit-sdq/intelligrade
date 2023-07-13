package edu.kit.kastel.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import edu.kit.kastel.extensions.guis.DebugMenuContent;
import edu.kit.kastel.sdq.artemis4j.api.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.client.RestClientManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;

public class MainMenuFactory implements ToolWindowFactory {
  private static final String WINDOW_TITLE = "Debug view";
  private static final String LOGIN_ERROR_DIALOG_TITLE = "Error logging in!";

  //set up automated GUI and generate necessary bindings
  private final JPanel contentPanel = new JPanel();
  private final DebugMenuContent generatedMenu = new DebugMenuContent();
  private final JPasswordField pwdInput = generatedMenu.getInputPwd();
  private final JTextField usernameField = generatedMenu.getInputUsername();
  private final JLabel loggedInLabel = generatedMenu.getLoggedInLabel();
  private final JTextField artemisUrlField = generatedMenu.getArtemisUrlInput();

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    contentPanel.setLayout(new GridLayout());
    //add action listener to login Button
    generatedMenu.getBtnLogin().addActionListener(this::loginButtonListener);

    //add content to menu panel
    contentPanel.add(generatedMenu);
    Content content = ContentFactory.getInstance().createContent(
            this.contentPanel,
            WINDOW_TITLE,
            false
    );
    toolWindow.getContentManager().addContent(content);
  }

  /**
   * Listener Method that gets called when the login Button is pressed
   * This method will Log in the User
   *
   * @param actionEvent The Event passed by AWT is the Button is pressed
   */
  private void loginButtonListener(ActionEvent actionEvent) {
    //create new Artemis Instance
    var artemisInstance = new RestClientManager(
            artemisUrlField.getText(),
            usernameField.getText(),
            new String(pwdInput.getPassword())
    );

    //try logging in and display error iff error ocurred
    try {
      artemisInstance.login();
    } catch (ArtemisClientException e) {
      JOptionPane.showMessageDialog(contentPanel, e.getMessage(), LOGIN_ERROR_DIALOG_TITLE, JOptionPane.ERROR_MESSAGE);
    }

    //set label if login was successful
    if (artemisInstance.isReady()) {
      loggedInLabel.setText("true");
      loggedInLabel.setForeground(JBColor.GREEN);
    }

  }

}
