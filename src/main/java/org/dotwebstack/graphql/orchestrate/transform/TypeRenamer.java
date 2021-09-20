package org.dotwebstack.graphql.orchestrate.transform;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;

@FunctionalInterface
public interface TypeRenamer {

  String apply(String typeName, GraphQLNamedType typeDefinition);
}
