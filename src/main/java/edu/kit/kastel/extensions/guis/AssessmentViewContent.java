/* Licensed under EPL-2.0 2023-2024. */
package edu.kit.kastel.extensions.guis;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ResourceBundle;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import edu.kit.kastel.sdq.artemis4j.grading.Course;
import edu.kit.kastel.sdq.artemis4j.grading.Exam;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingExercise;
import edu.kit.kastel.sdq.artemis4j.grading.ProgrammingSubmission;
import net.miginfocom.swing.MigLayout;

/**
 * @author clemens
 */
public class AssessmentViewContent extends JPanel {
    public AssessmentViewContent() {
        initComponents();
    }

    public ComboBox<Course> getCoursesDropdown() {
        return coursesDropdown;
    }

    public ComboBox<Exam> getExamsDropdown() {
        return examsDropdown;
    }

    public ComboBox<ProgrammingExercise> getExercisesDropdown() {
        return exercisesDropdown;
    }

    public ComboBox<ProgrammingSubmission> getBacklogSelector() {
        return backlogSelector;
    }

    public TextFieldWithBrowseButton getGradingConfigPathInput() {
        return gradingConfigPathInput;
    }

    public JButton getBtnGradingRound1() {
        return btnGradingRound1;
    }

    public JPanel getRatingGroupContainer() {
        return ratingGroupContainer;
    }

    public JButton getBtnSaveAssessment() {
        return btnSaveAssessment;
    }

    public JButton getSubmitAssesmentBtn() {
        return submitAssesmentBtn;
    }

    public JBLabel getAssessmentModeLabel() {
        return assessmentModeLabel;
    }

    public StatisticsContainer getStatisticsContainer() {
        return statisticsContainer;
    }

    private void createUIComponents() {
        // TODO: add custom component creation code here
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        // Generated using JFormDesigner Evaluation license - FooBar
        ResourceBundle bundle = ResourceBundle.getBundle("guiStrings");
        var tabbedPane1 = new JBTabbedPane();
        scrollPane2 = new JBScrollPane();
        var AssessmentPanel = new JPanel();
        var label1 = new JLabel();
        coursesDropdown = new ComboBox<>();
        var label2 = new JLabel();
        examsDropdown = new ComboBox<>();
        var label3 = new JLabel();
        exercisesDropdown = new ComboBox<>();
        var label5 = new JLabel();
        gradingConfigPathInput = new TextFieldWithBrowseButton();
        var separator1 = new JSeparator();
        var generalPanel = new JPanel();
        btnGradingRound1 = new JButton();
        btnGradingRound2 = new JButton();
        button5 = new JButton();
        var assessmentPanel = new JPanel();
        btnSaveAssessment = new JButton();
        submitAssesmentBtn = new JButton();
        button3 = new JButton();
        button4 = new JButton();
        var panel5 = new JPanel();
        var label8 = new JBLabel();
        statisticsContainer = new StatisticsContainer();
        label9 = new JBLabel();
        assessmentModeLabel = new JBLabel();
        var panel3 = new JPanel();
        var label7 = new JLabel();
        backlogSelector = new ComboBox<>();
        panel4 = new JPanel();
        button6 = new JButton();
        button7 = new JButton();
        GradingPanel = new JPanel();
        scrollPane = new JScrollPane();
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        ratingGroupContainer = new JPanel();
        TestResultsPanel = new JPanel();
        var label4 = new JLabel();
        var scrollPane1 = new JBScrollPane();
        testResultsTable = new JBTable();

        //======== this ========
        setBorder (new javax. swing. border. CompoundBorder( new javax .swing .border .TitledBorder (new
        javax. swing. border. EmptyBorder( 0, 0, 0, 0) , "JF\u006frmDes\u0069gner \u0045valua\u0074ion", javax
        . swing. border. TitledBorder. CENTER, javax. swing. border. TitledBorder. BOTTOM, new java
        .awt .Font ("D\u0069alog" ,java .awt .Font .BOLD ,12 ), java. awt
        . Color. red) , getBorder( )) );  addPropertyChangeListener (new java. beans.
        PropertyChangeListener( ){ @Override public void propertyChange (java .beans .PropertyChangeEvent e) {if ("\u0062order" .
        equals (e .getPropertyName () )) throw new RuntimeException( ); }} );
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        //======== tabbedPane1 ========
        {

            //======== scrollPane2 ========
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
                        "[10:10]" +
                        "[]" +
                        "[]" +
                        "[]" +
                        "[]" +
                        "[]"));

                    //---- label1 ----
                    label1.setText(bundle.getString("AssesmentViewContent.label1.text"));
                    AssessmentPanel.add(label1, "pad 0,cell 0 0,alignx label,growx 0");
                    AssessmentPanel.add(coursesDropdown, "cell 1 0");

                    //---- label2 ----
                    label2.setText(bundle.getString("AssesmentViewContent.label2.text"));
                    AssessmentPanel.add(label2, "cell 0 1,alignx label,growx 0");
                    AssessmentPanel.add(examsDropdown, "cell 1 1");

                    //---- label3 ----
                    label3.setText(bundle.getString("AssesmentViewContent.label3.text"));
                    AssessmentPanel.add(label3, "cell 0 2,alignx label,growx 0");
                    AssessmentPanel.add(exercisesDropdown, "cell 1 2");

                    //---- label5 ----
                    label5.setText("Grading config");
                    AssessmentPanel.add(label5, "cell 0 3,alignx label,growx 0");

                    //---- gradingConfigPathInput ----
                    gradingConfigPathInput.setEditable(false);
                    AssessmentPanel.add(gradingConfigPathInput, "cell 1 3");
                    AssessmentPanel.add(separator1, "cell 0 4 2 1");

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
                    AssessmentPanel.add(generalPanel, "cell 0 5 2 1,growx");

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

                        //---- btnSaveAssessment ----
                        btnSaveAssessment.setText(bundle.getString("AssesmentViewContent.btnSaveAssessment.text"));
                        assessmentPanel.add(btnSaveAssessment, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                            GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                            new Insets(0, 0, 5, 5), 0, 0));

                        //---- submitAssesmentBtn ----
                        submitAssesmentBtn.setText(bundle.getString("AssesmentViewContent.submitAssesmentBtn.text"));
                        assessmentPanel.add(submitAssesmentBtn, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
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
                    AssessmentPanel.add(assessmentPanel, "cell 0 6 2 1");

                    //======== panel5 ========
                    {
                        panel5.setBorder(new TitledBorder(new LineBorder(Color.darkGray, 1, true), bundle.getString("AssesmentViewContent.panel5.border")));
                        panel5.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), 0, 0));

                        //---- label8 ----
                        label8.setText(bundle.getString("AssesmentViewContent.label8.text"));
                        panel5.add(label8, new GridConstraints(0, 0, 1, 1,
                            GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                            null, null, null));
                        panel5.add(statisticsContainer, new GridConstraints(0, 1, 1, 1,
                            GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                            null, null, null));

                        //---- label9 ----
                        label9.setText(bundle.getString("AssesmentViewContent.label9.text"));
                        panel5.add(label9, new GridConstraints(1, 0, 1, 1,
                            GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                            null, null, null));

                        //---- assessmentModeLabel ----
                        assessmentModeLabel.setText("\u274c");
                        assessmentModeLabel.setIcon(null);
                        assessmentModeLabel.setHorizontalAlignment(SwingConstants.LEFT);
                        panel5.add(assessmentModeLabel, new GridConstraints(1, 1, 1, 1,
                            GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                            null, null, null));
                    }
                    AssessmentPanel.add(panel5, "pad 0,cell 0 7 2 1,growx");

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
                        panel3.add(backlogSelector, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
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
                    AssessmentPanel.add(panel3, "cell 0 8 2 1");
                }
                scrollPane2.setViewportView(AssessmentPanel);
            }
            tabbedPane1.addTab(bundle.getString("AssesmentViewContent.assessmentPanel.border"), scrollPane2);

            //======== GradingPanel ========
            {
                GradingPanel.setLayout(new MigLayout(
                    "fillx,hidemode 3,align left top",
                    // columns
                    "[fill]",
                    // rows
                    "[grow]"));

                //======== scrollPane ========
                {
                    scrollPane.setBorder(BorderFactory.createEmptyBorder());

                    //======== ratingGroupContainer ========
                    {
                        ratingGroupContainer.setLayout(new BoxLayout(ratingGroupContainer, BoxLayout.Y_AXIS));
                    }
                    scrollPane.setViewportView(ratingGroupContainer);
                }
                GradingPanel.add(scrollPane, "cell 0 0,growy");
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
    // Generated using JFormDesigner Evaluation license - FooBar
    private JBScrollPane scrollPane2;
    private ComboBox<Course> coursesDropdown;
    private ComboBox<Exam> examsDropdown;
    private ComboBox<ProgrammingExercise> exercisesDropdown;
    private TextFieldWithBrowseButton gradingConfigPathInput;
    private JButton btnGradingRound1;
    private JButton btnGradingRound2;
    private JButton button5;
    private JButton btnSaveAssessment;
    private JButton submitAssesmentBtn;
    private JButton button3;
    private JButton button4;
    private StatisticsContainer statisticsContainer;
    private JBLabel label9;
    private JBLabel assessmentModeLabel;
    private ComboBox<ProgrammingSubmission> backlogSelector;
    private JPanel panel4;
    private JButton button6;
    private JButton button7;
    private JPanel GradingPanel;
    private JScrollPane scrollPane;
    private JPanel ratingGroupContainer;
    private JPanel TestResultsPanel;
    private JBTable testResultsTable;
  // JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
