package org.umlg.sqlg.strategy;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.HasStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.optimization.InlineFilterStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.umlg.sqlg.step.SqlgHasStep;
import org.umlg.sqlg.structure.SqlgGraph;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 * Date: 2014/08/15
 */
public class SqlgHasStepStrategy extends AbstractTraversalStrategy<TraversalStrategy.OptimizationStrategy> implements TraversalStrategy.OptimizationStrategy {

    public SqlgHasStepStrategy() {
        super();
    }

    @Override
    public void apply(final Traversal.Admin<?, ?> traversal) {
        //Only optimize SqlgGraph. StarGraph also passes through here.
        //noinspection OptionalGetWithoutIsPresent
        if (!(traversal.getGraph().get() instanceof SqlgGraph)) {
            return;
        }
        List<HasStep> hasSteps = TraversalHelper.getStepsOfAssignableClass(HasStep.class, traversal);
        for (HasStep<?> hasStep : hasSteps) {

            SqlgHasStep sqlgHasStep = new SqlgHasStep(
                    hasStep.getTraversal(),
                    hasStep.getHasContainers().toArray(new HasContainer[]{})
            );
            for (String label : hasStep.getLabels()) {
                sqlgHasStep.addLabel(label);
            }
            TraversalHelper.replaceStep(
                    hasStep,
                    sqlgHasStep,
                    hasStep.getTraversal()
            );
        }
    }

    @Override
    public Set<Class<? extends OptimizationStrategy>> applyPrior() {
        return Stream.of(
                //Inline must happen first as it sometimes removes the need for a TraversalFilterStep
                InlineFilterStrategy.class
        ).collect(Collectors.toSet());
    }

}
