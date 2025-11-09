/* Licensed under EPL-2.0 2025. */
package edu.kit.kastel.sdq.intelligrade.widgets;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.intellij.openapi.diagnostic.Logger;
import net.miginfocom.swing.MigLayout;

/**
 * A layout manager that flexibly adjusts the number of columns to fit the available width of the container.
 * <br>
 * It will try to fit as many components in a row as possible, while ensuring that components are close to their
 * preferred size. If the components become too large, it will try to fit more components in the row to balance
 * the sizes.
 */
public class FlowWrapLayout implements LayoutManager2 {
    private static final Logger LOG = Logger.getInstance(FlowWrapLayout.class);
    private final List<FixedLayout> layouts;
    private final boolean isDebug;

    public FlowWrapLayout(int maxColumns) {
        this(maxColumns, false);
    }

    private FlowWrapLayout(int maxColumns, boolean isDebug) {
        this(IntStream.rangeClosed(1, maxColumns)
                .mapToObj(i -> new MigConstraint(i, "", "", ""))
                .toList());
    }

    public FlowWrapLayout(Collection<MigConstraint> layouts) {
        if (layouts.isEmpty()) {
            throw new IllegalArgumentException("Layouts list cannot be empty");
        }

        this.isDebug = false;
        this.layouts = new ArrayList<>(layouts.size());

        for (MigConstraint constraint : layouts) {
            var layoutConstraints = "wrap %d".formatted(constraint.columns());
            if (!constraint.layoutConstraints().isBlank()) {
                layoutConstraints += ", " + constraint.layoutConstraints();
            }

            var columnConstraints = constraint.columnConstraints();
            if (columnConstraints.isBlank()) {
                columnConstraints = "[grow]".repeat(constraint.columns());
            }

            this.layouts.add(new FixedLayout(
                    constraint.columns(),
                    new MigLayout(layoutConstraints, columnConstraints, constraint.rowConstraints())));
        }
    }

    private FixedLayout findClosestLayout(int columns) {
        var closestLayout = this.layouts.getFirst();
        for (var fixedLayout : this.layouts) {
            if (Math.abs(fixedLayout.columns() - columns) < Math.abs(closestLayout.columns() - columns)) {
                closestLayout = fixedLayout;
            }
        }

        return closestLayout;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // This is called with the component and the arguments that were supplied to add().
        // For example `growx` or `spanx 2`.
        for (var fixedLayout : layouts) {
            fixedLayout.layout().addLayoutComponent(name, comp);
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
        var minDim = this.currentLayout(parent).minimumLayoutSize(parent);
        var oneColumnDim = this.findClosestLayout(1).layout().minimumLayoutSize(parent);
        if (this.isDebug) {
            LOG.info("components: %d, maxWidth: %d"
                    .formatted(
                            parent.getComponentCount(),
                            parent.getWidth() - parent.getInsets().left - parent.getInsets().right));
            LOG.info("all layouts: [%s]"
                    .formatted(this.layouts.stream()
                            .map(fixedLayout -> "%d(width=%d, height=%d)"
                                    .formatted(
                                            fixedLayout.columns(),
                                            fixedLayout.layout().minimumLayoutSize(parent).width,
                                            fixedLayout.layout().minimumLayoutSize(parent).height))
                            .collect(Collectors.joining(",\n "))));
            LOG.info("current minimum layout size: (width=%d, height=%d), actual: (width=%d, height=%d)"
                    .formatted(minDim.width, minDim.height, oneColumnDim.width, oneColumnDim.height));
        }

        return new Dimension(oneColumnDim.width, minDim.height);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        var maxDim = this.currentLayout(target).maximumLayoutSize(target);
        var maxColumnsDim = this.findClosestLayout(Integer.MAX_VALUE).layout().maximumLayoutSize(target);

        return new Dimension(maxColumnsDim.width, maxDim.height);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        return this.currentLayout(target).getLayoutAlignmentX(target);
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return this.currentLayout(target).getLayoutAlignmentY(target);
    }

    private MigLayout currentLayout(Container parent) {
        var insets = parent.getInsets();
        var maxWidth = parent.getWidth() - insets.left - insets.right;
        var components = parent.getComponents();

        // This function might be called before the parent has a set width,
        // this might be the case during initial layout calculations, where the parent
        // wants to know how much space the layout needs.
        //
        // This layout tries to maximize the number of columns -> return the layout with the most columns
        if (maxWidth == 0) {
            return this.findClosestLayout(Integer.MAX_VALUE).layout();
        }

        int targetLayout = getTargetLayout(components, maxWidth);
        return this.findClosestLayout(targetLayout).layout();
    }

    public record VirtualLayout(List<VirtualRow> rows, int columns) {
        public VirtualLayout(int columns) {
            this(new ArrayList<>(), columns);
        }

        private VirtualRow currentRow() {
            if (this.rows.isEmpty()) {
                this.rows.add(new VirtualRow());
            }

            return this.rows.getLast();
        }

        public void add(Component component) {
            if (this.currentRow().componentCount == this.columns) {
                this.rows.add(new VirtualRow());
            }

            this.currentRow().add(component);
        }

        public boolean fitsWithin(int maxWidth) {
            for (var row : this.rows) {
                if (row.minWidth > maxWidth) {
                    return false;
                }
            }

            return true;
        }

        public int rowsAtPreferredWidth(int maxWidth) {
            return (int) this.rows.stream()
                    .filter(row -> row.preferredWidth <= maxWidth)
                    .count();
        }

        public int maxComponentInRow() {
            return this.rows.stream().mapToInt(row -> row.componentCount).max().orElse(1);
        }

        public static Comparator<VirtualLayout> comparator(int maxWidth) {
            return Comparator.comparing((VirtualLayout layout) -> layout.rowsAtPreferredWidth(maxWidth)
                            * 1.0
                            / layout.rows().size())
                    .thenComparing(VirtualLayout::maxComponentInRow);
        }

        public static class VirtualRow {
            private int preferredWidth;
            private int minWidth;
            private int componentCount;

            private VirtualRow() {
                this.preferredWidth = 0;
                this.minWidth = 0;
                this.componentCount = 0;
            }

            private void add(Component component) {
                this.preferredWidth += component.getPreferredSize().width;
                this.minWidth += component.getMinimumSize().width;
                this.componentCount += 1;
            }
        }
    }

    private int getTargetLayout(Component[] components, int maxWidth) {
        if (this.isDebug) {
            String text = Arrays.stream(components)
                    .map(component -> "(width: %d, min: %d, pref: %d)"
                            .formatted(
                                    component.getWidth(),
                                    component.getMinimumSize().width,
                                    component.getPreferredSize().width))
                    .collect(Collectors.joining(",\n"));

            LOG.info("[%s]".formatted(text));
        }

        // Choosing the best layout is a bit more involved, because there are many variables to consider.
        //
        // Each component has a minimum size and a preferred size.
        //
        // The best layout...
        // - should have the most columns possible
        // - where the number of components at their preferred size is maximized
        // - while no row exceeds the maximum width of the container
        //
        // Without maximizing the number of columns, it would always choose a single column layout,
        // because that would maximize the number of components at their preferred size.

        // This has O(len(layouts) * len(components)) complexity
        int targetLayout = this.layouts.stream()
                .map(FixedLayout::columns)
                .map(targetColumnNumber -> {
                    var layout = new VirtualLayout(targetColumnNumber);
                    for (var component : components) {
                        layout.add(component);
                    }

                    return layout;
                }) // Rows that overflow the max width can not be used
                .filter(layout -> layout.fitsWithin(maxWidth))
                .sorted(VirtualLayout.comparator(maxWidth).reversed())
                .map(VirtualLayout::maxComponentInRow)
                .findFirst()
                .orElse(1);

        if (this.isDebug) {
            LOG.info("Choosing layout with %d columns, to best fit in container width %d"
                    .formatted(targetLayout, maxWidth));
            LOG.info("-------------");
        }
        return targetLayout;
    }

    @Override
    public void layoutContainer(Container parent) {
        this.currentLayout(parent).layoutContainer(parent);
    }

    @Override
    public void invalidateLayout(Container target) {
        for (var layout : layouts) {
            layout.layout().invalidateLayout(target);
        }
    }

    private record FixedLayout(int columns, MigLayout layout) {
        private FixedLayout {
            if (columns <= 0) {
                throw new IllegalArgumentException("number of columns %d must be greater than 0".formatted(columns));
            }
        }
    }

    public record MigConstraint(
            int columns, String layoutConstraints, String columnConstraints, String rowConstraints) {
        public MigConstraint {
            if (columns <= 0) {
                throw new IllegalArgumentException("number of columns %d must be greater than 0".formatted(columns));
            }

            if (layoutConstraints.contains("wrap")) {
                throw new IllegalArgumentException(
                        "wrap is automatically added based on the specified number of columns");
            }
        }

        public MigConstraint(int columns, String layoutConstraints, String columnConstraints) {
            this(columns, layoutConstraints, columnConstraints, "");
        }

        public MigConstraint(int columns, String layoutConstraints) {
            this(columns, layoutConstraints, "");
        }

        public MigConstraint(int columns) {
            this(columns, "");
        }
    }
}
