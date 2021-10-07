package org.dotwebstack.graphql.orchestrate.transform;

import graphql.analysis.QueryTransformer;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedType;
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
import org.dotwebstack.graphql.orchestrate.Request;

public class RenameTypes implements Transform {

  private final TypeRenamer renamer;

  private final Map<String, String> nameMapping = new HashMap<>();

  private GraphQLSchema originalSchema;

  public RenameTypes(TypeRenamer renamer) {
    this.renamer = renamer;
  }

  @Override
  public GraphQLSchema transformSchema(GraphQLSchema schema) {
    originalSchema = schema;

    var typeVisitor = new GraphQLTypeVisitorStub() {
      @Override
      public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType type,
          TraverserContext<GraphQLSchemaElement> context) {
        return changeNode(context, type.transform(builder -> builder.name(renameType(type))));
      }

      @Override
      public TraversalControl visitGraphQLObjectType(GraphQLObjectType type,
          TraverserContext<GraphQLSchemaElement> context) {
        return changeNode(context, type.transform(builder -> builder.name(renameType(type))));
      }
    };

    return SchemaTransformer.transformSchema(schema, typeVisitor);
  }

  @Override
  public Request transformRequest(Request request) {
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

  private String renameType(GraphQLNamedType type) {
    var newName = renamer.apply(type.getName(), type);

    if (newName.equals(type.getName())) {
      return type.getName();
    }

    nameMapping.put(newName, type.getName());

    return newName;
  }

  private SelectionSet transformSelectionSet(SelectionSet selectionSet, String typeName) {
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
