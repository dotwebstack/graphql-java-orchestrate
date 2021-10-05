package org.dotwebstack.graphql.orchestrate.wrap;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import org.dotwebstack.graphql.orchestrate.delegate.SimpleDelegator;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;

public class SchemaWrapper {

  private SchemaWrapper() {}

  public static GraphQLSchema wrap(Subschema subschema) {
    var originalSchema = subschema.getSchema();

    // Wrapped schema gets it own fresh code registry
    var codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry();

    // Attach delegate fetchers for all root fields
    originalSchema.getQueryType()
        .getFieldDefinitions()
        .forEach(fieldDefinition -> codeRegistryBuilder.dataFetcher(
            originalSchema.getQueryType(), fieldDefinition, createDataFetcher(subschema, fieldDefinition)));

    // Create new schema with fresh code registry
    var wrappedSchema = originalSchema.transform(builder ->
        builder.codeRegistry(codeRegistryBuilder.build()));

    // Apply schema transforms
    for (var transform : subschema.getTransforms()) {
      wrappedSchema = transform.transformSchema(wrappedSchema);
    }

    return wrappedSchema;
  }

  private static DataFetcher<Object> createDataFetcher(Subschema subschema, GraphQLFieldDefinition fieldDefinition) {
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
