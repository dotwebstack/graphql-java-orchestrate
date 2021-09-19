package org.dotwebstack.graphql.orchestrate.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import graphql.ExecutionInput;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class TestUtil {

  private TestUtil() {
  }

  public static OperationDefinition parseQuery(String query) {
    return Parser.parse(query)
        .getDefinitionsOfType(OperationDefinition.class)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public static GraphQLSchema loadSchema(String name) {
    return loadSchema(name, GraphQLCodeRegistry.newCodeRegistry().build());
  }

  public static GraphQLSchema loadSchema(String name, GraphQLCodeRegistry codeRegistry) {
    var schemaResource = Thread.currentThread()
        .getContextClassLoader()
        .getResource(name + ".graphql");

    if (schemaResource == null) {
      throw new IllegalStateException("Schema resource not found.");
    }

    TypeDefinitionRegistry typeDefinitionRegistry;

    try {
      typeDefinitionRegistry = new SchemaParser()
          .parse(schemaResource.openStream());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    var runtimeWiring = RuntimeWiring.newRuntimeWiring()
        .codeRegistry(codeRegistry)
        .build();

    return new SchemaGenerator()
        .makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
  }

  public static GraphQLFieldDefinition fieldDefinition(GraphQLSchema schema, String typeName, String fieldName) {
    var type = schema.getType(typeName);

    if (!(type instanceof GraphQLFieldsContainer)) {
      return null;
    }

    return ((GraphQLFieldsContainer) type)
        .getField(fieldName);
  }

  public static GraphQLOutputType fieldType(GraphQLSchema schema, String typeName, String fieldName) {
    return Optional.ofNullable(fieldDefinition(schema, typeName, fieldName))
        .map(GraphQLFieldDefinition::getType)
        .orElse(null);
  }

  public static List<GraphQLArgument> fieldArguments(GraphQLSchema schema, String typeName, String fieldName) {
    return Optional.ofNullable(fieldDefinition(schema, typeName, fieldName))
        .map(GraphQLFieldDefinition::getArguments)
        .orElse(null);
  }

  public static Field extractQueryField(ExecutionInput executionInput) {
    var document = Parser.parse(executionInput.getQuery());
    var operations = document.getDefinitionsOfType(OperationDefinition.class);

    assertThat(operations, hasSize(1));
    var queryOperation = operations.get(0);
    assertThat(queryOperation.getOperation(), equalTo(OperationDefinition.Operation.QUERY));

    var fields = queryOperation.getSelectionSet()
        .getSelectionsOfType(Field.class);

    assertThat(fields, hasSize(1));
    return fields.get(0);
  }
}
