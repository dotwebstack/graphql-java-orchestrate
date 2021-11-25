package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.analysis.QueryTransformer.newQueryTransformer;
import static graphql.schema.SchemaTransformer.transformSchema;
import static java.util.Collections.unmodifiableMap;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.com.google.common.collect.Lists;
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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.dotwebstack.graphql.orchestrate.Request;

public class TransformUtils {

  private TransformUtils() {}

  public static GraphQLSchema mapSchema(GraphQLSchema schema, SchemaMapping mapping) {
    var typeVisitor = new GraphQLTypeVisitorStub() {
      @Override
      public TraversalControl visitGraphQLObjectType(GraphQLObjectType node,
          TraverserContext<GraphQLSchemaElement> traverserContext) {
        return mapping.getObjectType()
            .apply(node, traverserContext);
      }

      @Override
      public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node,
          TraverserContext<GraphQLSchemaElement> traverserContext) {
        return mapping.getInterfaceType()
            .apply(node, traverserContext);
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
  public static List<String> getResultPath(TraverserContext<Node> traverserContext) {
    return Lists.reverse(traverserContext.getParentNodes())
        .stream()
        .filter(Field.class::isInstance)
        .map(Field.class::cast)
        .map(Field::getResultKey)
        .collect(Collectors.toList());
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

  public static Map<String, Object> putMapValue(Map<String, Object> inputMap, String key, Object value) {
    var dataMap = new HashMap<>(inputMap);
    dataMap.put(key, value);
    return unmodifiableMap(dataMap);
  }

  public static Object mapTransform(Object data, List<String> fieldPath, UnaryOperator<Map<String, Object>> mapper) {
    return mapApply(data, dataMap -> mapTransform(dataMap, fieldPath, mapper));
  }

  public static Map<String, Object> mapTransform(Map<String, Object> data, List<String> fieldPath,
      UnaryOperator<Map<String, Object>> mapper) {
    var fieldPathSize = fieldPath.size();
    var fieldKey = fieldPath.get(0);
    var fieldValue = data.get(fieldKey);

    // Recursively transform nested values, if final field has not been reached yet
    if (fieldPathSize > 1) {
      return putMapValue(data, fieldKey, mapTransform(fieldValue, fieldPath.subList(1, fieldPathSize), mapper));
    }

    return putMapValue(data, fieldKey, mapApply(fieldValue, mapper));
  }

  @SuppressWarnings("unchecked")
  public static Object mapApply(Object data, UnaryOperator<Map<String, Object>> mapper) {
    if (data instanceof List) {
      return ((List<Map<String, Object>>) data).stream()
          .map(mapper)
          .collect(Collectors.toList());
    }

    if (data instanceof Map) {
      return mapper.apply((Map<String, Object>) data);
    }

    throw new TransformException("Unsupported field type.");
  }

  public static <T> T noopCombiner(T o1, T o2) {
    throw new TransformException("Combining should never happen, since streams must be processed sequentially.");
  }
}
