package org.umlg.sqlg.strategy.barrier;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.NotStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ReducingBarrierStep;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.optimization.CountStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.umlg.sqlg.step.barrier.SqlgNotStepBarrier;
import org.umlg.sqlg.structure.SqlgGraph;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 * Date: 2014/08/15
 */
public class SqlgNotStepStepStrategy<S> extends AbstractTraversalStrategy<TraversalStrategy.OptimizationStrategy> implements TraversalStrategy.OptimizationStrategy {

    public SqlgNotStepStepStrategy() {
        super();
    }

    @Override
    public void apply(final Traversal.Admin<?, ?> traversal) {
        //Only optimize SqlgGraph. StarGraph also passes through here.
        //noinspection OptionalGetWithoutIsPresent
        if (!(traversal.getGraph().get() instanceof SqlgGraph)) {
            return;
        }
        List<NotStep> notSteps = TraversalHelper.getStepsOfAssignableClass(NotStep.class, traversal);
        for (NotStep<S> notStep : notSteps) {

            //reducing barrier steps like count does not work with Sqlg's barrier optimizations
            List<ReducingBarrierStep> reducingBarrierSteps = TraversalHelper.getStepsOfAssignableClassRecursively(ReducingBarrierStep.class, traversal);
            if (!reducingBarrierSteps.isEmpty()) {
                return;
            }

            SqlgNotStepBarrier<S> sqlgNotStepBarrier = new SqlgNotStepBarrier<>(traversal, notStep.getLocalChildren().get(0));
            for (String label : notStep.getLabels()) {
                sqlgNotStepBarrier.addLabel(label);
            }
            TraversalHelper.replaceStep(
                    notStep,
                    sqlgNotStepBarrier,
                    notStep.getTraversal()
            );
        }
    }

    @Override
    public Set<Class<? extends OptimizationStrategy>> applyPrior() {
        return Stream.of(
                //Inline must happen first as it sometimes removes the need for a TraversalFilterStep
                SqlgVertexStepStrategy.class,
                CountStrategy.class
        ).collect(Collectors.toSet());
    }

}
