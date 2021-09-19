package org.dotwebstack.graphql.orchestrate.delegate;

import graphql.schema.DataFetchingEnvironment;
import java.util.concurrent.CompletableFuture;

public interface Delegator {

  CompletableFuture<Object> delegate(DataFetchingEnvironment environment);
}
