package edu.kit.kastel.wrappers;

public abstract class Displayable<T> {
  private final T item;

  public Displayable(T item) {
    this.item = item;
  }

  public T getWrappedValue() {
    return item;
  }

  @Override
  public abstract String toString();
}
