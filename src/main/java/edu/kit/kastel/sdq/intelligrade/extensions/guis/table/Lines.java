/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis.table;

import java.util.List;
import java.util.stream.Collectors;

import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import org.jetbrains.annotations.NotNull;

public record Lines(List<LineLocation> locations) implements Comparable<Lines> {
    public static Lines fromLines(List<Lines> lines) {
        return new Lines(lines.stream().flatMap(l -> l.locations.stream()).toList());
    }

    public static Lines fromAnnotation(Annotation annotation) {
        return new Lines(List.of(LineLocation.fromAnnotation(annotation)));
    }

    @Override
    public String toString() {
        return locations.stream().map(LineLocation::toString).collect(Collectors.joining(", "));
    }

    @Override
    public int compareTo(@NotNull Lines other) {
        // make sure compareTo is working correctly with equals/hashCode
        if (this.equals(other)) {
            return 0;
        }

        // then sort both lines for the comparison of individual elements
        List<LineLocation> left = locations.stream().sorted().toList();
        List<LineLocation> right = other.locations.stream().sorted().toList();

        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int comparison = left.get(i).compareTo(right.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }

        // if the lists are equal up to the minimum size, the longer list is considered greater
        return Integer.compare(locations.size(), other.locations.size());
    }
}
