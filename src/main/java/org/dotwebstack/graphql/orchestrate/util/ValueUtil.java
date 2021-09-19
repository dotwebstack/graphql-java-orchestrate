package org.dotwebstack.graphql.orchestrate.util;

import graphql.language.BooleanValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.ScalarValue;
import graphql.language.StringValue;
import java.math.BigDecimal;
import java.math.BigInteger;

public class ValueUtil {

  private ValueUtil() {}

  public static ScalarValue<?> scalarValueFrom(Object input) {
    if (input instanceof String) {
      return new StringValue((String) input);
    }

    if (input instanceof BigInteger) {
      return new IntValue((BigInteger) input);
    }

    if (input instanceof Integer) {
      return new IntValue(BigInteger.valueOf((Integer) input));
    }

    if (input instanceof Long) {
      return new IntValue(BigInteger.valueOf((Long) input));
    }

    if (input instanceof Short) {
      return new IntValue(BigInteger.valueOf((Short) input));
    }

    if (input instanceof BigDecimal) {
      return new FloatValue((BigDecimal) input);
    }

    if (input instanceof Float) {
      return new FloatValue(BigDecimal.valueOf((Float) input));
    }

    if (input instanceof Double) {
      return new FloatValue(BigDecimal.valueOf((Double) input));
    }

    if (input instanceof Boolean) {
      return new BooleanValue((Boolean) input);
    }

    throw new IllegalArgumentException("Could not map to scalar value.");
  }
}
