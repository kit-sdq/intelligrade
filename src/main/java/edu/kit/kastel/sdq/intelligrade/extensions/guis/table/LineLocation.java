/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis.table;

import edu.kit.kastel.sdq.artemis4j.grading.Annotation;
import org.jetbrains.annotations.NotNull;

public record LineLocation(int startLine, int endLine) implements Comparable<LineLocation> {
    public static LineLocation fromAnnotation(Annotation annotation) {
        return new LineLocation(annotation.getStartLine() + 1, annotation.getEndLine() + 1);
    }

    @Override
    public String toString() {
        return startLine == endLine ? "%d".formatted(startLine) : "%d - %s".formatted(startLine, endLine);
    }

    @Override
    public int compareTo(@NotNull LineLocation other) {
        // It is not necessary to override equals and hashCode, because
        // this is already done by the record keyword.
        if (this.equals(other)) {
            return 0;
        }

        if (startLine != other.startLine) {
            return Integer.compare(startLine, other.startLine);
        }

        return Integer.compare(endLine, other.endLine);
    }
}
