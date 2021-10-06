package org.dotwebstack.graphql.orchestrate.delegate;

import graphql.schema.DataFetchingEnvironment;
import java.util.function.Function;

public interface KeyFromEnvFunction extends Function<DataFetchingEnvironment, Object> {
}
