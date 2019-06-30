package io.industrialist.toml4j;

public class ParseException extends RuntimeException {
  private static final long serialVersionUID = 5781840390746662361L;

  public ParseException(String errorMessage) {
    super(errorMessage);
  }
}
