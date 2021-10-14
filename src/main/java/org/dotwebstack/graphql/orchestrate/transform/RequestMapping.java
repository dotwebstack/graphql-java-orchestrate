package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.util.TraversalControl.CONTINUE;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.util.TraversalControl;
import java.util.function.Function;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(builderMethodName = "newRequestMapping")
public class RequestMapping {

  @Builder.Default
  private final Function<QueryVisitorFieldEnvironment, TraversalControl> field = environment -> CONTINUE;
}
