package org.dotwebstack.graphql.orchestrate.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GraphqlJavaOrchestrateException extends RuntimeException {

  static final long serialVersionUID = 1564735180022L;

  HttpStatus statusCode;

  public GraphqlJavaOrchestrateException(HttpStatus statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }
}
