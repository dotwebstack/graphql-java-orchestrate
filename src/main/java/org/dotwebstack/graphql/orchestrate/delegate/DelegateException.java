package org.dotwebstack.graphql.orchestrate.delegate;

import graphql.GraphQLError;
import java.util.List;
import lombok.Getter;

@Getter
public class DelegateException extends RuntimeException {

  private final List<GraphQLError> errors;

  public DelegateException(List<GraphQLError> errors) {
    super("An error occured while delegating request.");
    this.errors = errors;
  }
}
