/*
 * Created by JFormDesigner on Thu Jul 13 23:56:37 CEST 2023
 */

package edu.kit.kastel.extensions.guis;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import com.intellij.openapi.ui.*;
import com.intellij.ui.components.*;
import com.intellij.ui.table.*;
import com.jgoodies.forms.factories.*;
import net.miginfocom.swing.*;

/**
 * @author clemens
 */
public class AssesmentViewContent extends JPanel {
  public AssesmentViewContent() {
    initComponents();
  }

  public TextFieldWithBrowseButton getGradingConfigPathInput() {
    return gradingConfigPathInput;
  }

  public TextFieldWithBrowseButton getAutograderConfigPathInput() {
    return autograderConfigPathInput;
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
    label5 = new JLabel();
    gradingConfigPathInput = new TextFieldWithBrowseButton();
    label6 = new JLabel();
    autograderConfigPathInput = new TextFieldWithBrowseButton();
    separator1 = new JSeparator();
    generalPanel = new JPanel();
    btnGradingRound1 = new JButton();
    btnGradingRound2 = new JButton();
    button5 = new JButton();
    assessmentPanel = new JPanel();
    button1 = new JButton();
    button2 = new JButton();
    button3 = new JButton();
    button4 = new JButton();
    panel5 = new JPanel();
    label8 = new JLabel();
    StatisticsContainer = new JLabel();
    panel3 = new JPanel();
    label7 = new JLabel();
    comboBox4 = new ComboBox();
    panel4 = new JPanel();
    button6 = new JButton();
    button7 = new JButton();
    GradingPanel = new JPanel();
    TestResultsPanel = new JPanel();
    label4 = new JLabel();
    scrollPane1 = new JBScrollPane();
    testResultsTable = new JBTable();

    //======== this ========
    setBorder (new javax. swing. border. CompoundBorder( new javax .swing .border .TitledBorder (new javax. swing. border. EmptyBorder( 0
    , 0, 0, 0) , "JF\u006frmD\u0065sig\u006eer \u0045val\u0075ati\u006fn", javax. swing. border. TitledBorder. CENTER, javax. swing. border. TitledBorder. BOTTOM
    , new java .awt .Font ("Dia\u006cog" ,java .awt .Font .BOLD ,12 ), java. awt. Color. red) ,
     getBorder( )) );  addPropertyChangeListener (new java. beans. PropertyChangeListener( ){ @Override public void propertyChange (java .beans .PropertyChangeEvent e
    ) {if ("\u0062ord\u0065r" .equals (e .getPropertyName () )) throw new RuntimeException( ); }} );
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

    //======== tabbedPane1 ========
    {

      //======== AssessmentPanel ========
      {
        AssessmentPanel.setLayout(new MigLayout(
          "fillx,insets 0,hidemode 3,align left top,gap 5 5",
          // columns
          "[80:115,fill]" +
          "[151,grow,fill]",
          // rows
          "[]" +
          "[]" +
          "[]" +
          "[30]" +
          "[35:30]" +
          "[10:10]" +
          "[]" +
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

        //---- label5 ----
        label5.setText("Grading config");
        AssessmentPanel.add(label5, "cell 0 3,alignx right,growx 0");
        AssessmentPanel.add(gradingConfigPathInput, "cell 1 3");

        //---- label6 ----
        label6.setText("Autograder config");
        AssessmentPanel.add(label6, "cell 0 4");
        AssessmentPanel.add(autograderConfigPathInput, "cell 1 4");
        AssessmentPanel.add(separator1, "cell 0 5 2 1");

        //======== generalPanel ========
        {
          generalPanel.setBorder(new CompoundBorder(
            new TitledBorder(new LineBorder(Color.darkGray, 1, true), bundle.getString("AssesmentViewContent.generalPanel.border")),
            new EmptyBorder(5, 5, 5, 5)));
          generalPanel.setForeground(Color.blue);
          generalPanel.setLayout(new BorderLayout());

          //---- btnGradingRound1 ----
          btnGradingRound1.setText(bundle.getString("AssesmentViewContent.btnGradingRound1.text"));
          generalPanel.add(btnGradingRound1, BorderLayout.CENTER);

          //---- btnGradingRound2 ----
          btnGradingRound2.setText(bundle.getString("AssesmentViewContent.btnGradingRound2.text"));
          generalPanel.add(btnGradingRound2, BorderLayout.NORTH);

          //---- button5 ----
          button5.setText(bundle.getString("AssesmentViewContent.button5.text"));
          generalPanel.add(button5, BorderLayout.SOUTH);
        }
        AssessmentPanel.add(generalPanel, "cell 0 6 2 1,growx");

        //======== assessmentPanel ========
        {
          assessmentPanel.setBorder(new CompoundBorder(
            new TitledBorder(new LineBorder(Color.darkGray, 1, true), bundle.getString("AssesmentViewContent.assessmentPanel.border")),
            new EmptyBorder(5, 5, 5, 5)));
          assessmentPanel.setLayout(new GridBagLayout());
          ((GridBagLayout)assessmentPanel.getLayout()).columnWidths = new int[] {0, 0, 0};
          ((GridBagLayout)assessmentPanel.getLayout()).rowHeights = new int[] {0, 0, 0};
          ((GridBagLayout)assessmentPanel.getLayout()).columnWeights = new double[] {1.0, 1.0, 1.0E-4};
          ((GridBagLayout)assessmentPanel.getLayout()).rowWeights = new double[] {0.0, 0.0, 1.0E-4};

          //---- button1 ----
          button1.setText(bundle.getString("AssesmentViewContent.button1.text"));
          assessmentPanel.add(button1, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
            new Insets(0, 0, 5, 5), 0, 0));

          //---- button2 ----
          button2.setText(bundle.getString("AssesmentViewContent.button2.text"));
          assessmentPanel.add(button2, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
            new Insets(0, 0, 5, 0), 0, 0));

          //---- button3 ----
          button3.setText(bundle.getString("AssesmentViewContent.button3.text"));
          assessmentPanel.add(button3, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
            new Insets(0, 0, 0, 5), 0, 0));

          //---- button4 ----
          button4.setText(bundle.getString("AssesmentViewContent.button4.text"));
          assessmentPanel.add(button4, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
            new Insets(0, 0, 0, 0), 0, 0));
        }
        AssessmentPanel.add(assessmentPanel, "cell 0 7 2 1");

        //======== panel5 ========
        {
          panel5.setBorder(new TitledBorder(new LineBorder(Color.darkGray, 1, true), bundle.getString("AssesmentViewContent.panel5.border")));
          panel5.setLayout(new FlowLayout(FlowLayout.LEFT));

          //---- label8 ----
          label8.setText(bundle.getString("AssesmentViewContent.label8.text"));
          panel5.add(label8);
          panel5.add(StatisticsContainer);
        }
        AssessmentPanel.add(panel5, "cell 0 8 2 1");

        //======== panel3 ========
        {
          panel3.setBorder(new TitledBorder(new LineBorder(Color.darkGray), bundle.getString("AssesmentViewContent.panel3.border")));
          panel3.setLayout(new GridBagLayout());
          ((GridBagLayout)panel3.getLayout()).columnWidths = new int[] {85, 0, 0};
          ((GridBagLayout)panel3.getLayout()).rowHeights = new int[] {0, 0, 0};
          ((GridBagLayout)panel3.getLayout()).columnWeights = new double[] {0.0, 1.0, 1.0E-4};
          ((GridBagLayout)panel3.getLayout()).rowWeights = new double[] {0.0, 0.0, 1.0E-4};

          //---- label7 ----
          label7.setText(bundle.getString("AssesmentViewContent.label7.text"));
          panel3.add(label7, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
            GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
            new Insets(0, 0, 5, 5), 0, 0));
          panel3.add(comboBox4, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
            new Insets(0, 0, 5, 0), 0, 0));

          //======== panel4 ========
          {
            panel4.setLayout(new FlowLayout());

            //---- button6 ----
            button6.setText(bundle.getString("AssesmentViewContent.button6.text"));
            panel4.add(button6);

            //---- button7 ----
            button7.setText(bundle.getString("AssesmentViewContent.button7.text"));
            panel4.add(button7);
          }
          panel3.add(panel4, new GridBagConstraints(0, 1, 2, 1, 0.0, 0.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
            new Insets(0, 0, 0, 0), 0, 0));
        }
        AssessmentPanel.add(panel3, "cell 0 9 2 1");
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

      //======== TestResultsPanel ========
      {
        TestResultsPanel.setLayout(new MigLayout(
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
        TestResultsPanel.add(label4, "cell 0 0 2 1");

        //======== scrollPane1 ========
        {
          scrollPane1.setViewportView(testResultsTable);
        }
        TestResultsPanel.add(scrollPane1, "cell 0 1 2 1,growy");
      }
      tabbedPane1.addTab(bundle.getString("AssesmentViewContent.TestResultsPanel.tab.title"), TestResultsPanel);
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
  private JLabel label5;
  private TextFieldWithBrowseButton gradingConfigPathInput;
  private JLabel label6;
  private TextFieldWithBrowseButton autograderConfigPathInput;
  private JSeparator separator1;
  private JPanel generalPanel;
  private JButton btnGradingRound1;
  private JButton btnGradingRound2;
  private JButton button5;
  private JPanel assessmentPanel;
  private JButton button1;
  private JButton button2;
  private JButton button3;
  private JButton button4;
  private JPanel panel5;
  private JLabel label8;
  private JLabel StatisticsContainer;
  private JPanel panel3;
  private JLabel label7;
  private ComboBox comboBox4;
  private JPanel panel4;
  private JButton button6;
  private JButton button7;
  private JPanel GradingPanel;
  private JPanel TestResultsPanel;
  private JLabel label4;
  private JBScrollPane scrollPane1;
  private JBTable testResultsTable;
  // JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
