package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.util.TraversalControl.CONTINUE;

import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import java.util.function.BiFunction;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(builderMethodName = "newSchemaMapping")
public class SchemaMapping {

  @Builder.Default
  private final SchemaMappingFunction<GraphQLObjectType> objectType = (element, context) -> CONTINUE;

  @Builder.Default
  private final SchemaMappingFunction<GraphQLInterfaceType> interfaceType = (element, context) -> CONTINUE;

  public interface SchemaMappingFunction<T>
      extends BiFunction<T, TraverserContext<GraphQLSchemaElement>, TraversalControl> {
  }
}
