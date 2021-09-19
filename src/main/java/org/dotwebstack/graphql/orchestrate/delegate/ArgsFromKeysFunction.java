package org.dotwebstack.graphql.orchestrate.delegate;

import graphql.language.Argument;
import java.util.List;
import java.util.function.Function;

public interface ArgsFromKeysFunction extends Function<List<Object>, List<Argument>> {}
