package org.dotwebstack.graphql.orchestrate.transform;

import graphql.schema.GraphQLFieldDefinition;

@FunctionalInterface
public interface ObjectFieldRenamer {

  String apply(String typeName, String fieldName, GraphQLFieldDefinition fieldDefinition);
}
