package edu.kit.kastel.extensions.tool_windows;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import edu.kit.kastel.extensions.guis.AssesmentViewContent;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.GridLayout;

public class MainToolWindowFactory implements ToolWindowFactory {

  //set up automated GUI and generate necessary bindings
  private final JPanel contentPanel = new JPanel();
  private final AssesmentViewContent generatedMenu = new AssesmentViewContent();
  private final TextFieldWithBrowseButton gradingConfigInput = generatedMenu.getGradingConfigPathInput();
  private final TextFieldWithBrowseButton autograderConfigInput = generatedMenu.getAutograderConfigPathInput();

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    contentPanel.setLayout(new GridLayout());

    //add content to menu panel
    contentPanel.add(generatedMenu);
    Content content = ContentFactory.getInstance().createContent(
            this.contentPanel,
            null,
            false
    );

    addListeners();

    toolWindow.getContentManager().addContent(content);
  }

  private void addListeners() {
    gradingConfigInput.addBrowseFolderListener(
            new TextBrowseFolderListener(new FileChooserDescriptor(
                    true,
                    false,
                    false,
                    false,
                    false,
                    false)
            ));
    autograderConfigInput.addBrowseFolderListener(new TextBrowseFolderListener(new FileChooserDescriptor(
            true,
            false,
            false,
            false,
            false,
            false)
    ));
  }
}
