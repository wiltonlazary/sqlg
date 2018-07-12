package org.umlg.sqlg.step.barrier;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.step.branch.LocalStep;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.process.traversal.util.FastNoSuchElementException;
import org.umlg.sqlg.step.SqlgAbstractStep;
import org.umlg.sqlg.structure.SqlgTraverser;

import java.util.*;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 *         Date: 2017/04/20
 */
public class SqlgLocalStepBarrier<S, E> extends SqlgAbstractStep<S, E> implements TraversalParent, Step<S, E> {

    private boolean first = true;
    private Traversal.Admin<S, E> localTraversal;
    private List<Traverser.Admin<E>> results = new ArrayList<>();
    private Iterator<Traverser.Admin<E>> resultIterator;

    public SqlgLocalStepBarrier(final Traversal.Admin traversal, LocalStep<S, E> localStep) {
        super(traversal);
        this.localTraversal = localStep.getLocalChildren().get(0);
    }

    @Override
    public List<Traversal.Admin<S, E>> getLocalChildren() {
        return Collections.singletonList(this.localTraversal);
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return this.localTraversal.getTraverserRequirements();
    }

    @Override
    protected Traverser.Admin<E> processNextStart() throws NoSuchElementException {
        if (this.first) {
            this.first = false;
            while (this.starts.hasNext()) {
                this.localTraversal.addStart(this.starts.next());
            }
            while (this.localTraversal.hasNext()) {
                this.results.add(this.localTraversal.nextTraverser());
            }
            this.results.sort((o1, o2) -> {
                SqlgTraverser x = (SqlgTraverser) o1;
                SqlgTraverser y = (SqlgTraverser) o2;
                return Long.compare(x.getStartElementIndex(), y.getStartElementIndex());
            });
            this.resultIterator = this.results.iterator();
        }
        if (this.resultIterator.hasNext()) {
            Traverser.Admin<E> traverser = this.resultIterator.next();
            return traverser;
        } else {
            throw FastNoSuchElementException.instance();
        }
    }

    @Override
    public void reset() {
        super.reset();
        this.first = true;
        this.results.clear();
        this.localTraversal.reset();
    }

    @Override
    public SqlgLocalStepBarrier<S, E> clone() {
        final SqlgLocalStepBarrier<S, E> clone = (SqlgLocalStepBarrier<S, E>) super.clone();
        clone.localTraversal = this.localTraversal.clone();
        return clone;
    }

    @Override
    public void setTraversal(final Traversal.Admin<?, ?> parentTraversal) {
        super.setTraversal(parentTraversal);
        this.integrateChild(this.localTraversal);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ this.localTraversal.hashCode();
    }
}
