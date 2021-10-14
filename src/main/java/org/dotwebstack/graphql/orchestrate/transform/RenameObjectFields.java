package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.util.TreeTransformerUtil.changeNode;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapRequest;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapSchema;

import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.util.TraversalControl;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;

public class RenameObjectFields extends AbstractTransform {

  private final ObjectFieldRenamer renamer;

  private final Map<String, Map<String, String>> nameMapping = new HashMap<>();

  private GraphQLSchema transformedSchema;

  public RenameObjectFields(@NonNull ObjectFieldRenamer renamer) {
    this.renamer = renamer;
  }

  @Override
  public GraphQLSchema transformSchema(@NonNull GraphQLSchema originalSchema) {
    transformedSchema = mapSchema(originalSchema, SchemaMapping.newSchemaMapping()
        .objectType((objectType, context) -> {
          var fieldDefinitions = objectType.getFieldDefinitions()
              .stream()
              .map(fieldDefinition -> transformField(objectType, fieldDefinition))
              .collect(Collectors.toList());

          return changeNode(context, objectType.transform(builder -> builder.replaceFields(fieldDefinitions)));
        })
        .build());

    return transformedSchema;
  }

  @Override
  public CompletableFuture<Result> transform(@NonNull Request originalRequest,
      @NonNull Function<Request, CompletableFuture<Result>> next) {
    var transformedRequest = transformRequest(originalRequest);
    return next.apply(transformedRequest);
  }

  private Request transformRequest(@NonNull Request request) {
    return mapRequest(request, transformedSchema, RequestMapping.newRequestMapping()
        .field(environment -> {
          var field = environment.getField();
          var fieldsContainer = environment.getFieldsContainer();

          return findMappedName(fieldsContainer, field)
              .map(fieldName -> changeNode(environment.getTraverserContext(),
                  field.transform(builder -> builder.name(fieldName)
                      .alias(field.getName()))))
              .orElse(TraversalControl.CONTINUE);
        })
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
