package org.dotwebstack.graphql.orchestrate.delegate;

import graphql.language.Argument;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.function.Function;

public interface ArgsFromEnvFunction extends Function<DataFetchingEnvironment, List<Argument>> {}
