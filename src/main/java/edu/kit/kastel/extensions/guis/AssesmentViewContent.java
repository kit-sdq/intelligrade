/*
 * Created by JFormDesigner on Thu Jul 13 23:56:37 CEST 2023
 */

package edu.kit.kastel.extensions.guis;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import com.intellij.openapi.ui.*;
import com.intellij.ui.components.*;
import com.intellij.ui.table.*;
import net.miginfocom.swing.*;

/**
 * @author clemens
 */
public class AssesmentViewContent extends JPanel {
  public AssesmentViewContent() {
    initComponents();
  }

  private void initComponents() {
    // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
    // Generated using JFormDesigner Evaluation license - Clemens
    ResourceBundle bundle = ResourceBundle.getBundle("guiStrings");
    tabbedPane1 = new JBTabbedPane();
    AssessmentPanel = new JPanel();
    label1 = new JLabel();
    comboBox1 = new ComboBox();
    label2 = new JLabel();
    comboBox2 = new ComboBox();
    label3 = new JLabel();
    comboBox3 = new ComboBox();
    GradingPanel = new JPanel();
    panel1 = new JPanel();
    label4 = new JLabel();
    scrollPane1 = new JBScrollPane();
    testResultsTable = new JBTable();

    //======== this ========
    setBorder (new javax. swing. border. CompoundBorder( new javax .swing .border .TitledBorder (new javax. swing
    . border. EmptyBorder( 0, 0, 0, 0) , "JFor\u006dDesi\u0067ner \u0045valu\u0061tion", javax. swing. border. TitledBorder
    . CENTER, javax. swing. border. TitledBorder. BOTTOM, new java .awt .Font ("Dia\u006cog" ,java .
    awt .Font .BOLD ,12 ), java. awt. Color. red) , getBorder( )) )
    ;  addPropertyChangeListener (new java. beans. PropertyChangeListener( ){ @Override public void propertyChange (java .beans .PropertyChangeEvent e
    ) {if ("bord\u0065r" .equals (e .getPropertyName () )) throw new RuntimeException( ); }} )
    ;
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

    //======== tabbedPane1 ========
    {

      //======== AssessmentPanel ========
      {
        AssessmentPanel.setLayout(new MigLayout(
          "fillx,insets 0,hidemode 3,align left top,gap 5 5",
          // columns
          "[80:80,fill]" +
          "[grow,fill]",
          // rows
          "[]" +
          "[]" +
          "[]"));

        //---- label1 ----
        label1.setText(bundle.getString("AssesmentViewContent.label1.text"));
        AssessmentPanel.add(label1, "pad 0,cell 0 0,alignx right,growx 0");
        AssessmentPanel.add(comboBox1, "cell 1 0");

        //---- label2 ----
        label2.setText(bundle.getString("AssesmentViewContent.label2.text"));
        AssessmentPanel.add(label2, "cell 0 1,alignx right,growx 0");
        AssessmentPanel.add(comboBox2, "cell 1 1");

        //---- label3 ----
        label3.setText(bundle.getString("AssesmentViewContent.label3.text"));
        AssessmentPanel.add(label3, "cell 0 2,alignx right,growx 0");
        AssessmentPanel.add(comboBox3, "cell 1 2");
      }
      tabbedPane1.addTab("Assessment", AssessmentPanel);

      //======== GradingPanel ========
      {
        GradingPanel.setLayout(new MigLayout(
          "fillx,hidemode 3,align left top",
          // columns
          "[fill]" +
          "[fill]",
          // rows
          "[]" +
          "[]" +
          "[]"));
      }
      tabbedPane1.addTab(bundle.getString("AssesmentViewContent.GradingPanel.tab.title"), GradingPanel);

      //======== panel1 ========
      {
        panel1.setLayout(new MigLayout(
          "fillx,hidemode 3,align left top",
          // columns
          "[fill]" +
          "[fill]",
          // rows
          "[36]" +
          "[grow]"));

        //---- label4 ----
        label4.setText(bundle.getString("AssesmentViewContent.label4.text"));
        label4.setFont(label4.getFont().deriveFont(label4.getFont().getStyle() | Font.BOLD));
        panel1.add(label4, "cell 0 0 2 1");

        //======== scrollPane1 ========
        {
          scrollPane1.setViewportView(testResultsTable);
        }
        panel1.add(scrollPane1, "cell 0 1 2 1,growy");
      }
      tabbedPane1.addTab(bundle.getString("AssesmentViewContent.panel1.tab.title"), panel1);
    }
    add(tabbedPane1);
    // JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
  }

  // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
  // Generated using JFormDesigner Evaluation license - Clemens
  private JBTabbedPane tabbedPane1;
  private JPanel AssessmentPanel;
  private JLabel label1;
  private ComboBox comboBox1;
  private JLabel label2;
  private ComboBox comboBox2;
  private JLabel label3;
  private ComboBox comboBox3;
  private JPanel GradingPanel;
  private JPanel panel1;
  private JLabel label4;
  private JBScrollPane scrollPane1;
  private JBTable testResultsTable;
  // JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
