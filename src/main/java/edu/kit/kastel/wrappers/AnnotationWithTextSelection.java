/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.wrappers;

import java.util.Objects;

import com.intellij.openapi.editor.markup.RangeHighlighter;
import edu.kit.kastel.sdq.artemis4j.grading.Annotation;

public record AnnotationWithTextSelection(Annotation annotation, RangeHighlighter mistakeHighlighter) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnnotationWithTextSelection that = (AnnotationWithTextSelection) o;
        return Objects.equals(annotation, that.annotation);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(annotation);
    }
}
