package org.umlg.sqlg.structure;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.TraverserGenerator;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 *         Date: 2017/05/01
 */
public class SqlgTraverserGenerator implements TraverserGenerator {

    private static final SqlgTraverserGenerator INSTANCE = new SqlgTraverserGenerator();
    private static final Set<TraverserRequirement> REQUIREMENTS = EnumSet.of(
            TraverserRequirement.BULK,
            TraverserRequirement.LABELED_PATH,
            TraverserRequirement.OBJECT,
            TraverserRequirement.PATH,
            TraverserRequirement.SACK,
            TraverserRequirement.SIDE_EFFECTS,
            TraverserRequirement.SINGLE_LOOP);

    private SqlgTraverserGenerator() {
    }

    public <S> Traverser.Admin<S> generate(final S start, final Step<S, ?> startStep, final long initialBulk, boolean endsWithSack, boolean requiresOneBulk) {
        return new SqlgTraverser<>(start, startStep, initialBulk, endsWithSack, requiresOneBulk);
    }

    @Override
    public <S> Traverser.Admin<S> generate(final S start, final Step<S, ?> startStep, final long initialBulk) {
        throw new IllegalStateException("SqlgTraverserGenerator.generate(final S start, final Step<S, ?> startStep, final long initialBulk) should not be called.");
    }

    @Override
    public Set<TraverserRequirement> getProvidedRequirements() {
        return REQUIREMENTS;
    }

    public static SqlgTraverserGenerator instance() {
        return INSTANCE;
    }
}
