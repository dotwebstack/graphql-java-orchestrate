package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.util.TreeTransformerUtil.changeNode;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapRequest;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapSchema;

import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;
import graphql.util.TraversalControl;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;

public class RenameTypes extends AbstractTransform {

  private final TypeRenamer renamer;

  private final Map<String, String> nameMapping = new HashMap<>();

  private GraphQLSchema originalSchema;

  public RenameTypes(@NonNull TypeRenamer renamer) {
    this.renamer = renamer;
  }

  @Override
  public GraphQLSchema transformSchema(@NonNull GraphQLSchema originalSchema) {
    this.originalSchema = originalSchema;

    return mapSchema(originalSchema, SchemaMapping.newSchemaMapping()
        .objectType((objectType, context) -> changeNode(context,
            objectType.transform(builder -> builder.name(renameType(objectType)))))
        .interfaceType((interfaceType, context) -> changeNode(context,
            interfaceType.transform(builder -> builder.name(renameType(interfaceType)))))
        .build());
  }

  @Override
  public CompletableFuture<Result> transform(@NonNull Request originalRequest,
      @NonNull Function<Request, CompletableFuture<Result>> next) {
    var transformedRequest = transformRequest(originalRequest);
    return next.apply(transformedRequest);
  }

  private Request transformRequest(@NonNull Request originalRequest) {
    return mapRequest(originalRequest, originalSchema, RequestMapping.newRequestMapping()
        .field(environment -> {
          var field = environment.getField();
          var fieldType = environment.getFieldDefinition()
              .getType();

          if (fieldType instanceof GraphQLFieldsContainer) {
            var newSelectionSet = transformSelectionSet(field.getSelectionSet());

            return changeNode(environment.getTraverserContext(),
                field.transform(builder -> builder.selectionSet(newSelectionSet)));
          }

          return TraversalControl.CONTINUE;
        })
        .build());
  }

  private String renameType(GraphQLNamedType type) {
    var newName = renamer.apply(type.getName(), type);

    if (newName.equals(type.getName())) {
      return type.getName();
    }

    nameMapping.put(newName, type.getName());

    return newName;
  }

  private SelectionSet transformSelectionSet(SelectionSet selectionSet) {
    var newSelections = selectionSet.getSelections()
        .stream()
        .map(this::transformSelection)
        .collect(Collectors.toList());

    return selectionSet.transform(builder -> builder.selections(newSelections));
  }

  private Selection<?> transformSelection(Selection<?> selection) {
    if (selection instanceof InlineFragment) {
      return transformInlineFragment((InlineFragment) selection);
    }

    return selection;
  }

  private InlineFragment transformInlineFragment(InlineFragment inlineFragment) {
    var typeName = inlineFragment.getTypeCondition()
        .getName();

    if (!nameMapping.containsKey(typeName)) {
      return inlineFragment;
    }

    return inlineFragment.transform(builder -> builder.typeCondition(TypeName.newTypeName(nameMapping.get(typeName))
        .build()));
  }
}
