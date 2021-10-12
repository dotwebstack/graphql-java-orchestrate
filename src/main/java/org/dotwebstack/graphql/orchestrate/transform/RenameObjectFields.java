package org.dotwebstack.graphql.orchestrate.transform;

import graphql.analysis.QueryTransformer;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.language.Field;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.Request;

public class RenameObjectFields implements Transform {

  private final ObjectFieldRenamer renamer;

  private final Map<String, Map<String, String>> nameMapping = new HashMap<>();

  private GraphQLSchema transformedSchema;

  public RenameObjectFields(@NonNull ObjectFieldRenamer renamer) {
    this.renamer = renamer;
  }

  @Override
  public GraphQLSchema transformSchema(@NonNull GraphQLSchema originalSchema) {
    var typeVisitor = new GraphQLTypeVisitorStub() {
      @Override
      public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType,
          TraverserContext<GraphQLSchemaElement> context) {
        var fieldDefinitions = objectType.getFieldDefinitions()
            .stream()
            .map(fieldDefinition -> transformField(objectType, fieldDefinition))
            .collect(Collectors.toList());

        return changeNode(context, objectType.transform(builder -> builder.replaceFields(fieldDefinitions)));
      }
    };

    transformedSchema = SchemaTransformer.transformSchema(originalSchema, typeVisitor);

    return transformedSchema;
  }

  @Override
  public Request transformRequest(@NonNull Request request) {
    var queryTransformer = QueryTransformer.newQueryTransformer()
        .schema(transformedSchema)
        .root(request.getSelectionSet())
        .rootParentType(transformedSchema.getQueryType())
        .fragmentsByName(Map.of())
        .variables(Map.of())
        .build();

    var queryVisitor = new QueryVisitorStub() {
      @Override
      public TraversalControl visitFieldWithControl(QueryVisitorFieldEnvironment environment) {
        var field = environment.getField();

        return findMappedName(environment.getFieldsContainer(), field)
            .map(fieldName -> TreeTransformerUtil.changeNode(environment.getTraverserContext(),
                field.transform(builder -> builder.name(fieldName)
                    .alias(field.getName()))))
            .orElse(TraversalControl.CONTINUE);
      }
    };

    var newSelectionSet = (SelectionSet) queryTransformer.transform(queryVisitor);

    return request.transform(builder -> builder.selectionSet(newSelectionSet)
        .build());
  }

  private Optional<String> findMappedName(GraphQLFieldsContainer fieldsContainer, Field field) {
    return Optional.ofNullable(nameMapping.get(fieldsContainer.getName()))
        .flatMap(typeMapping -> Optional.ofNullable(typeMapping.get(field.getName())));
  }

  private GraphQLFieldDefinition transformField(GraphQLObjectType objectType, GraphQLFieldDefinition fieldDefinition) {
    var newName = renamer.apply(objectType.getName(), fieldDefinition.getName(), fieldDefinition);

    if (newName.equals(fieldDefinition.getName())) {
      return fieldDefinition;
    }

    nameMapping.putIfAbsent(objectType.getName(), new HashMap<>());
    nameMapping.get(objectType.getName())
        .put(newName, fieldDefinition.getName());

    return fieldDefinition.transform(builder -> builder.name(newName));
  }
}
