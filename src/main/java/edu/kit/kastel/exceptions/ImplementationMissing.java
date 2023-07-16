package edu.kit.kastel.exceptions;

/**
 * This exception should be thrown if e.g. an event triggers a method call that should never occur
 */
public class ImplementationMissing extends RuntimeException {

  public ImplementationMissing(String message) {
    super(message);
  }
}
