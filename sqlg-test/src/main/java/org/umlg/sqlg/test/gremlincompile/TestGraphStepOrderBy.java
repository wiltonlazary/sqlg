package org.umlg.sqlg.test.gremlincompile;

import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.DefaultGraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.test.BaseTest;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by pieter on 2015/08/22.
 */
public class TestGraphStepOrderBy extends BaseTest {

    @BeforeClass
    public static void beforeClass() throws ClassNotFoundException, IOException, PropertyVetoException {
        BaseTest.beforeClass();
        if (configuration.getString("jdbc.url").contains("postgresql")) {
            configuration.addProperty("distributed", true);
        }
    }

    @Test
    public void testOrderByInSchemas() {
        Vertex a = this.sqlgGraph.addVertex(T.label, "A.A", "name", "a");
        Vertex b = this.sqlgGraph.addVertex(T.label, "A.A", "name", "b");
        Vertex c = this.sqlgGraph.addVertex(T.label, "A.A", "name", "c");
        this.sqlgGraph.tx().commit();
        List<Vertex> vertices = this.sqlgGraph.traversal().V().hasLabel("A.A").order().by("name", Order.decr).toList();
        Assert.assertEquals(c, vertices.get(0));
        Assert.assertEquals(b, vertices.get(1));
        Assert.assertEquals(a, vertices.get(2));
    }

    @Test
    public void testOrderBy() throws InterruptedException {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "a", "surname", "a");
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "a", "surname", "b");
        Vertex a3 = this.sqlgGraph.addVertex(T.label, "A", "name", "a", "surname", "c");
        Vertex b1 = this.sqlgGraph.addVertex(T.label, "A", "name", "b", "surname", "a");
        Vertex b2 = this.sqlgGraph.addVertex(T.label, "A", "name", "b", "surname", "b");
        Vertex b3 = this.sqlgGraph.addVertex(T.label, "A", "name", "b", "surname", "c");
        this.sqlgGraph.tx().commit();

        testOrderBy_assert(this.sqlgGraph, a1, a2, a3, b1, b2, b3);
        if (this.sqlgGraph1 != null) {
            Thread.sleep(SLEEP_TIME);
            testOrderBy_assert(this.sqlgGraph1, a1, a2, a3, b1, b2, b3);
        }
    }

    private void testOrderBy_assert(SqlgGraph sqlgGraph, Vertex a1, Vertex a2, Vertex a3, Vertex b1, Vertex b2, Vertex b3) {
        DefaultGraphTraversal<Vertex, Vertex> traversal = (DefaultGraphTraversal<Vertex, Vertex>) sqlgGraph.traversal().V().hasLabel("A")
                .order()
                .by("name", Order.incr).by("surname", Order.decr);
        Assert.assertEquals(3, traversal.getSteps().size());
        List<Vertex> result = traversal.toList();
        Assert.assertEquals(1, traversal.getSteps().size());
        Assert.assertEquals(6, result.size());
        Assert.assertEquals(a3, result.get(0));
        Assert.assertEquals(a2, result.get(1));
        Assert.assertEquals(a1, result.get(2));
        Assert.assertEquals(b3, result.get(3));
        Assert.assertEquals(b2, result.get(4));
        Assert.assertEquals(b1, result.get(5));
    }

    @Test
    public void testOrderBy2() throws InterruptedException {
        Vertex group = this.sqlgGraph.addVertex(T.label, "Group", "name", "MTN");
        Vertex network = this.sqlgGraph.addVertex(T.label, "Network", "name", "SouthAfrica");
        Vertex networkSoftwareVersion = this.sqlgGraph.addVertex(T.label, "NetworkSoftwareVersion", "name", "SouthAfricaHuawei");
        group.addEdge("groupNetwork", network);
        network.addEdge("networkNetworkSoftwareVersion", networkSoftwareVersion);
        Vertex networkNodeGroupBsc = this.sqlgGraph.addVertex(T.label, "NetworkNodeGroup", "name", "BSC");
        Vertex networkNodeGroupRnc = this.sqlgGraph.addVertex(T.label, "NetworkNodeGroup", "name", "RNC");
        networkSoftwareVersion.addEdge("networkSoftwareVersionNetworkNodeGroup", networkNodeGroupBsc);
        networkSoftwareVersion.addEdge("networkSoftwareVersionNetworkNodeGroup", networkNodeGroupRnc);
        Vertex bsc1 = this.sqlgGraph.addVertex(T.label, "NetworkNode", "name", "BSCA");
        Vertex bsc2 = this.sqlgGraph.addVertex(T.label, "NetworkNode", "name", "BSCB");
        Vertex bsc3 = this.sqlgGraph.addVertex(T.label, "NetworkNode", "name", "BSCC");
        Vertex bsc4 = this.sqlgGraph.addVertex(T.label, "NetworkNode", "name", "BSCD");
        Vertex rnc1 = this.sqlgGraph.addVertex(T.label, "NetworkNode", "name", "RNCA");
        Vertex rnc2 = this.sqlgGraph.addVertex(T.label, "NetworkNode", "name", "RNCB");
        Vertex rnc3 = this.sqlgGraph.addVertex(T.label, "NetworkNode", "name", "RNCC");
        Vertex rnc4 = this.sqlgGraph.addVertex(T.label, "NetworkNode", "name", "RNCD");
        networkNodeGroupBsc.addEdge("networkNodeGroupNetworkNode", bsc1);
        networkNodeGroupBsc.addEdge("networkNodeGroupNetworkNode", bsc2);
        networkNodeGroupBsc.addEdge("networkNodeGroupNetworkNode", bsc3);
        networkNodeGroupBsc.addEdge("networkNodeGroupNetworkNode", bsc4);
        networkNodeGroupRnc.addEdge("networkNodeGroupNetworkNode", rnc1);
        networkNodeGroupRnc.addEdge("networkNodeGroupNetworkNode", rnc2);
        networkNodeGroupRnc.addEdge("networkNodeGroupNetworkNode", rnc3);
        networkNodeGroupRnc.addEdge("networkNodeGroupNetworkNode", rnc4);
        this.sqlgGraph.tx().commit();

        testOrderBy2_assert(this.sqlgGraph);
        if (this.sqlgGraph1 != null) {
            Thread.sleep(SLEEP_TIME);
            testOrderBy2_assert(this.sqlgGraph1);
        }
    }

    private void testOrderBy2_assert(SqlgGraph sqlgGraph) {
        DefaultGraphTraversal<Vertex, Map<String, Vertex>> traversal = (DefaultGraphTraversal<Vertex, Map<String, Vertex>>) sqlgGraph.traversal().V()
                .hasLabel("Group").as("g")
                .out("groupNetwork").as("network")
                .out("networkNetworkSoftwareVersion").as("nsv")
                .out("networkSoftwareVersionNetworkNodeGroup").as("nng")
                .out("networkNodeGroupNetworkNode").as("nn")
                .<Vertex>select("g", "network", "nsv", "nng", "nn")
                .order()
                .by(__.select("g").by("name"), Order.incr)
                .by(__.select("network").by("name"), Order.incr)
                .by(__.select("nsv").by("name"), Order.incr)
                .by(__.select("nng").by("name"), Order.incr)
                .by(__.select("nn").by("name"), Order.decr);
        Assert.assertEquals(8, traversal.getSteps().size());
        List<Map<String, Vertex>> result = traversal.toList();
        Assert.assertEquals(3, traversal.getSteps().size());

        Assert.assertEquals(8, result.size());
        Map<String,Vertex> row1 = result.get(0);
        Assert.assertEquals("BSC", row1.get("nng").value("name"));
        Assert.assertEquals("BSCD", row1.get("nn").value("name"));
        Map<String,Vertex> row2 = result.get(1);
        Assert.assertEquals("BSC", row2.get("nng").value("name"));
        Assert.assertEquals("BSCC", row2.get("nn").value("name"));
        Map<String,Vertex> row3 = result.get(2);
        Assert.assertEquals("BSC", row3.get("nng").value("name"));
        Assert.assertEquals("BSCB", row3.get("nn").value("name"));
        Map<String,Vertex> row4 = result.get(3);
        Assert.assertEquals("BSC", row4.get("nng").value("name"));
        Assert.assertEquals("BSCA", row4.get("nn").value("name"));
        Map<String,Vertex> row5 = result.get(4);
        Assert.assertEquals("RNC", row5.get("nng").value("name"));
        Assert.assertEquals("RNCD", row5.get("nn").value("name"));
        Map<String,Vertex> row6 = result.get(5);
        Assert.assertEquals("RNC", row6.get("nng").value("name"));
        Assert.assertEquals("RNCC", row6.get("nn").value("name"));
        Map<String,Vertex> row7 = result.get(6);
        Assert.assertEquals("RNC", row7.get("nng").value("name"));
        Assert.assertEquals("RNCB", row7.get("nn").value("name"));
        Map<String,Vertex> row8 = result.get(7);
        Assert.assertEquals("RNC", row8.get("nng").value("name"));
        Assert.assertEquals("RNCA", row8.get("nn").value("name"));
    }

    @Test
    public void testOrderby3() throws InterruptedException {
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A", "name", "aa");
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A", "name", "ab");
        Vertex b1 = this.sqlgGraph.addVertex(T.label, "B", "name", "ba");
        Vertex b2 = this.sqlgGraph.addVertex(T.label, "B", "name", "bb");
        Vertex b3 = this.sqlgGraph.addVertex(T.label, "B", "name", "bc");
        Vertex b4 = this.sqlgGraph.addVertex(T.label, "B", "name", "bd");
        a1.addEdge("ab", b1);
        a1.addEdge("ab", b2);
        a2.addEdge("ab", b3);
        a2.addEdge("ab", b4);
        this.sqlgGraph.tx().commit();

        testOrderBy3_assert(this.sqlgGraph);
        if (this.sqlgGraph1 != null) {
            Thread.sleep(SLEEP_TIME);
            testOrderBy3_assert(this.sqlgGraph1);
        }
    }

    private void testOrderBy3_assert(SqlgGraph sqlgGraph) {
        DefaultGraphTraversal<Vertex, Map<String, Vertex>> traversal = (DefaultGraphTraversal<Vertex, Map<String, Vertex>>) sqlgGraph.traversal().V()
                .hasLabel("A").as("a")
                .out("ab").as("b")
                .<Vertex>select("a", "b")
                .order()
                .by(__.select("a").by("name"), Order.incr)
                .by(__.select("b").by("name"), Order.decr);
        Assert.assertEquals(5, traversal.getSteps().size());
        List<Map<String, Vertex>> result = traversal.toList();
        Assert.assertEquals(3, traversal.getSteps().size());

        Assert.assertEquals(4, result.size());
        Map<String, Vertex> map1 = result.get(0);
        Map<String, Vertex> map2 = result.get(1);
        Map<String, Vertex> map3 = result.get(2);
        Map<String, Vertex> map4 = result.get(3);
        Assert.assertEquals("aa", map1.get("a").value("name"));
        Assert.assertEquals("bb", map1.get("b").value("name"));
        Assert.assertEquals("aa", map2.get("a").value("name"));
        Assert.assertEquals("ba", map2.get("b").value("name"));
        Assert.assertEquals("ab", map3.get("a").value("name"));
        Assert.assertEquals("bd", map3.get("b").value("name"));
        Assert.assertEquals("ab", map4.get("a").value("name"));
        Assert.assertEquals("bc", map4.get("b").value("name"));
    }

}
