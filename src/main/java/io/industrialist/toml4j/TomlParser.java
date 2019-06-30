package io.industrialist.toml4j;

import io.industrialist.toml4j.node.TomlArrayNode;
import io.industrialist.toml4j.node.TomlBooleanNode;
import io.industrialist.toml4j.node.TomlDateTimeNode;
import io.industrialist.toml4j.node.TomlFloatNode;
import io.industrialist.toml4j.node.TomlHashNode;
import io.industrialist.toml4j.node.TomlIntegerNode;
import io.industrialist.toml4j.node.TomlNode;
import io.industrialist.toml4j.node.TomlNodeType;
import io.industrialist.toml4j.node.TomlStringNode;
import io.industrialist.toml4j.node.TomlTableArrayNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TomlParser {
  private static final Matcher tableExpressionMatcher = Pattern
      .compile("\\[([\\w:.,?!@#]+(\\.\\[\\w:.,?!@#]+)*+([\\\"\\w:.,?!@#\\\"])*)]").matcher("");
  private static final Matcher arrayOfTablesExpressionMatcher =
      Pattern.compile("\\[\\[([\\w:.,?!@#]+(\\.\\[\\w:.,?!@#]+)*+([\\\"\\w:.,?!@#\\\"])*)]]")
          .matcher("");
  private static final Matcher valueExpressionMatcher =
      Pattern.compile("([^\\s][A-Za-z0-9_-]|[\\\"\\w:.,?!@#\\\"]+)\\s*=(.+)").matcher("");

  private static final Matcher stringValueMatcher = Pattern.compile("^\".*\"$").matcher("");
  private static final Matcher integerValueMatcher = Pattern.compile("^-?\\d+$").matcher("");
  private static final Matcher floatValueMatcher = Pattern.compile("^-?\\d+\\.\\d+?$").matcher("");
  private static final Matcher booleanValueMatcher = Pattern.compile("^(true|false)$").matcher("");
  private static final Matcher dateTimeValueMatcher =
      Pattern.compile(
          "^([\\+-]?\\d{4}(?!\\d{2}\\b))((-?)((0[1-9]|1[0-2])(\\3([12]\\d|0[1-9]|3[01]))?|W([0-4]\\d|5[0-2])(-?[1-7])?|(00[1-9]|0[1-9]\\d|[12]\\d{2}|3([0-5]\\d|6[1-6])))([T\\s]((([01]\\d|2[0-3])((:?)[0-5]\\d)?|24\\:?00)([\\.,]\\d+(?!:))?)?(\\17[0-5]\\d([\\.,]\\d+)?)?([zZ]|([\\+-])([01]\\d|2[0-3]):?([0-5]\\d)?)?)?)?$")
          .matcher("");
  private static final Matcher arrayValueMatcher = Pattern.compile("\\[(.*)\\]").matcher("");

  public TomlNode parse(String tomlString) throws IOException {
    BufferedReader reader = new BufferedReader(new StringReader(tomlString));
    try {
      return parse(reader);
    } finally {
      reader.close();
    }
  }

  public TomlNode parse(InputStream inputStream) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    return parse(reader);
  }

  private TomlNode parse(BufferedReader reader) throws IOException {
    TomlHashNode rootNode = new TomlHashNode();
    // TODO: Need TomlContainerNode
    TomlHashNode currentNode = rootNode;
    String line;
    while ((line = reader.readLine()) != null) {
      line = stripCommentAndWhitespace(line);
      if (line.equals("")) {
        continue;
      }

      if (tableExpressionMatcher.reset(line).matches()) {
        String keyGroupPath = tableExpressionMatcher.group(1);
        currentNode = rootNode;
        for (String keyGroup : keyGroupPath.split("\\.")) {
          TomlNode existingNode = currentNode.get(keyGroup);
          if (existingNode != null && !existingNode.isHash()) {
            throw new ParseException("Duplicate key found: " + keyGroupPath);
          }

          if (existingNode == null) {
            existingNode = new TomlHashNode();
            currentNode.put(keyGroup, existingNode);
          }

          currentNode = (TomlHashNode) existingNode;
        }
      } else if (arrayOfTablesExpressionMatcher.reset(line).matches()) {
        String keyGroupPath = arrayOfTablesExpressionMatcher.group(1);
        currentNode = rootNode;
        String[] keys = keyGroupPath.split("\\.");
        for (int i = 0; i < keys.length - 1; i++) {
          String keyGroup = keys[i];
          TomlNode existingNode = currentNode.get(keyGroup);
          if (existingNode != null && !existingNode.isArrayOfTables()) {
            throw new ParseException("Duplicate key found: " + keyGroupPath);
          }

          if (existingNode == null) {
            existingNode = new TomlTableArrayNode();
            currentNode.put(keyGroup, existingNode);
          }

          currentNode = (TomlHashNode) existingNode;
        }

        String finalKeyGroup = keys[keys.length - 1];
        TomlNode existingNode = currentNode.get(finalKeyGroup);
        if (existingNode != null && !existingNode.isArrayOfTables()) {
          throw new ParseException("Duplicate key found: " + keyGroupPath);
        }

        if (existingNode == null) {
          existingNode = new TomlTableArrayNode();
          currentNode.put(finalKeyGroup, existingNode);
        }

        TomlHashNode tableArrayNode = new TomlHashNode();
        ((TomlTableArrayNode) existingNode).add(tableArrayNode);

        currentNode = (TomlHashNode) tableArrayNode;
      } else if (valueExpressionMatcher.reset(line).matches()) {
        String key = valueExpressionMatcher.group(1);
        String value = valueExpressionMatcher.group(2).trim();

        if (currentNode.contains(key)) {
          throw new ParseException("Duplicate key found");
        }

        // Parse out whole multiline array
        if (value.startsWith("[") && !value.endsWith("]")) {
          value = parseMultilineArray(value, reader);
        }

        currentNode.put(key, parseValue(value));
      } else {
        throw new ParseException("Invalid line: " + line + " - Please check toml file.");
      }
    }

    return rootNode;
  }

  private String parseMultilineArray(String firstLine, BufferedReader reader) throws IOException {
    StringBuilder singleLineArrayBuilder = new StringBuilder(firstLine);
    boolean arrayEndingFound = false;
    String line;
    while ((line = reader.readLine()) != null) {
      line = stripCommentAndWhitespace(line);
      if (line.equals("")) {
        continue;
      }
      singleLineArrayBuilder.append(line);

      if (line.endsWith("]")) {
        arrayEndingFound = true;
        break;
      }
    }

    if (!arrayEndingFound) {
      throw new ParseException("Unclosed array");
    }

    return singleLineArrayBuilder.toString();
  }

  private TomlNode parseValue(String value) {
    if (stringValueMatcher.reset(value).matches()) {
      value = value.substring(1, value.length() - 1); // Remove quotes
      return TomlStringNode.valueOf(StringUtils.unescapeString(value));
    } else if (integerValueMatcher.reset(value).matches()) {
      return TomlIntegerNode.valueOf(Long.valueOf(value));
    } else if (booleanValueMatcher.reset(value).matches()) {
      return TomlBooleanNode.valueOf(Boolean.valueOf(value));
    } else if (floatValueMatcher.reset(value).matches()) {
      return TomlFloatNode.valueOf(Double.valueOf(value));
    } else if (dateTimeValueMatcher.reset(value).matches()) {
      return TomlDateTimeNode.valueOf(value);
    } else if (arrayValueMatcher.reset(value).matches()) {
      return parseArrayValue(value);
    } else {
      throw new ParseException("Invalid value: " + value);
    }
  }

  private TomlNode parseArrayValue(String value) {
    // Remove surrounding brackets '[' and ']'
    value = value.substring(1, value.length() - 1);
    TomlArrayNode arrayNode = new TomlArrayNode();
    if (value.matches(".*(?:\\]),.*")) { // Nested arrays
      // Split with lookbehind to keep brackets
      TomlNodeType arrayType = null;
      for (String nestedValue : value.split("(?<=(?:\\])),")) {
        TomlNode nestedArrayNode = parseValue(nestedValue.trim());
        if (arrayType == null) {
          arrayType = nestedArrayNode.getNodeType();
        }

        if (!nestedArrayNode.getNodeType().equals(arrayType)) {
          throw new ParseException("Cannot mix data types in an array");
        }

        arrayNode.add(nestedArrayNode);
      }
    } else {
      TomlNodeType arrayType = null;
      for (String arrayValue : value.split("\\,")) {
        TomlNode arrayValueNode = parseValue(arrayValue.trim());
        if (arrayType == null) {
          arrayType = arrayValueNode.getNodeType();
        }

        if (!arrayValueNode.getNodeType().equals(arrayType)) {
          throw new ParseException("Cannot mix data types in an array");
        }

        arrayNode.add(arrayValueNode);
      }
    }

    return arrayNode;
  }

  // TODO: A key could probably have a '#' too.
  private String stripCommentAndWhitespace(String line) {
    String temp = line.trim();
    boolean inKeyGroup = false;
    boolean inString = false;
    for (int i = 0; i < temp.length(); i++) {
      char ch = temp.charAt(i);
      if (ch == '\"' && !inString) {
        inString = true;
      } else if (inString && ch == '\"' && i > 0 && temp.charAt(i - 1) != '\\') {
        inString = false;
      } else if (ch == '[' && !inKeyGroup) {
        inKeyGroup = true;
      } else if (inKeyGroup && ch == ']') {
        inKeyGroup = false;
      }

      if (!inKeyGroup && !inString && ch == '#') {
        temp = temp.substring(0, i);
        break;
      }
    }

    return temp;
    // return commentMatcher.reset(line).replaceAll("").trim();
  }
}
