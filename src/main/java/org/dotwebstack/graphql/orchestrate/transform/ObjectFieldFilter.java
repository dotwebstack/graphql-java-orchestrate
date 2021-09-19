package org.dotwebstack.graphql.orchestrate.transform;

import graphql.schema.GraphQLFieldDefinition;

@FunctionalInterface
public interface ObjectFieldFilter {

  boolean apply(String typeName, String fieldName, GraphQLFieldDefinition fieldDefinition);
}
