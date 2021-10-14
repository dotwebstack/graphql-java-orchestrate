package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.util.TraversalControl.CONTINUE;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.util.TraversalControl;
import java.util.function.Function;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder(builderMethodName = "newRequestMapping")
public class RequestMapping {

  @NonNull
  @Builder.Default
  private final Function<QueryVisitorFieldEnvironment, TraversalControl> field = environment -> CONTINUE;
}
