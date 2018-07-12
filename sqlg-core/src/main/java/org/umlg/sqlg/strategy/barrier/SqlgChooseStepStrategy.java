package org.umlg.sqlg.strategy.barrier;

import org.apache.tinkerpop.gremlin.process.computer.traversal.strategy.optimization.MessagePassingReductionStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.branch.ChooseStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.HasNextStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ReducingBarrierStep;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.optimization.*;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.umlg.sqlg.step.barrier.SqlgBranchStepBarrier;
import org.umlg.sqlg.step.barrier.SqlgChooseStepBarrier;
import org.umlg.sqlg.structure.SqlgGraph;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 * Date: 2014/08/15
 */
public class SqlgChooseStepStrategy<M, S, E> extends AbstractTraversalStrategy<TraversalStrategy.OptimizationStrategy> implements TraversalStrategy.OptimizationStrategy {

    public SqlgChooseStepStrategy() {
        super();
    }

    @Override
    public void apply(final Traversal.Admin<?, ?> traversal) {
        //Only optimize SqlgGraph. StarGraph also passes through here.
        //noinspection OptionalGetWithoutIsPresent
        if (!(traversal.getGraph().get() instanceof SqlgGraph)) {
            return;
        }
        List<ChooseStep> chooseSteps = TraversalHelper.getStepsOfAssignableClass(ChooseStep.class, traversal);
        for (ChooseStep<S, E, M> chooseStep : chooseSteps) {
            Traversal.Admin<S, M> predicateTraversal = chooseStep.getLocalChildren().get(0);

            //The predicate branch step is a local traversal.
            //As such if it contains a ReducingBarrierStep the SqlgBranchStepBarrier will not work.
            List<ReducingBarrierStep> reducingBarrierSteps = TraversalHelper.getStepsOfAssignableClass(ReducingBarrierStep.class, predicateTraversal);
            if (!reducingBarrierSteps.isEmpty()) {
                continue;
            }

            if (predicateTraversal.getSteps().get(predicateTraversal.getSteps().size() - 1) instanceof HasNextStep) {
                predicateTraversal.removeStep(predicateTraversal.getSteps().get(predicateTraversal.getSteps().size() - 1));
            }

            SqlgBranchStepBarrier sqlgBranchStepBarrier = new SqlgChooseStepBarrier<>(
                    traversal,
                    predicateTraversal
            );
            for (String label : chooseStep.getLabels()) {
                sqlgBranchStepBarrier.addLabel(label);
            }
            try {
                Field traversalOptionsField = chooseStep.getClass().getSuperclass().getDeclaredField("traversalOptions");
                traversalOptionsField.setAccessible(true);
                Map<M, List<Traversal.Admin<S, E>>> traversalOptions = (Map<M, List<Traversal.Admin<S, E>>>) traversalOptionsField.get(chooseStep);
                for (Map.Entry<M, List<Traversal.Admin<S, E>>> entry : traversalOptions.entrySet()) {
                    for (Traversal.Admin<S, E> admin : entry.getValue()) {
                        sqlgBranchStepBarrier.addGlobalChildOption(entry.getKey(), admin);
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            TraversalHelper.replaceStep(
                    chooseStep,
                    sqlgBranchStepBarrier,
                    chooseStep.getTraversal()
            );
        }
    }

    @Override
    public Set<Class<? extends OptimizationStrategy>> applyPost() {
        return Stream.of(
                MatchPredicateStrategy.class,
                RepeatUnrollStrategy.class,
                PathRetractionStrategy.class,
//                InlineFilterStrategy.class,
                MessagePassingReductionStrategy.class,
                IncidentToAdjacentStrategy.class
        ).collect(Collectors.toSet());
    }

    @Override
    public Set<Class<? extends OptimizationStrategy>> applyPrior() {
        return Stream.of(
                SqlgVertexStepStrategy.class
        ).collect(Collectors.toSet());
    }

}
