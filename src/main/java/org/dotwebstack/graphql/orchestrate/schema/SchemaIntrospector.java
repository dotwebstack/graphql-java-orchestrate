package org.dotwebstack.graphql.orchestrate.schema;

import static graphql.introspection.IntrospectionQuery.INTROSPECTION_QUERY;

import graphql.ExecutionInput;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Document;
import graphql.language.SDLDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.concurrent.CompletableFuture;

public class SchemaIntrospector {

  private SchemaIntrospector() {}

  public static CompletableFuture<TypeDefinitionRegistry> introspectSchema(Executor executor) {
    return executor.execute(ExecutionInput.newExecutionInput()
        .query(INTROSPECTION_QUERY)
        .build())
        .thenApply(new IntrospectionResultToSchema()::createSchemaDefinition)
        .thenApply(SchemaIntrospector::buildTypeDefinitionRegistry);
  }

  private static TypeDefinitionRegistry buildTypeDefinitionRegistry(Document document) {
    var typeDefinitionRegistry = new TypeDefinitionRegistry();

    document.getDefinitions()
        .stream()
        .filter(SDLDefinition.class::isInstance)
        .map(SDLDefinition.class::cast)
        .forEach(typeDefinitionRegistry::add);

    return typeDefinitionRegistry;
  }
}
