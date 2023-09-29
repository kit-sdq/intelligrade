package edu.kit.kastel.wrappers;

/**
 * represents anything that can react to a plugIn internal event.
 */
public interface EventListener extends java.util.EventListener {

  public void trigger();
}
