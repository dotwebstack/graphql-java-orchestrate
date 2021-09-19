package org.dotwebstack.graphql.orchestrate.util;

import graphql.language.Field;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NodeUtil {

  private NodeUtil() {
  }

  public static List<? extends Selection> filterSelectionsByField(SelectionSet selectionSet, Predicate<Field> predicate) {
    return selectionSet.getSelections()
        .stream()
        .filter(selection -> !(selection instanceof Field) || predicate.test((Field) selection))
        .collect(Collectors.toList());
  }

  public static List<Field> filterFields(SelectionSet selectionSet, Predicate<Field> predicate) {
    return selectionSet.getSelectionsOfType(Field.class)
        .stream()
        .filter(predicate)
        .collect(Collectors.toList());
  }

  public static Optional<Field> findFieldByName(SelectionSet selectionSet, String fieldName) {
    return selectionSet.getSelectionsOfType(Field.class)
        .stream()
        .filter(field -> fieldName.equals(field.getName()))
        .findFirst();
  }
}
