package org.umlg.sqlg.test;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.DefaultGraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.RangeGlobalStep;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.out;
import static org.junit.Assert.*;


/**
 * Test range and limit, they should be implemented as the SQL level
 *
 * @author jpmoresmau
 */
public class TestRangeLimit extends BaseTest {

    /**
     * ensure once we've built the traversal, it contains a RangeGlobalStep
     *
     * @param g
     */
    private void ensureRangeGlobal(GraphTraversal<?, ?> g) {
        DefaultGraphTraversal<?, ?> dgt = (DefaultGraphTraversal<?, ?>) g;
        boolean found = false;
        for (Step<?, ?> s : dgt.getSteps()) {
            found |= (s instanceof RangeGlobalStep<?>);
        }
        assertTrue(found);
    }


    /**
     * once we've run the traversal, it shouldn't contain the RangeGlobalStep,
     * since it was changed into a Range on the ReplacedStep
     *
     * @param g
     */
    private void ensureNoRangeGlobal(GraphTraversal<?, ?> g) {
        DefaultGraphTraversal<?, ?> dgt = (DefaultGraphTraversal<?, ?>) g;
        for (Step<?, ?> s : dgt.getSteps()) {
            assertFalse(s instanceof RangeGlobalStep<?>);
        }
    }

    @Test
    public void testVWithLimit() {
        for (int i = 0; i < 100; i++) {
            this.sqlgGraph.addVertex(T.label, "A", "name", "a" + i);
            this.sqlgGraph.addVertex(T.label, "B", "name", "a" + i);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Vertex> g = this.sqlgGraph.traversal().V().range(1, 2);
        ensureRangeGlobal(g);
        assertEquals(1, g.toList().size());
        g = this.sqlgGraph.traversal().V().range(1, 2);
        ensureRangeGlobal(g);
        assertTrue(g.hasNext());
    }


    @Test
    public void testRangeOnVertexLabels() {
        for (int i = 0; i < 100; i++) {
            this.sqlgGraph.addVertex(T.label, "A", "name", "a" + i);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Object> g = this.sqlgGraph.traversal().V().hasLabel("A").order().by("name").range(1, 4).values("name");
        ensureRangeGlobal(g);
        int cnt = 0;
        Set<String> names = new HashSet<>();
        String previous = null;
        while (g.hasNext()) {
            String n = (String) g.next();
            names.add(n);
            if (previous != null) {
                assertTrue(previous.compareTo(n) < 0);
            }
            previous = n;
            cnt++;
        }
        ensureNoRangeGlobal(g);
        assertEquals(3, cnt);
        assertEquals(names.toString(), 3, names.size());
        assertTrue(names.toString(), names.contains("a1"));
        assertTrue(names.toString(), names.contains("a10"));
        assertTrue(names.toString(), names.contains("a11"));

    }

    @Test
    public void testRangeOnVertexLabelsCriteria() {
        for (int i = 0; i < 100; i++) {
            this.sqlgGraph.addVertex(T.label, "A", "name", "a" + i, "prop0", "value");
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Object> g = this.sqlgGraph.traversal().V().hasLabel("A").has("prop0", "value").order().by("name").range(1, 4).values("name");
        ensureRangeGlobal(g);
        int cnt = 0;
        Set<String> names = new HashSet<>();
        String previous = null;
        while (g.hasNext()) {
            String n = (String) g.next();
            names.add(n);
            if (previous != null) {
                assertTrue(previous.compareTo(n) < 0);
            }
            previous = n;
            cnt++;
        }
        ensureNoRangeGlobal(g);
        assertEquals(3, cnt);
        assertEquals(names.toString(), 3, names.size());
        assertTrue(names.toString(), names.contains("a1"));
        assertTrue(names.toString(), names.contains("a10"));
        assertTrue(names.toString(), names.contains("a11"));

    }

    @Test
    public void testRangeOnVertexLabelsNoOrder() {
        for (int i = 0; i < 20; i++) {
            this.sqlgGraph.addVertex(T.label, "A", "name", "a" + i);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Object> g = this.sqlgGraph.traversal().V().hasLabel("A").range(1, 4).values("name");
        ensureRangeGlobal(g);
        int cnt = 0;
        Set<String> names = new HashSet<>();
        while (g.hasNext()) {
            String n = (String) g.next();
            names.add(n);
            cnt++;
        }
        ensureNoRangeGlobal(g);
        assertEquals(3, cnt);
        assertEquals(names.toString(), 3, names.size());

    }

    @Test
    public void testLimitOnVertexLabels() {
        for (int i = 0; i < 20; i++) {
            this.sqlgGraph.addVertex(T.label, "A", "name", "a" + i);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Object> g = this.sqlgGraph.traversal().V().hasLabel("A").order().by("name").limit(3).values("name");
        ensureRangeGlobal(g);
        int cnt = 0;
        Set<String> names = new HashSet<>();
        String previous = null;
        while (g.hasNext()) {
            String n = (String) g.next();
            names.add(n);
            if (previous != null) {
                assertTrue(previous.compareTo(n) < 0);
            }
            previous = n;
            cnt++;
        }
        ensureNoRangeGlobal(g);
        assertEquals(3, cnt);
        assertEquals(names.toString(), 3, names.size());
        assertTrue(names.toString(), names.contains("a1"));
        assertTrue(names.toString(), names.contains("a10"));
        assertTrue(names.toString(), names.contains("a0"));

    }

    @Test
    public void testRangeOnEdgeLabels() {
        for (int i = 0; i < 20; i++) {
            Vertex a = this.sqlgGraph.addVertex(T.label, "A", "name", "a" + i);
            Vertex b = this.sqlgGraph.addVertex(T.label, "B", "name", "b" + i);
            a.addEdge("E", b, "name", "e" + i);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Edge, Object> g = this.sqlgGraph.traversal().E().hasLabel("E").order().by("name").range(1, 4).values("name");
        ensureRangeGlobal(g);
        int cnt = 0;
        Set<String> names = new HashSet<>();
        String previous = null;
        while (g.hasNext()) {
            String n = (String) g.next();
            names.add(n);
            if (previous != null) {
                assertTrue(previous.compareTo(n) < 0);
            }
            previous = n;
            cnt++;
        }
        ensureNoRangeGlobal(g);
        assertEquals(3, cnt);
        assertEquals(names.toString(), 3, names.size());
        assertTrue(names.toString(), names.contains("e1"));
        assertTrue(names.toString(), names.contains("e10"));
        assertTrue(names.toString(), names.contains("e11"));

    }

    @Test
    public void testRangeOnMultipleLabelsOrdered() {
        for (int i = 0; i < 20; i++) {
            Vertex a = this.sqlgGraph.addVertex(T.label, "A", "name", "a" + i);
            Vertex b = this.sqlgGraph.addVertex(T.label, "B", "name", "b" + i);
            a.addEdge("E", b, "name", "e" + i);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Object> g = this.sqlgGraph.traversal().V().hasLabel("A", "B").order().by("name").range(1, 4).values("name");
        ensureRangeGlobal(g);
        int cnt = 0;
        Set<String> names = new HashSet<>();
        String previous = null;
        while (g.hasNext()) {
            String n = (String) g.next();
            names.add(n);
            if (previous != null) {
                assertTrue(previous.compareTo(n) < 0);
            }
            previous = n;
            cnt++;
        }
        // order by on multiple labels is not done in SQL, so the range isn't
        ensureRangeGlobal(g);
        assertEquals(3, cnt);
        assertEquals(names.toString(), 3, names.size());
        assertTrue(names.toString(), names.contains("a1"));
        assertTrue(names.toString(), names.contains("a10"));
        assertTrue(names.toString(), names.contains("a11"));

    }

    @Test
    public void testRangeOnMultipleLabelsOffset() {
        for (int i = 0; i < 20; i++) {
            Vertex a = this.sqlgGraph.addVertex(T.label, "A", "name", "a" + i);
            Vertex b = this.sqlgGraph.addVertex(T.label, "B", "name", "b" + i);
            a.addEdge("E", b, "name", "e" + i);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Object> g = this.sqlgGraph.traversal().V().hasLabel("A", "B").range(1, 4).values("name");
        ensureRangeGlobal(g);
        int cnt = 0;
        Set<String> names = new HashSet<>();
        while (g.hasNext()) {
            String n = (String) g.next();
            names.add(n);
            cnt++;
        }
        // cannot have offset on different labels
        ensureRangeGlobal(g);
        assertEquals(3, cnt);
        assertEquals(names.toString(), 3, names.size());

    }

    @Test
    public void testRangeOnMultipleLabels() {
        for (int i = 0; i < 20; i++) {
            Vertex a = this.sqlgGraph.addVertex(T.label, "A", "name", "a" + i);
            Vertex b = this.sqlgGraph.addVertex(T.label, "B", "name", "b" + i);
            a.addEdge("E", b, "name", "e" + i);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Object> g = this.sqlgGraph.traversal().V().hasLabel("A", "B").limit(4).values("name");
        ensureRangeGlobal(g);
        int cnt = 0;
        Set<String> names = new HashSet<>();
        while (g.hasNext()) {
            String n = (String) g.next();
            names.add(n);
            cnt++;
        }
        // we still have to cut the union result
        ensureRangeGlobal(g);
        assertEquals(4, cnt);
        assertEquals(names.toString(), 4, names.size());

    }


    @Test
    public void testRangeOnEdgesOutput() {
        Vertex a = this.sqlgGraph.addVertex(T.label, "A", "name", "a0");

        for (int i = 0; i < 20; i++) {
            Vertex b = this.sqlgGraph.addVertex(T.label, "B", "name", "b" + i);
            a.addEdge("E", b, "name", "e" + i);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Object> g = this.sqlgGraph.traversal().V(a).out("E").order().by("name").range(1, 4).values("name");
        ensureRangeGlobal(g);
        int cnt = 0;
        Set<String> names = new HashSet<>();
        String previous = null;
        while (g.hasNext()) {
            String n = (String) g.next();
            names.add(n);
            if (previous != null) {
                assertTrue(previous.compareTo(n) < 0);
            }
            previous = n;
            cnt++;
        }
        ensureNoRangeGlobal(g);
        assertEquals(3, cnt);
        assertEquals(names.toString(), 3, names.size());
        assertTrue(names.toString(), names.contains("b1"));
        assertTrue(names.toString(), names.contains("b10"));
        assertTrue(names.toString(), names.contains("b11"));

    }

    @Test
    public void testRangeOut() {
        for (int i = 0; i < 100; i++) {
            Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "age", i);
            Vertex b1 = this.sqlgGraph.addVertex(T.label, "B", "age", i);
            Vertex c1 = this.sqlgGraph.addVertex(T.label, "C", "age", i);
            a1.addEdge("ab", b1);
            b1.addEdge("bc", c1);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Vertex> g = this.sqlgGraph.traversal().V().hasLabel("A").out().out().order().by("age").range(10, 20);
        ensureRangeGlobal(g);
        List<Vertex> vertexList = g.toList();
        ensureNoRangeGlobal(g);
        assertEquals(10, vertexList.size());
        for (Vertex v : vertexList) {
            assertEquals("C", v.label());
            int i = (Integer) v.property("age").value();
            assertTrue(i >= 10 && i < 20);
        }
    }


    @Test
    public void testRangeRepeatOut() {
        for (int i = 0; i < 100; i++) {
            Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "age", i);
            Vertex b1 = this.sqlgGraph.addVertex(T.label, "B", "age", i);
            Vertex c1 = this.sqlgGraph.addVertex(T.label, "C", "age", i);
            a1.addEdge("ab", b1);
            b1.addEdge("bc", c1);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Vertex> g = this.sqlgGraph.traversal().V().hasLabel("A").repeat(out()).times(2).order().by("age").range(10, 20);
        ensureRangeGlobal(g);
        List<Vertex> vertexList = g.toList();
        ensureNoRangeGlobal(g);
        assertEquals(10, vertexList.size());
        for (Vertex v : vertexList) {
            assertEquals("C", v.label());
            int i = (Integer) v.property("age").value();
            assertTrue(i >= 10 && i < 20);
        }
    }

    @Test
    public void testRangeBoth() {
        for (int i = 0; i < 100; i++) {
            Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "age", i);
            Vertex b1 = this.sqlgGraph.addVertex(T.label, "B", "age", i);
            Vertex c1 = this.sqlgGraph.addVertex(T.label, "C", "age", i);
            a1.addEdge("ab", b1);
            b1.addEdge("bc", c1);
        }
        this.sqlgGraph.tx().commit();
        GraphTraversal<Vertex, Vertex> g = this.sqlgGraph.traversal().V().hasLabel("B").both().order().by("age").range(10, 20);
        ensureRangeGlobal(g);
        List<Vertex> vertexList = g.toList();
        // cannot be done in SQL
        ensureRangeGlobal(g);
        assertEquals(10, vertexList.size());
        for (Vertex v : vertexList) {
            assertTrue(v.label().equals("A") || v.label().equals("C"));
            int i = (Integer) v.property("age").value();
            assertTrue(String.valueOf(i), i >= 5 && i < 10);
        }
    }
}
