package org.dotwebstack.graphql.orchestrate.transform;

import graphql.analysis.QueryTransformer;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.language.Field;
import graphql.language.InlineFragment;
import graphql.language.Selection;
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
import java.util.stream.Collectors;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.Request;

public class RenameObjectFields implements Transform {

  private final ObjectFieldRenamer renamer;

  private final Map<String, Map<String, String>> nameMapping = new HashMap<>();

  private GraphQLSchema originalSchema;

  public RenameObjectFields(@NonNull ObjectFieldRenamer renamer) {
    this.renamer = renamer;
  }

  @Override
  public GraphQLSchema transformSchema(@NonNull GraphQLSchema originalSchema) {
    this.originalSchema = originalSchema;

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

    return SchemaTransformer.transformSchema(originalSchema, typeVisitor);
  }

  @Override
  public Request transformRequest(@NonNull Request request) {
    var queryTransformer = QueryTransformer.newQueryTransformer()
        .schema(originalSchema)
        .root(request.getSelectionSet())
        .rootParentType(originalSchema.getQueryType())
        .fragmentsByName(Map.of())
        .variables(Map.of())
        .build();

    var queryVisitor = new QueryVisitorStub() {
      @Override
      public TraversalControl visitFieldWithControl(QueryVisitorFieldEnvironment environment) {
        var field = environment.getField();
        var fieldType = environment.getFieldDefinition()
            .getType();

        if (fieldType instanceof GraphQLFieldsContainer) {
          var newSelectionSet =
              transformSelectionSet(field.getSelectionSet(), ((GraphQLFieldsContainer) fieldType).getName());

          return TreeTransformerUtil.changeNode(environment.getTraverserContext(),
              field.transform(builder -> builder.selectionSet(newSelectionSet)));
        }

        return TraversalControl.CONTINUE;
      }
    };

    var newSelectionSet = (SelectionSet) queryTransformer.transform(queryVisitor);

    return request.transform(builder -> builder.selectionSet(newSelectionSet)
        .build());
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

  private Field transformField(Field field, String typeName) {
    var originalName = nameMapping.containsKey(typeName) ? nameMapping.get(typeName)
        .get(field.getName()) : null;

    if (originalName == null) {
      return field;
    }

    return field.transform(builder -> builder.name(originalName)
        .alias(field.getName()));
  }

  private SelectionSet transformSelectionSet(SelectionSet selectionSet, String typeName) {
    var newSelections = selectionSet.getSelections()
        .stream()
        .map(selection -> transformSelection(selection, typeName))
        .collect(Collectors.toList());

    return selectionSet.transform(builder -> builder.selections(newSelections));
  }

  private Selection<?> transformSelection(Selection<?> selection, String typeName) {
    if (selection instanceof Field) {
      return transformField((Field) selection, typeName);
    }

    if (selection instanceof InlineFragment) {
      return transformInlineFragment((InlineFragment) selection);
    }

    return selection;
  }

  private InlineFragment transformInlineFragment(InlineFragment inlineFragment) {
    var typeName = inlineFragment.getTypeCondition()
        .getName();

    return inlineFragment
        .transform(builder -> builder.selectionSet(transformSelectionSet(inlineFragment.getSelectionSet(), typeName)));
  }
}
