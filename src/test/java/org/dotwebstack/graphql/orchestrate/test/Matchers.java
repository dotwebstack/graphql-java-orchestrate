package org.dotwebstack.graphql.orchestrate.test;

import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.StringValue;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class Matchers {

  public static Matcher<Field> hasZeroArguments() {
    return new TypeSafeMatcher<>() {
      @Override
      protected boolean matchesSafely(Field field) {
        return field.getArguments()
            .isEmpty();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("field has zero arguments");
      }
    };
  }

  public static Matcher<Field> hasStringArgument(String name, String value) {
    return new TypeSafeMatcher<>() {
      @Override
      protected boolean matchesSafely(Field field) {
        return field.getArguments()
            .stream()
            .filter(arg -> name.equals(arg.getName()))
            .findFirst()
            .map(Argument::getValue)
            .filter(StringValue.class::isInstance)
            .map(StringValue.class::cast)
            .map(v -> value.equals(v.getValue()))
            .orElse(false);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("field has argument ")
            .appendValue(name)
            .appendText(" with value ")
            .appendValue(value);
      }
    };
  }
}
