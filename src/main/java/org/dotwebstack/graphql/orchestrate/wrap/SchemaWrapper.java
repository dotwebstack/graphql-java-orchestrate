package org.dotwebstack.graphql.orchestrate.wrap;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.delegate.SimpleDelegator;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;

public class SchemaWrapper {

  private SchemaWrapper() {}

  public static GraphQLSchema wrap(@NonNull Subschema subschema) {
    var originalSchema = subschema.getSchema();

    // Wrapped schema gets it own fresh code registry
    var codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry();

    // Attach delegate fetchers for all root fields
    originalSchema.getQueryType()
        .getFieldDefinitions()
        .forEach(fieldDefinition -> codeRegistryBuilder.dataFetcher(originalSchema.getQueryType(), fieldDefinition,
            createDataFetcher(subschema, fieldDefinition)));

    // Create new schema with fresh code registry
    var wrappedSchema = originalSchema.transform(builder -> builder.codeRegistry(codeRegistryBuilder.build()));

    if (subschema.getTransform() != null) {
      wrappedSchema = subschema.getTransform()
          .transformSchema(wrappedSchema);
    }

    return wrappedSchema;
  }

  private static DataFetcher<Object> createDataFetcher(Subschema subschema, GraphQLFieldDefinition fieldDefinition) {
    var delegator = SimpleDelegator.newDelegator()
        .subschema(subschema)
        .fieldName(fieldDefinition.getName())
        .argsFromEnv(environment -> environment.getField()
            .getArguments())
        .build();

    return delegator::delegate;
  }
}
