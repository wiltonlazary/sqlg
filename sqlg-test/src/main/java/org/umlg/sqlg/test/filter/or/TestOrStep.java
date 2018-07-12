package org.umlg.sqlg.test.filter.or;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.DefaultGraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.umlg.sqlg.predicate.FullText;
import org.umlg.sqlg.structure.topology.IndexType;
import org.umlg.sqlg.structure.topology.VertexLabel;
import org.umlg.sqlg.test.BaseTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 * Date: 2017/10/30
 */
public class TestOrStep extends BaseTest {

    @Test
    public void testOrStepOptimizedWith3Ors() {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a1");
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a2");
        Vertex a3 = this.sqlgGraph.addVertex(T.label, "A", "name", "a3");
        this.sqlgGraph.addVertex(T.label, "A", "name", "a4");
        this.sqlgGraph.tx().commit();
        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("A")
                .or(
                        __.has("name", "a1"),
                        __.has("name", "a2"),
                        __.has("name", "a3")
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(3, vertices.size());
        Assert.assertTrue(vertices.contains(a1) && vertices.contains(a2) && vertices.contains(a3));
    }

    @Test
    public void testOrChained() {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a1","p1","v1");
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a2","p1","v1");
        this.sqlgGraph.addVertex(T.label, "A", "name", "a3","p1","v1");
        this.sqlgGraph.addVertex(T.label, "A", "name", "a4","p1","v1");
        this.sqlgGraph.tx().commit();
        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("A")
                .or(
                        __.has("name", "a1").has("p1","v1"),
                        __.has("name", "a2"),
                        __.has("name", "a3").has("p1","v2")
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(2, vertices.size());
        Assert.assertTrue(vertices.contains(a1) && vertices.contains(a2));
    }
    
    @Test
    public void testOrMissingProperty() {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a1","p1","v1");
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a2","p1","v1");
        Vertex a3 = this.sqlgGraph.addVertex(T.label, "A", "name", "a3","p1","v1");
        this.sqlgGraph.addVertex(T.label, "A", "name", "a4","p1","v1");
        this.sqlgGraph.tx().commit();
        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("A")
                .or(
                        __.has("name", "a1").has("p1","v1"),
                        __.has("name", "a2"),
                        __.has("name", "a3").has("p2","v2")
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(2, vertices.size());
        Assert.assertTrue(vertices.contains(a1) && vertices.contains(a2));
        
        traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("A")
                .or(
                        __.has("name", "a1").has("p1","v1"),
                        __.has("name", "a2"),
                        __.has("name", "a3").has("p2")
                );
        vertices = traversal.toList();
        //Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(2, vertices.size());
        Assert.assertTrue(vertices.contains(a1) && vertices.contains(a2));
        
        traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("A")
                .or(
                        __.has("name", "a1").has("p1","v1"),
                        __.has("name", "a2"),
                        __.has("name", "a3").hasNot("p2")
                );
        vertices = traversal.toList();
        //Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(3, vertices.size());
        Assert.assertTrue(vertices.contains(a1) && vertices.contains(a2) && vertices.contains(a3));
    }
    
    @Test
    public void testOrStepOptimized() {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a1");
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a2");
        Vertex a3 = this.sqlgGraph.addVertex(T.label, "A", "name", "a3");
        this.sqlgGraph.tx().commit();
        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("A")
                .or(
                        __.has("name", "a1"),
                        __.has("name", "a2")
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(2, vertices.size());
        Assert.assertTrue(vertices.contains(a1) && vertices.contains(a2));
    }

    @Test
    public void testNestedOrStep() {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a1");
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a2");
        Vertex a3 = this.sqlgGraph.addVertex(T.label, "A", "name", "a3");
        Vertex a4 = this.sqlgGraph.addVertex(T.label, "A", "name", "a4");
        this.sqlgGraph.tx().commit();
        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("A")
                .or(
                        __.has("name", "a1"),
                        __.or(
                                __.has("name", "a2"),
                                __.has("name", "a3")
                        )
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(3, vertices.size());
        Assert.assertTrue(vertices.contains(a1) && vertices.contains(a2) && vertices.contains(a3));
    }

    //    //Unoptimized
    @Test
    public void testOrWithinPredicate() {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a1");
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a2");
        Vertex a3 = this.sqlgGraph.addVertex(T.label, "A", "name", "a3");
        Vertex a4 = this.sqlgGraph.addVertex(T.label, "A", "name", "a4");
        Vertex a5 = this.sqlgGraph.addVertex(T.label, "A", "name", "a5");
        Vertex a6 = this.sqlgGraph.addVertex(T.label, "A", "name", "a6");
        Vertex a7 = this.sqlgGraph.addVertex(T.label, "A", "name", "a7");
        this.sqlgGraph.tx().commit();
        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("A")
                .or(
                        __.has("name", P.within("a1", "a2", "a3", "a4", "a5")),
                        __.or(
                                __.has("name", "a6")
                        )
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(2, traversal.getSteps().size());
        Assert.assertEquals(6, vertices.size());
        Assert.assertTrue(vertices.contains(a1) && vertices.contains(a2) && vertices.contains(a3));

        //logic in BaseStrategy is different
        traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("A")
                .or(
                        __.has("name", "a6"),
                        __.or(
                                __.has("name", P.within("a1", "a2", "a3", "a4", "a5"))
                        )
                );
        vertices = traversal.toList();
        Assert.assertEquals(2, traversal.getSteps().size());
        Assert.assertEquals(6, vertices.size());
        Assert.assertTrue(vertices.contains(a1) && vertices.contains(a2) && vertices.contains(a3));
    }

    @Test
    public void testOrBetween() {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a1", "age", 1);
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a2", "age", 2);
        Vertex a3 = this.sqlgGraph.addVertex(T.label, "A", "name", "a3", "age", 3);
        Vertex a4 = this.sqlgGraph.addVertex(T.label, "A", "name", "a4", "age", 4);
        Vertex a5 = this.sqlgGraph.addVertex(T.label, "A", "name", "a5", "age", 5);
        Vertex a6 = this.sqlgGraph.addVertex(T.label, "A", "name", "a6", "age", 6);
        Vertex a7 = this.sqlgGraph.addVertex(T.label, "A", "name", "a7", "age", 7);
        this.sqlgGraph.tx().commit();

        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal().V()
                .hasLabel("A")
                .or(
                        __.has("age", P.between(2, 5)),
                        __.has("name", "a7")
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(4, vertices.size());
        Assert.assertTrue(vertices.containsAll(Arrays.asList(a2, a3, a4, a7)));
    }

    @Test
    public void testOrInside() {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a1", "age", 1);
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a2", "age", 2);
        Vertex a3 = this.sqlgGraph.addVertex(T.label, "A", "name", "a3", "age", 3);
        Vertex a4 = this.sqlgGraph.addVertex(T.label, "A", "name", "a4", "age", 4);
        Vertex a5 = this.sqlgGraph.addVertex(T.label, "A", "name", "a5", "age", 5);
        Vertex a6 = this.sqlgGraph.addVertex(T.label, "A", "name", "a6", "age", 6);
        Vertex a7 = this.sqlgGraph.addVertex(T.label, "A", "name", "a7", "age", 7);
        this.sqlgGraph.tx().commit();

        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal().V()
                .hasLabel("A")
                .or(
                        __.has("age", P.inside(2, 5)),
                        __.has("name", "a7")
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(3, vertices.size());
        Assert.assertTrue(vertices.containsAll(Arrays.asList(a3, a4, a7)));
    }

    @Test
    public void testOrOutside() {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a1", "age", 1);
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a2", "age", 2);
        Vertex a3 = this.sqlgGraph.addVertex(T.label, "A", "name", "a3", "age", 3);
        Vertex a4 = this.sqlgGraph.addVertex(T.label, "A", "name", "a4", "age", 4);
        Vertex a5 = this.sqlgGraph.addVertex(T.label, "A", "name", "a5", "age", 5);
        Vertex a6 = this.sqlgGraph.addVertex(T.label, "A", "name", "a6", "age", 6);
        Vertex a7 = this.sqlgGraph.addVertex(T.label, "A", "name", "a7", "age", 7);
        this.sqlgGraph.tx().commit();

        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal().V()
                .hasLabel("A")
                .or(
                        __.has("age", P.outside(2, 5)),
                        __.has("name", "a7")
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(3, vertices.size());
        Assert.assertTrue(vertices.containsAll(Arrays.asList(a1, a6, a7)));
    }

    @Test
    public void testOrFullText() {
        Assume.assumeTrue(isPostgres());
        Vertex v0 = this.sqlgGraph.addVertex(T.label, "Sentence", "name", "a fat cat sat on a mat and ate a fat rat");
        Vertex v1 = this.sqlgGraph.addVertex(T.label, "Sentence", "name", "fatal error");
        Vertex v2 = this.sqlgGraph.addVertex(T.label, "Sentence", "name", "error is not fatal");


        VertexLabel vl = this.sqlgGraph.getTopology().getVertexLabel("public", "Sentence").get();
        vl.ensureIndexExists(IndexType.getFullTextGIN("english"), Collections.singletonList(vl.getProperty("name").get()));
        this.sqlgGraph.tx().commit();

        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("Sentence")
                .or(
                        __.has("name", FullText.fullTextMatch("english", "fat & rat")),
                        __.has("name", "fatal error")
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(2, vertices.size());
        Assert.assertTrue(vertices.containsAll(Arrays.asList(v0, v1)));

        traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("Sentence")
                .or(
                        __.has("name", FullText.fullTextMatch("english", "fat & cow")),
                        __.has("name", "fatal error")
                );
        vertices = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(1, vertices.size());
        Assert.assertTrue(vertices.containsAll(Arrays.asList(v1)));
    }

    @Test
    public void testOrStepForVertexStep() {
        Vertex aa1 = this.sqlgGraph.addVertex(T.label, "AA", "name", "a1");
        Vertex aa2 = this.sqlgGraph.addVertex(T.label, "AA", "name", "a2");
        Vertex aa3 = this.sqlgGraph.addVertex(T.label, "AA", "name", "a3");
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a1");
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a2");
        Vertex a3 = this.sqlgGraph.addVertex(T.label, "A", "name", "a3");
        Vertex a4 = this.sqlgGraph.addVertex(T.label, "A", "name", "a4");
        aa1.addEdge("ab", a1);
        aa1.addEdge("ab", a2);
        aa1.addEdge("ab", a2);
        aa1.addEdge("ab", a2);
        this.sqlgGraph.tx().commit();
        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) this.sqlgGraph.traversal()
                .V().hasLabel("AA")
                .local(
                        __.out("ab")
                                .or(
                                        __.has("name", "a1"),
                                        __.or(
                                                __.has("name", "a2"),
                                                __.has("name", "a3")
                                        )
                                )
                );
        List<Vertex> vertices = traversal.toList();
        Assert.assertEquals(2, traversal.getSteps().size());
        Assert.assertEquals(4, vertices.size());
        Assert.assertTrue(vertices.contains(a1) && vertices.contains(a2));
        Assert.assertTrue(!vertices.contains(a3) || vertices.contains(a4));
    }
}
