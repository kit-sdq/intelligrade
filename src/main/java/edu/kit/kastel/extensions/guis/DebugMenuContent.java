/*
 * Created by JFormDesigner on Thu Jul 13 00:33:53 CEST 2023
 */

package edu.kit.kastel.extensions.guis;

import java.util.*;
import javax.swing.*;
import net.miginfocom.swing.*;

/**
 * @author clemens
 */
public class DebugMenuContent extends JPanel {
  public DebugMenuContent() {
    initComponents();
  }

  public JButton getBtnLogin() {
    return btnLogin;
  }

  public JTextField getInputUsername() {
    return InputUsername;
  }

  public JPasswordField getInputPwd() {
    return inputPwd;
  }

  public JLabel getLoggedInLabel() {
    return loggedInLabel;
  }


  private void initComponents() {
    // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
    // Generated using JFormDesigner Evaluation license - Clemens
    ResourceBundle bundle = ResourceBundle.getBundle("guiStrings");
    label3 = new JLabel();
    loggedInLabel = new JLabel();
    separator1 = new JSeparator();
    label1 = new JLabel();
    InputUsername = new JTextField();
    label2 = new JLabel();
    inputPwd = new JPasswordField();
    btnLogin = new JButton();

    //======== this ========
    setBorder ( new javax . swing. border .CompoundBorder ( new javax . swing. border .TitledBorder ( new javax
    . swing. border .EmptyBorder ( 0, 0 ,0 , 0) ,  "JF\u006frmDes\u0069gner \u0045valua\u0074ion" , javax. swing
    .border . TitledBorder. CENTER ,javax . swing. border .TitledBorder . BOTTOM, new java. awt .
    Font ( "D\u0069alog", java .awt . Font. BOLD ,12 ) ,java . awt. Color .red
    ) , getBorder () ) );  addPropertyChangeListener( new java. beans .PropertyChangeListener ( ){ @Override
    public void propertyChange (java . beans. PropertyChangeEvent e) { if( "\u0062order" .equals ( e. getPropertyName (
    ) ) )throw new RuntimeException( ) ;} } );
    setLayout(new MigLayout(
      "fillx,insets 0,hidemode 3,align left top,gap 0 0",
      // columns
      "[90,fill]" +
      "[grow,fill]",
      // rows
      "[]" +
      "[]" +
      "[top]" +
      "[top]" +
      "[top]"));

    //---- label3 ----
    label3.setText(bundle.getString("DebugMenuContent.label3.text"));
    add(label3, "cell 0 0");

    //---- loggedInLabel ----
    loggedInLabel.setText(bundle.getString("DebugMenuContent.loggedInLabel.text"));
    add(loggedInLabel, "cell 1 0");
    add(separator1, "cell 0 1 2 1");

    //---- label1 ----
    label1.setText(bundle.getString("labelUnameField"));
    add(label1, "cell 0 2,alignx left,growx 0");
    add(InputUsername, "cell 1 2,growx");

    //---- label2 ----
    label2.setText(bundle.getString("LabelPwdField"));
    add(label2, "cell 0 3,alignx left,growx 0");
    add(inputPwd, "cell 1 3,growx");

    //---- btnLogin ----
    btnLogin.setText("log in");
    add(btnLogin, "cell 0 4 2 1");
    // JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
  }

  // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
  // Generated using JFormDesigner Evaluation license - Clemens
  private JLabel label3;
  private JLabel loggedInLabel;
  private JSeparator separator1;
  private JLabel label1;
  private JTextField InputUsername;
  private JLabel label2;
  private JPasswordField inputPwd;
  private JButton btnLogin;
  // JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
