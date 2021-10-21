package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.analysis.QueryTransformer.newQueryTransformer;
import static graphql.schema.SchemaTransformer.transformSchema;
import static java.util.Collections.unmodifiableMap;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.language.Field;
import graphql.language.Node;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.dotwebstack.graphql.orchestrate.Request;

public class TransformUtils {

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

  public static <T> List<T> listMerge(List<T> list1, List<T> list2) {
    return Stream.concat(list1.stream(), list2.stream())
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

  @SuppressWarnings("rawtypes")
  public static List<String> getResultPath(TraverserContext<Node> context, Field field) {
    var parentFields = context.getParentNodes()
        .stream()
        .filter(Field.class::isInstance)
        .map(Field.class::cast)
        .map(Field::getResultKey)
        .collect(Collectors.toList());

    return listAppend(parentFields, field.getResultKey());
  }

  @SuppressWarnings("rawtypes")
  public static List<String> getResultPath(TraverserContext<Node> context, List<String> fieldPath) {
    var parentFields = context.getParentNodes()
        .stream()
        .filter(Field.class::isInstance)
        .map(Field.class::cast)
        .map(Field::getResultKey)
        .collect(Collectors.toList());

    return listMerge(parentFields, fieldPath);
  }

  public static Object getFieldValue(Object data, List<String> fieldPath) {
    var fieldPathSize = fieldPath.size();
    var fieldValue = getFieldValue(data, fieldPath.get(0));

    // Field (or parent) is absent or target field has been reached
    if (fieldValue == null || fieldPathSize == 1) {
      return fieldValue;
    }

    return getFieldValue(fieldValue, fieldPath.subList(1, fieldPathSize));
  }

  @SuppressWarnings("unchecked")
  public static Object getFieldValue(Object data, String fieldKey) {
    if (data instanceof List) {
      return (((List<Object>) data).stream()).map(item -> getFieldValue(item, fieldKey))
          .collect(Collectors.toList());
    }

    if (data instanceof Map) {
      return ((Map<String, Object>) data).get(fieldKey);
    }

    throw new TransformException("Unsupported field type.");
  }

  @SuppressWarnings("unchecked")
  public static Object putFieldValue(Object data, List<String> fieldPath, Object fieldValue) {
    var fieldPathSize = fieldPath.size();

    if (data instanceof List) {
      return (((List<Object>) data).stream()).map(item -> putFieldValue(item, fieldPath, fieldValue))
          .collect(Collectors.toList());
    }

    if (!(data instanceof Map)) {
      throw new TransformException("Unsupported field type.");
    }

    var fieldKey = fieldPath.get(0);
    var dataMap = ((Map<String, Object>) data);

    if (fieldPathSize == 1) {
      return putFieldValue(dataMap, fieldKey, fieldValue);
    }

    var nestedMap = dataMap.getOrDefault(fieldKey, Map.of());

    return putFieldValue(dataMap, fieldKey, putFieldValue(nestedMap, fieldPath.subList(1, fieldPathSize), fieldValue));
  }

  public static Map<String, Object> putFieldValue(Map<String, Object> data, String fieldKey, Object fieldValue) {
    var dataMap = new HashMap<>(data);
    dataMap.put(fieldKey, fieldValue);
    return unmodifiableMap(dataMap);
  }

  public static <T> T noopCombiner(T o1, T o2) {
    throw new TransformException("Combining should never happen, since streams must be processed sequentially.");
  }
}
