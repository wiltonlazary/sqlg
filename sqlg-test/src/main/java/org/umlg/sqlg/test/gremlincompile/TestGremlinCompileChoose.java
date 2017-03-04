package org.umlg.sqlg.test.gremlincompile;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.DefaultGraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalOptionParent;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Assert;
import org.junit.Test;
import org.umlg.sqlg.test.BaseTest;

import java.util.Arrays;

/**
 * Date: 2017/02/06
 * Time: 9:27 AM
 */
public class TestGremlinCompileChoose extends BaseTest {

    //not optimized
    @Test
    public void g_V_chooseXlabelX_optionXblah__outXknowsXX_optionXbleep__outXcreatedXX_optionXnone__identityX_name() {
        loadModern();
        DefaultGraphTraversal<Vertex, String> traversal = (DefaultGraphTraversal)this.sqlgGraph.traversal().V().choose(__.label())
                .option("blah", __.out("knows"))
                .option("bleep", __.out("created"))
                .option(TraversalOptionParent.Pick.none, __.identity()).values("name");

        Assert.assertEquals(3, traversal.getSteps().size());
        printTraversalForm(traversal);
        Assert.assertEquals(3, traversal.getSteps().size());
        checkResults(Arrays.asList("marko", "vadas", "peter", "josh", "lop", "ripple"), traversal);
    }
}
