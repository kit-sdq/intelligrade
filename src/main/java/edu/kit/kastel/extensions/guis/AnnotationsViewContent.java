/* Licensed under EPL-2.0 2023-2024. */
package edu.kit.kastel.extensions.guis;

import java.awt.event.*;

import javax.swing.*;

import com.intellij.ui.components.*;
import com.intellij.ui.table.*;
import edu.kit.kastel.state.PluginState;

/**
 * @author clemens
 */
public class AnnotationsViewContent extends JPanel {
    public AnnotationsViewContent() {
        initComponents();
        PluginState.getInstance()
                .registerAssessmentStartedListener(
                        assessment -> assessment.registerAnnotationsUpdatedListener(annotations -> {
                            AnnotationsTableModel model = ((AnnotationsTableModel) this.annotationsTable.getModel());
                            model.setAnnotations(annotations);
                            this.annotationsTable.updateUI();
                        }));

        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            AnnotationsTableModel model = ((AnnotationsTableModel) this.annotationsTable.getModel());
            model.clearAnnotations();
            this.annotationsTable.updateUI();
        });
    }

    private void createUIComponents() {
        // TODO: add custom component creation code here
    }

    private void annotationsTableKeyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
            int selectedRow = annotationsTable.getSelectedRow();
            if (selectedRow != -1) {
                ((AnnotationsTableModel) this.annotationsTable.getModel()).deleteItem(selectedRow);
                this.updateUI();
            }
        }
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        // Generated using JFormDesigner Evaluation license - FooBar
        scrollPane1 = new JBScrollPane();
        annotationsTable = new JBTable(new AnnotationsTableModel());

        //======== this ========
        setBorder(new javax.swing.border.CompoundBorder(new javax.swing.border.TitledBorder(new javax.
        swing.border.EmptyBorder(0,0,0,0), "JF\u006frmD\u0065sig\u006eer \u0045val\u0075ati\u006fn",javax.swing.border
        .TitledBorder.CENTER,javax.swing.border.TitledBorder.BOTTOM,new java.awt.Font("Dia\u006cog"
        ,java.awt.Font.BOLD,12),java.awt.Color.red), getBorder
        ())); addPropertyChangeListener(new java.beans.PropertyChangeListener(){@Override public void propertyChange(java
        .beans.PropertyChangeEvent e){if("\u0062ord\u0065r".equals(e.getPropertyName()))throw new RuntimeException
        ();}});
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        //======== scrollPane1 ========
        {

            //---- annotationsTable ----
            annotationsTable.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    annotationsTableKeyReleased(e);
                }
            });
            scrollPane1.setViewportView(annotationsTable);
        }
        add(scrollPane1);
    // JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
    // Generated using JFormDesigner Evaluation license - FooBar
    private JBScrollPane scrollPane1;
    private JBTable annotationsTable;
  // JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
