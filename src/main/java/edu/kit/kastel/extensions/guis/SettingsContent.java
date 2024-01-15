/*
 * Created by JFormDesigner on Thu Jul 13 23:52:04 CEST 2023
 */

package edu.kit.kastel.extensions.guis;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import com.intellij.ui.*;
import net.miginfocom.swing.*;

/**
 * @author clemens
 */
public class SettingsContent extends JPanel {
  public SettingsContent() {
    initComponents();
  }

  public JLabel getLoggedInLabel() {
    return loggedInLabel;
  }

  public JTextField getArtemisUrlInput() {
    return artemisUrlInput;
  }

  public JTextField getInputUsername() {
    return InputUsername;
  }

  public JPasswordField getInputPwd() {
    return inputPwd;
  }

  public JButton getBtnLogin() {
    return btnLogin;
  }

  private void createUIComponents() {
    // TODO: add custom component creation code here
  }

  public JSpinner getNumColsSlider() {
    return numColsSlider;
  }

  public ColorPanel getAnnotationColorPicker() {
      return annotationColorPicker;
  }

  private void initComponents() {
    // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
    // Generated using JFormDesigner Evaluation license - CHServer root Passwort
    ResourceBundle bundle = ResourceBundle.getBundle("guiStrings");
    label3 = new JLabel();
    loggedInLabel = new JLabel();
    separator1 = new JSeparator();
    label4 = new JLabel();
    artemisUrlInput = new JTextField();
    label1 = new JLabel();
    InputUsername = new JTextField();
    label2 = new JLabel();
    inputPwd = new JPasswordField();
    btnLogin = new JButton();
    separator2 = new JSeparator();
    label5 = new JLabel();
    numColsSlider = new JSpinner();
    label6 = new JLabel();
    annotationColorPicker = new ColorPanel();

    //======== this ========
    setBorder ( new javax . swing. border .CompoundBorder ( new javax . swing. border .TitledBorder ( new javax . swing. border .
    EmptyBorder ( 0, 0 ,0 , 0) ,  "JF\u006frmDes\u0069gner \u0045valua\u0074ion" , javax. swing .border . TitledBorder. CENTER ,javax . swing
    . border .TitledBorder . BOTTOM, new java. awt .Font ( "D\u0069alog", java .awt . Font. BOLD ,12 ) ,
    java . awt. Color .red ) , getBorder () ) );  addPropertyChangeListener( new java. beans .PropertyChangeListener ( )
    { @Override public void propertyChange (java . beans. PropertyChangeEvent e) { if( "\u0062order" .equals ( e. getPropertyName () ) )
    throw new RuntimeException( ) ;} } );
    setLayout(new MigLayout(
        "fillx,insets 0,hidemode 3,align left top,gap 0 0",
        // columns
        "[109,fill]" +
        "[grow,fill]",
        // rows
        "[]" +
        "[]" +
        "[]" +
        "[top]" +
        "[top]" +
        "[top]" +
        "[]" +
        "[]" +
        "[]"));

    //---- label3 ----
    label3.setText(bundle.getString("DebugMenuContent.label3.text"));
    add(label3, "cell 0 0");

    //---- loggedInLabel ----
    loggedInLabel.setText(bundle.getString("DebugMenuContent.loggedInLabel.text"));
    loggedInLabel.setForeground(Color.red);
    add(loggedInLabel, "cell 1 0");
    add(separator1, "cell 0 1 2 1");

    //---- label4 ----
    label4.setText(bundle.getString("DebugMenuContent.label4.text"));
    add(label4, "cell 0 2");

    //---- artemisUrlInput ----
    artemisUrlInput.setText(bundle.getString("DebugMenuContent.artemisUrlInput.text"));
    add(artemisUrlInput, "cell 1 2");

    //---- label1 ----
    label1.setText(bundle.getString("labelUnameField"));
    add(label1, "cell 0 3,alignx left,growx 0");
    add(InputUsername, "cell 1 3,growx");

    //---- label2 ----
    label2.setText(bundle.getString("LabelPwdField"));
    add(label2, "cell 0 4,alignx left,growx 0");
    add(inputPwd, "cell 1 4,growx");

    //---- btnLogin ----
    btnLogin.setText(bundle.getString("DebugMenuContent.btnLogin.text"));
    add(btnLogin, "cell 0 5 2 1");
    add(separator2, "cell 0 6 2 1");

    //---- label5 ----
    label5.setText(bundle.getString("DebugMenuContent.label5.text"));
    add(label5, "cell 0 7");

    //---- numColsSlider ----
    numColsSlider.setModel(new SpinnerNumberModel(2, 1, null, 1));
    add(numColsSlider, "cell 1 7");

    //---- label6 ----
    label6.setText(bundle.getString("AnnotationColor"));
    add(label6, "cell 0 8");

    //---- annotationColorPicker ----
    annotationColorPicker.setSelectedColor(new Color(0x9b3636));
    add(annotationColorPicker, "cell 1 8");
    // JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
  }

  // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
  // Generated using JFormDesigner Evaluation license - CHServer root Passwort
  private JLabel label3;
  private JLabel loggedInLabel;
  private JSeparator separator1;
  private JLabel label4;
  private JTextField artemisUrlInput;
  private JLabel label1;
  private JTextField InputUsername;
  private JLabel label2;
  private JPasswordField inputPwd;
  private JButton btnLogin;
  private JSeparator separator2;
  private JLabel label5;
  private JSpinner numColsSlider;
  private JLabel label6;
  private ColorPanel annotationColorPicker;
  // JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
