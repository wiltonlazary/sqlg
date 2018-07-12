package org.umlg.sqlg.strategy;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.branch.ChooseStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.branch.OptionalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.EdgeVertexStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.VertexStep;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.sql.parse.ReplacedStep;
import org.umlg.sqlg.sql.parse.ReplacedStepTree;
import org.umlg.sqlg.step.SqlgStep;
import org.umlg.sqlg.step.SqlgVertexStep;
import org.umlg.sqlg.structure.SqlgGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 *         Date: 2017/03/04
 */
public class VertexStrategy extends BaseStrategy {

    private static Logger logger = LoggerFactory.getLogger(VertexStrategy.class);

    private VertexStrategy(Traversal.Admin<?, ?> traversal) {
        super(traversal);
    }


    public static VertexStrategy from(Traversal.Admin<?, ?> traversal) {
        return new VertexStrategy(traversal);
    }

    public void apply() {
        //Only optimize graph step.
        if (!(this.traversal.getGraph().orElseThrow(IllegalStateException::new) instanceof SqlgGraph)) {
            return;
        }
        if (this.sqlgGraph.features().supportsBatchMode() && this.sqlgGraph.tx().isInNormalBatchMode()) {
            this.sqlgGraph.tx().flush();
        }
        if (this.canNotBeOptimized()) {
            logger.debug("gremlin not optimized due to path or tree step. " + this.traversal.toString() + "\nPath to gremlin:\n" + ExceptionUtils.getStackTrace(new Throwable()));
            return;
        }
        combineSteps();
    }

    void combineSteps() {
        List<Step<?, ?>> steps = new ArrayList(this.traversal.asAdmin().getSteps());
        ListIterator<Step<?, ?>> stepIterator = steps.listIterator();
        MutableInt pathCount = new MutableInt(0);
        while (stepIterator.hasNext()) {
            Step<?, ?> step = stepIterator.next();
            if (this.reset) {
                this.reset = false;
                this.sqlgStep = null;
            }
            if (isReplaceableStep(step.getClass())) {
                stepIterator.previous();
                boolean keepGoing = handleStep(stepIterator, pathCount);
                if (!keepGoing) {
                    break;
                }
            } else {
                //restart
                this.sqlgStep = null;
            }
        }
        if (this.currentTreeNodeNode != null) {
            ReplacedStepTree replacedStepTree = this.currentTreeNodeNode.getReplacedStepTree();
            replacedStepTree.maybeAddLabelToLeafNodes();
        }
    }

    /**
     * EdgeOtherVertexStep can not be optimized as the direction information is lost.
     *
     * @param stepIterator The steps to iterate. Unused for the VertexStrategy
     * @param step The current step.
     * @param pathCount The path count.
     * @return true if the optimization can continue else false.
     */
    @Override
    protected boolean doFirst(ListIterator<Step<?, ?>> stepIterator, Step<?, ?> step, MutableInt pathCount) {
        if (!(step instanceof VertexStep || step instanceof EdgeVertexStep || step instanceof ChooseStep ||
                step instanceof OptionalStep)) {
            return false;
        }
        if (step instanceof OptionalStep) {
            if (unoptimizableOptionalStep((OptionalStep<?>) step)) {
                return false;
            }
        }
        if (step instanceof ChooseStep) {
            if (unoptimizableChooseStep((ChooseStep<?, ?, ?>) step)) {
                return false;
            }
        }
        this.sqlgStep = constructSqlgStep(step);
        TraversalHelper.insertBeforeStep(this.sqlgStep, step, this.traversal);
        return true;
    }

    @Override
    protected SqlgStep constructSqlgStep(Step startStep) {
        SqlgVertexStep sqlgStep = new SqlgVertexStep(this.traversal);
        this.currentReplacedStep = ReplacedStep.from(this.sqlgGraph.getTopology());
        this.currentTreeNodeNode = sqlgStep.addReplacedStep(this.currentReplacedStep);
        return sqlgStep;
    }

    @Override
    protected boolean isReplaceableStep(Class<? extends Step> stepClass) {
        return CONSECUTIVE_STEPS_TO_REPLACE.contains(stepClass);
    }

    @Override
    protected void replaceStepInTraversal(Step stepToReplace, SqlgStep sqlgStep) {
        if (this.traversal.getSteps().contains(stepToReplace)) {
            TraversalHelper.replaceStep(stepToReplace, sqlgStep, this.traversal);
        } else {
            TraversalHelper.insertAfterStep(sqlgStep, stepToReplace.getPreviousStep(), this.traversal);
        }
    }
}
