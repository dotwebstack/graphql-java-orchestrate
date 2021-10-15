package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.analysis.QueryTransformer.newQueryTransformer;
import static graphql.schema.SchemaTransformer.transformSchema;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.language.Field;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.dotwebstack.graphql.orchestrate.Request;

class TransformUtils {

  private TransformUtils() {}

  public static GraphQLSchema mapSchema(GraphQLSchema schema, SchemaMapping mapping) {
    var typeVisitor = new GraphQLTypeVisitorStub() {
      @Override
      public TraversalControl visitGraphQLObjectType(GraphQLObjectType node,
          TraverserContext<GraphQLSchemaElement> context) {
        return mapping.getObjectType()
            .apply(node, context);
      }

      @Override
      public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node,
          TraverserContext<GraphQLSchemaElement> context) {
        return mapping.getInterfaceType()
            .apply(node, context);
      }
    };

    return transformSchema(schema, typeVisitor);
  }

  public static Request mapRequest(Request request, GraphQLSchema schema, RequestMapping mapping) {
    var queryTransformer = newQueryTransformer().schema(schema)
        .root(request.getSelectionSet())
        .rootParentType(schema.getQueryType())
        .fragmentsByName(Map.of())
        .variables(Map.of())
        .build();

    var queryVisitor = new QueryVisitorStub() {
      @Override
      public TraversalControl visitFieldWithControl(QueryVisitorFieldEnvironment environment) {
        return mapping.getField()
            .apply(environment);
      }
    };

    var newSelectionSet = (SelectionSet) queryTransformer.transform(queryVisitor);

    return request.transform(builder -> builder.selectionSet(newSelectionSet)
        .build());
  }

  public static <T> List<T> listAppend(List<T> list, T element) {
    return Stream.concat(list.stream(), Stream.of(element))
        .collect(Collectors.toList());
  }

  public static boolean isField(Selection<?> selection, String fieldName) {
    return selection instanceof Field && fieldName.equals(((Field) selection).getName());
  }

  public static boolean containsField(SelectionSet selectionSet, String fieldName) {
    return selectionSet.getSelectionsOfType(Field.class)
        .stream()
        .anyMatch(field -> fieldName.equals(field.getName()));
  }

  public static SelectionSet includeFieldPath(SelectionSet selectionSet, List<String> fieldPath) {
    var fieldName = fieldPath.get(0);
    var fieldPathSize = fieldPath.size();

    @SuppressWarnings("rawtypes")
    List<Selection> selections;

    if (!containsField(selectionSet, fieldName)) {
      selections = listAppend(selectionSet.getSelections(), Field.newField(fieldName)
          .selectionSet(
              fieldPathSize > 1 ? includeFieldPath(new SelectionSet(List.of()), fieldPath.subList(1, fieldPath.size()))
                  : null)
          .build());
    } else {
      selections = selectionSet.getSelections()
          .stream()
          .map(selection -> {
            if (isField(selection, fieldName)) {
              var field = (Field) selection;

              // Final field has been reached
              if (fieldPathSize == 1) {
                return field;
              }

              return field.transform(builder -> builder
                  .selectionSet(includeFieldPath(field.getSelectionSet(), fieldPath.subList(1, fieldPathSize))));
            }

            return selection;
          })
          .collect(Collectors.toList());
    }

    return selectionSet.transform(builder -> builder.selections(selections));
  }

  public static SelectionSet excludeField(SelectionSet selectionSet, String fieldName) {
    var selections = selectionSet.getSelections()
        .stream()
        .filter(selection -> !isField(selection, fieldName))
        .collect(Collectors.toList());

    return selectionSet.transform(builder -> builder.selections(selections));
  }

  // public static List<Field> getResultPath(TraverserContext<Node> context, Field field) {
  // var parentFields = context.getParentNodes()
  // .stream()
  // .filter(Field.class::isInstance)
  // .map(Field.class::cast)
  // .collect(Collectors.toList());
  //
  // return listAppend(parentFields, field);
  // }
}
