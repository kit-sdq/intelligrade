/* Licensed under EPL-2.0 2025-2026. */
package edu.kit.kastel.sdq.intelligrade.widgets;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.intellij.openapi.diagnostic.Logger;
import net.miginfocom.swing.MigLayout;

/**
 * A layout manager that flexibly adjusts the number of columns to fit the available width of the container.
 * <br>
 * It tries to fit as many components in a row as possible. Components may shrink down to their minimum width, or
 * to the configured wrapped-component width, before the layout reduces the number of columns.
 */
public class FlowWrapLayout implements LayoutManager2 {
    private static final Logger LOG = Logger.getInstance(FlowWrapLayout.class);
    private final List<FixedLayout> layouts;
    private final SharedSizeGroups sharedSizeGroups;
    private final int minimumWrappedComponentWidth;
    private final boolean isDebug;

    public FlowWrapLayout(int maxColumns) {
        this(maxColumns, "");
    }

    public FlowWrapLayout(int maxColumns, String layoutConstraints) {
        this(maxColumns, layoutConstraints, false);
    }

    public FlowWrapLayout(int maxColumns, String layoutConstraints, SharedSizeGroups sharedSizeGroups) {
        this(maxColumns, layoutConstraints, sharedSizeGroups, Integer.MAX_VALUE, false);
    }

    public FlowWrapLayout(
            int maxColumns,
            String layoutConstraints,
            SharedSizeGroups sharedSizeGroups,
            int minimumWrappedComponentWidth) {
        this(maxColumns, layoutConstraints, sharedSizeGroups, minimumWrappedComponentWidth, false);
    }

    private FlowWrapLayout(int maxColumns, String layoutConstraints, boolean isDebug) {
        this(maxColumns, layoutConstraints, null, Integer.MAX_VALUE, isDebug);
    }

    private FlowWrapLayout(
            int maxColumns,
            String layoutConstraints,
            SharedSizeGroups sharedSizeGroups,
            int minimumWrappedComponentWidth,
            boolean isDebug) {
        this(
                IntStream.rangeClosed(1, maxColumns)
                        .mapToObj(i -> new MigConstraint(i, layoutConstraints, "", ""))
                        .toList(),
                sharedSizeGroups,
                minimumWrappedComponentWidth,
                isDebug);
    }

    public FlowWrapLayout(Collection<MigConstraint> layouts) {
        this(layouts, false);
    }

    public FlowWrapLayout(Collection<MigConstraint> layouts, boolean isDebug) {
        this(layouts, null, isDebug);
    }

    public FlowWrapLayout(Collection<MigConstraint> layouts, SharedSizeGroups sharedSizeGroups) {
        this(layouts, sharedSizeGroups, false);
    }

    public FlowWrapLayout(
            Collection<MigConstraint> layouts, SharedSizeGroups sharedSizeGroups, int minimumWrappedComponentWidth) {
        this(layouts, sharedSizeGroups, minimumWrappedComponentWidth, false);
    }

    public FlowWrapLayout(Collection<MigConstraint> layouts, SharedSizeGroups sharedSizeGroups, boolean isDebug) {
        this(layouts, sharedSizeGroups, Integer.MAX_VALUE, isDebug);
    }

    private FlowWrapLayout(
            Collection<MigConstraint> layouts,
            SharedSizeGroups sharedSizeGroups,
            int minimumWrappedComponentWidth,
            boolean isDebug) {
        if (layouts.isEmpty()) {
            throw new IllegalArgumentException("Layouts list cannot be empty");
        }

        if (minimumWrappedComponentWidth <= 0) {
            throw new IllegalArgumentException("minimum wrapped component width must be greater than 0");
        }

        this.isDebug = isDebug;
        this.sharedSizeGroups = sharedSizeGroups;
        this.minimumWrappedComponentWidth = minimumWrappedComponentWidth;
        this.layouts = new ArrayList<>(layouts.size());

        for (MigConstraint constraint : layouts) {
            var layoutConstraints = "wrap %d".formatted(constraint.columns());
            if (!constraint.layoutConstraints().isBlank()) {
                layoutConstraints += ", " + constraint.layoutConstraints();
            }

            var columnConstraints = constraint.columnConstraints();
            if (columnConstraints.isBlank()) {
                columnConstraints = "[0::,grow]".repeat(constraint.columns());
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
        registerSharedSizeGroup(comp, name);
        for (var fixedLayout : layouts) {
            fixedLayout.layout().addLayoutComponent(withShrinkableWidth(name), comp);
        }
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        registerSharedSizeGroup(comp, constraints);
        for (var layout : layouts) {
            layout.layout().addLayoutComponent(comp, withShrinkableWidth(constraints));
        }
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        if (this.sharedSizeGroups != null) {
            this.sharedSizeGroups.remove(comp);
        }

        for (var fixedLayout : layouts) {
            fixedLayout.layout().removeLayoutComponent(comp);
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        applySharedSizeGroups();
        // The preferred size is the layout with the most columns
        return this.findClosestLayout(Integer.MAX_VALUE).layout().preferredLayoutSize(parent);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        applySharedSizeGroups();
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
        applySharedSizeGroups();
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
        applySharedSizeGroups();
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

    public record VirtualLayout(List<VirtualRow> rows, int columns, int minimumWrappedComponentWidth) {
        public VirtualLayout(int columns) {
            this(columns, Integer.MAX_VALUE);
        }

        public VirtualLayout(int columns, int minimumWrappedComponentWidth) {
            this(new ArrayList<>(), columns, minimumWrappedComponentWidth);
        }

        public VirtualLayout {
            if (minimumWrappedComponentWidth <= 0) {
                throw new IllegalArgumentException("minimum wrapped component width must be greater than 0");
            }
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

            this.currentRow().add(component, this.minimumWrappedComponentWidth);
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

            private void add(Component component, int minimumWrappedComponentWidth) {
                this.preferredWidth += component.getPreferredSize().width;
                this.minWidth += effectiveMinimumWidth(component, minimumWrappedComponentWidth);
                this.componentCount += 1;
            }
        }

        private static int effectiveMinimumWidth(Component component, int minimumWrappedComponentWidth) {
            if (minimumWrappedComponentWidth == Integer.MAX_VALUE) {
                return component.getMinimumSize().width;
            }

            return minimumWrappedComponentWidth;
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
        // Each component has a minimum size and a preferred size. Some components, like wrapping text buttons,
        // also have a configured wrapped-component width that represents the smallest useful column width.
        //
        // The best layout...
        // - should have the most columns possible
        // - should allow components to shrink or wrap before reducing the number of columns
        // - while no row exceeds the maximum width of the container
        //
        // Without maximizing the number of columns, it would always choose a single column layout,
        // because that would maximize the number of components at their preferred size.
        //
        // This has O(len(layouts) * len(components)) complexity
        int availableComponents = Math.max(1, components.length);
        int targetLayout = this.layouts.stream()
                .filter(layout -> layout.columns() <= availableComponents)
                .map(targetColumnNumber -> {
                    var layout = new VirtualLayout(targetColumnNumber.columns(), this.minimumWrappedComponentWidth);
                    for (var component : components) {
                        layout.add(component);
                    }

                    return layout;
                })
                .filter(layout -> layout.fitsWithin(maxWidth))
                .max(Comparator.comparing(VirtualLayout::columns))
                .map(VirtualLayout::columns)
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
        applySharedSizeGroups();
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

    private void registerSharedSizeGroup(Component component, Object constraints) {
        if (this.sharedSizeGroups == null) {
            return;
        }

        String sizeGroupKey = extractSizeGroupKey(constraints);
        if (sizeGroupKey != null) {
            this.sharedSizeGroups.add(component, sizeGroupKey);
        }
    }

    private void applySharedSizeGroups() {
        if (this.sharedSizeGroups != null) {
            this.sharedSizeGroups.apply();
        }
    }

    private static Object withShrinkableWidth(Object constraints) {
        if (!(constraints instanceof String constraintText)) {
            return constraints;
        }

        return withShrinkableWidth(constraintText);
    }

    private static String withShrinkableWidth(String constraints) {
        if (constraints == null || constraints.isBlank()) {
            return "wmin 0";
        }

        if (Arrays.stream(constraints.split(","))
                .map(String::trim)
                .anyMatch(segment -> segment.startsWith("wmin ") || segment.startsWith("width "))) {
            return constraints;
        }

        return constraints + ", wmin 0";
    }

    private static String extractSizeGroupKey(Object constraints) {
        if (!(constraints instanceof String constraintText)) {
            return null;
        }

        for (String segment : constraintText.split(",")) {
            String[] tokens = segment.trim().split("\\s+");
            if (tokens.length >= 2 && "sizegroup".equals(tokens[0])) {
                return tokens[1];
            }
        }

        return null;
    }

    public static class SharedSizeGroups {
        private final Map<Component, SharedSizeGroupEntry> components = new WeakHashMap<>();

        private void add(Component component, String key) {
            this.components.computeIfAbsent(component, ignored -> SharedSizeGroupEntry.create(component)).key = key;
        }

        private void remove(Component component) {
            SharedSizeGroupEntry entry = this.components.remove(component);
            if (entry != null) {
                entry.restore(component);
            }
        }

        private void apply() {
            if (this.components.isEmpty()) {
                return;
            }

            Map<String, Dimension> preferredSizes = new HashMap<>();
            Map<String, Dimension> minimumSizes = new HashMap<>();
            for (var entry : this.components.entrySet()) {
                Component component = entry.getKey();
                SharedSizeGroupEntry sizeGroupEntry = entry.getValue();

                sizeGroupEntry.restore(component);
                expand(preferredSizes, sizeGroupEntry.key, component.getPreferredSize());
                expand(minimumSizes, sizeGroupEntry.key, component.getMinimumSize());
            }

            for (var entry : this.components.entrySet()) {
                Component component = entry.getKey();
                String key = entry.getValue().key;

                component.setPreferredSize(preferredSizes.get(key));
                component.setMinimumSize(minimumSizes.get(key));
            }
        }

        private static void expand(Map<String, Dimension> dimensions, String key, Dimension candidate) {
            dimensions.compute(key, (ignored, current) -> {
                if (current == null) {
                    return new Dimension(candidate);
                }

                current.width = Math.max(current.width, candidate.width);
                current.height = Math.max(current.height, candidate.height);
                return current;
            });
        }
    }

    private static class SharedSizeGroupEntry {
        private final boolean hadPreferredSize;
        private final Dimension preferredSize;
        private final boolean hadMinimumSize;
        private final Dimension minimumSize;

        private String key;

        private SharedSizeGroupEntry(
                boolean hadPreferredSize, Dimension preferredSize, boolean hadMinimumSize, Dimension minimumSize) {
            this.hadPreferredSize = hadPreferredSize;
            this.preferredSize = preferredSize;
            this.hadMinimumSize = hadMinimumSize;
            this.minimumSize = minimumSize;
        }

        private static SharedSizeGroupEntry create(Component component) {
            return new SharedSizeGroupEntry(
                    component.isPreferredSizeSet(),
                    component.isPreferredSizeSet() ? component.getPreferredSize() : null,
                    component.isMinimumSizeSet(),
                    component.isMinimumSizeSet() ? component.getMinimumSize() : null);
        }

        private void restore(Component component) {
            component.setPreferredSize(this.hadPreferredSize ? new Dimension(this.preferredSize) : null);
            component.setMinimumSize(this.hadMinimumSize ? new Dimension(this.minimumSize) : null);
        }
    }
}
