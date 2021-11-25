package org.dotwebstack.graphql.orchestrate.transform;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;

@Getter
@Builder(builderMethodName = "newContext")
public class TransformContext {

  @NonNull
  private final Subschema subschema;
}
