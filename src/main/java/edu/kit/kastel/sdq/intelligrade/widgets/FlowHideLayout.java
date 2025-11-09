/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.widgets;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.miginfocom.layout.AC;
import net.miginfocom.layout.ConstraintParser;
import net.miginfocom.layout.DimConstraint;
import net.miginfocom.swing.MigLayout;

/**
 * A layout manager that hides columns if there is not enough space to display all columns.
 * <p>
 * Consider this class experimental, there are some edge-cases that might not be handled correctly
 * like a "spanx 2" constraint will be considered as 1 column, even if it is actually spanning 2 columns.
 */
public class FlowHideLayout implements LayoutManager2 {
    private final List<PartialLayout> layouts;

    private static String layoutConstraintsWithWrap(String layoutConstraints, int columns) {
        String result = "hidemode 3, wrap %d".formatted(columns);
        if (!layoutConstraints.isBlank()) {
            result = result + ", " + layoutConstraints;
        }

        return result;
    }

    public FlowHideLayout(Collection<Integer> mandatoryColumns, String layoutConstraints, String columnConstraints) {
        if (layoutConstraints.contains("wrap")) {
            throw new IllegalArgumentException("wrap is automatically added based on the specified number of columns");
        }
        this.layouts = new ArrayList<>();

        var parsedColumnConstraints = ConstraintParser.parseColumnConstraints(columnConstraints);
        int numberOfMandatory = mandatoryColumns.size();
        int totalColumns = parsedColumnConstraints.getCount();

        for (int cols = numberOfMandatory; cols <= totalColumns; cols++) {
            var currentColumnConstraints = new AC();

            List<DimConstraint> constraints = new ArrayList<>(List.of(parsedColumnConstraints.getConstraints()));

            Set<Integer> ignoredColumns = new HashSet<>();
            // The number of columns to remove:
            int columnsToRemove = constraints.size() - cols;
            for (int i = constraints.size() - 1; i >= 0 && columnsToRemove > 0; i--) {
                if (!mandatoryColumns.contains(i)) {
                    constraints.remove(i);
                    ignoredColumns.add(i);
                    columnsToRemove--;
                }
            }

            if (columnsToRemove > 0) {
                break;
            }

            currentColumnConstraints.setConstraints(constraints.toArray(new DimConstraint[0]));

            this.layouts.add(new PartialLayout(
                    new MigLayout(
                            ConstraintParser.parseLayoutConstraint(layoutConstraintsWithWrap(layoutConstraints, cols)),
                            currentColumnConstraints),
                    totalColumns,
                    ignoredColumns));
        }
    }

    private PartialLayout findClosestLayout(int columns) {
        var closestLayout = this.layouts.getFirst();
        for (var layout : this.layouts) {
            if (Math.abs(layout.columns() - columns) < Math.abs(closestLayout.columns() - columns)) {
                closestLayout = layout;
            }
        }

        return closestLayout;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        for (var layout : layouts) {
            layout.layout().addLayoutComponent(name, comp);
        }
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        for (var layout : layouts) {
            layout.layout().addLayoutComponent(comp, constraints);
        }
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        for (var fixedLayout : layouts) {
            fixedLayout.layout().removeLayoutComponent(comp);
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        // The preferred size is the layout with the most columns
        return this.findClosestLayout(Integer.MAX_VALUE).layout().preferredLayoutSize(parent);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        var minDim = this.currentLayout(parent).layout().minimumLayoutSize(parent);
        var oneColumnDim = this.findClosestLayout(1).layout().minimumLayoutSize(parent);

        return new Dimension(oneColumnDim.width, minDim.height);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        var maxDim = this.currentLayout(target).layout().maximumLayoutSize(target);
        var maxColumnsDim = this.findClosestLayout(Integer.MAX_VALUE).layout().maximumLayoutSize(target);

        return new Dimension(maxColumnsDim.width, maxDim.height);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        return this.currentLayout(target).layout().getLayoutAlignmentX(target);
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return this.currentLayout(target).layout().getLayoutAlignmentY(target);
    }

    private PartialLayout currentLayout(Container parent) {
        var insets = parent.getInsets();
        var maxWidth = parent.getWidth() - insets.left - insets.right;
        var components = parent.getComponents();

        // This function might be called before the parent has a set width,
        // this might be the case during initial layout calculations, where the parent
        // wants to know how much space the layout needs.
        //
        // This layout tries to maximize the number of columns -> return the layout with the most columns
        if (maxWidth == 0) {
            return this.findClosestLayout(Integer.MAX_VALUE);
        }

        int targetLayout = getTargetLayout(components, maxWidth);
        return this.findClosestLayout(targetLayout);
    }

    private int getTargetLayout(Component[] components, int maxWidth) {
        // TODO: This is duplicate code from FlowWrapLayout, maybe refactor into a shared utility method

        return this.layouts.stream()
                .map(PartialLayout::columns)
                .map(targetColumnNumber -> {
                    var layout = new FlowWrapLayout.VirtualLayout(targetColumnNumber);
                    for (var component : components) {
                        layout.add(component);
                    }

                    return layout;
                }) // Rows that overflow the max width can not be used
                .filter(layout -> layout.fitsWithin(maxWidth))
                .sorted(FlowWrapLayout.VirtualLayout.comparator(maxWidth).reversed())
                .map(FlowWrapLayout.VirtualLayout::maxComponentInRow)
                .findFirst()
                .orElse(1);
    }

    @Override
    public void layoutContainer(Container parent) {
        // TODO: This is not optimal, because it will involve the "to be hidden" components in the layout choosing
        var partialLayout = this.currentLayout(parent);

        // This hides/shows components based on the current layout:
        int currentColumn = 0;
        for (var component : parent.getComponents()) {
            component.setVisible(!partialLayout.ignoredColumns().contains(currentColumn));

            currentColumn = (currentColumn + 1) % partialLayout.numberOfColumns();
        }

        partialLayout.layout().layoutContainer(parent);
    }

    @Override
    public void invalidateLayout(Container target) {
        for (var layout : layouts) {
            layout.layout().invalidateLayout(target);
        }
    }

    private record PartialLayout(MigLayout layout, int numberOfColumns, Set<Integer> ignoredColumns) {
        private int columns() {
            return switch (this.layout.getColumnConstraints()) {
                case AC ac -> ac.getCount();
                case String string -> ConstraintParser.parseColumnConstraints(string)
                        .getCount();
                default -> throw new IllegalStateException("Unexpected value: " + this.layout.getColumnConstraints());
            };
        }
    }
}
