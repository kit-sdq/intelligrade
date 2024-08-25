/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.wrappers;

/**
 * A generic wrapper class to enable overriding the toString method.
 * Required because some UI classes are parameterized and automatically call toString.
 *
 * @param <T> The Type to be wrapped
 */
public abstract class Displayable<T> {
    private final T item;

    protected Displayable(T item) {
        this.item = item;
    }

    /**
     * Get the wrapped value.
     *
     * @return A reference to the wrapped value. Does not copy.
     */
    public T getWrappedValue() {
        return item;
    }

    @Override
    public abstract String toString();
}
