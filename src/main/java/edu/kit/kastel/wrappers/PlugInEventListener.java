/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.wrappers;

/**
 * represents anything that can react to a plugIn internal event.
 */
public interface PlugInEventListener extends java.util.EventListener {

    void trigger();
}
