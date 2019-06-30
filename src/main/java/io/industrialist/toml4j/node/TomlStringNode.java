package io.industrialist.toml4j.node;

import io.industrialist.toml4j.StringUtils;

public class TomlStringNode extends TomlValueNode {
  private String value;

  public TomlStringNode(String value) {
    this.value = value;
  }

  public void setString(String replacement) {
    value = replacement;
  }

  public static TomlStringNode valueOf(String value) {
    return new TomlStringNode(value);
  }

  @Override
  public TomlNodeType getNodeType() {
    return TomlNodeType.STRING;
  }

  @Override
  public String stringValue() {
    return value;
  }

  @Override
  public String asStringValue() {
    return value;
  }

  @Override
  public String toString() {
    return new StringBuilder().append("\"").append(StringUtils.escapeString(value)).append("\"")
        .toString();
  }
}
