package org.dotwebstack.graphql.orchestrate.wrap;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import lombok.Builder;
import lombok.Getter;
import org.dotwebstack.graphql.orchestrate.delegate.SimpleDelegator;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;

@Builder
@Getter
public class SchemaWrapper {

  private final Subschema subschema;

  public GraphQLSchema wrap() {
    var originalSchema = subschema.getSchema();

    // Wrapped schema gets it own fresh code registry
    var codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry();

    // Attach delegate fetchers for all root fields
    originalSchema.getQueryType()
        .getFieldDefinitions()
        .forEach(fieldDefinition -> codeRegistryBuilder.dataFetcher(
            originalSchema.getQueryType(), fieldDefinition, createDataFetcher(fieldDefinition)));

    // Create new schema with fresh code registry
    var wrappedSchema = originalSchema.transform(builder ->
        builder.codeRegistry(codeRegistryBuilder.build()));

    // Apply schema transforms
    for (var transform : subschema.getTransforms()) {
      wrappedSchema = transform.transformSchema(wrappedSchema);
    }

    return wrappedSchema;
  }

  private DataFetcher<Object> createDataFetcher(GraphQLFieldDefinition fieldDefinition) {
    var delegator = SimpleDelegator.builder()
        .subschema(subschema)
        .fieldName(fieldDefinition.getName())
        .argsFromEnv(environment -> environment.getField()
            .getArguments())
        .build();

    return environment -> delegator.delegate(environment)
        .thenApply(data -> {
          var transformedData = data;

          for (var transform : subschema.getTransforms()) {
            transformedData = transform.transformResult(transformedData);
          }

          return transformedData;
        });
  }
}
