package org.umlg.sqlg.test.gremlincompile;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.test.BaseTest;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Date: 2015/10/07
 * Time: 7:28 PM
 */
public class TestBulkWithout extends BaseTest {

    @BeforeClass
    public static void beforeClass() throws ClassNotFoundException, IOException, PropertyVetoException {
        BaseTest.beforeClass();
        if (isPostgres()) {
            configuration.addProperty("distributed", true);
        }
    }

    @Test
    public void testBulkWithout() throws InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        if (this.sqlgGraph.getSqlDialect().supportsBatchMode()) {
            this.sqlgGraph.tx().normalBatchModeOn();
        }
        Vertex god = this.sqlgGraph.addVertex(T.label, "God");
        List<String> uuids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String uuid = UUID.randomUUID().toString();
            uuids.add(uuid);
            Vertex person = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", uuid);
            god.addEdge("creator", person);
        }
        this.sqlgGraph.tx().commit();
        stopWatch.stop();
        System.out.println(stopWatch.toString());
        stopWatch.reset();
        stopWatch.start();
        testBulkWithout_assert(this.sqlgGraph, uuids);
        if (this.sqlgGraph1 != null) {
            Thread.sleep(SLEEP_TIME);
            testBulkWithout_assert(this.sqlgGraph1, uuids);
        }
        stopWatch.stop();
        System.out.println(stopWatch.toString());
    }

    private void testBulkWithout_assert(SqlgGraph sqlgGraph, List<String> uuids) {
        List<Vertex> persons = sqlgGraph.traversal().V().hasLabel("God").out().has("idNumber", P.without(uuids.subList(0, 2).toArray())).toList();
        Assert.assertEquals(98, persons.size());
        persons = this.sqlgGraph.traversal().V().hasLabel("God").out().has("idNumber", P.without(uuids.toArray())).toList();
        Assert.assertEquals(0, persons.size());
    }

    @Test
    public void testBulkWithinMultipleHasContainers() throws InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Vertex god = this.sqlgGraph.addVertex(T.label, "God");
        Vertex person1 = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", 1, "name", "pete");
        god.addEdge("creator", person1);
        Vertex person2 = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", 2, "name", "pete");
        god.addEdge("creator", person2);
        Vertex person3 = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", 3, "name", "john");
        god.addEdge("creator", person3);
        Vertex person4 = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", 4, "name", "pete");
        god.addEdge("creator", person4);
        Vertex person5 = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", 5, "name", "pete");
        god.addEdge("creator", person5);
        Vertex person6 = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", 6, "name", "pete");
        god.addEdge("creator", person6);
        Vertex person7 = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", 7, "name", "pete");
        god.addEdge("creator", person7);
        Vertex person8 = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", 8, "name", "pete");
        god.addEdge("creator", person8);
        Vertex person9 = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", 9, "name", "pete");
        god.addEdge("creator", person9);
        Vertex person10 = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", 10, "name", "pete");
        god.addEdge("creator", person10);

        this.sqlgGraph.tx().commit();
        stopWatch.stop();
        System.out.println(stopWatch.toString());
        stopWatch.reset();
        stopWatch.start();
        testBulkWithinMultipleHasContainers_assert(this.sqlgGraph);
        if (this.sqlgGraph1 != null) {
            Thread.sleep(SLEEP_TIME);
            testBulkWithinMultipleHasContainers_assert(this.sqlgGraph1);
        }
        stopWatch.stop();
        System.out.println(stopWatch.toString());
    }

    private void testBulkWithinMultipleHasContainers_assert(SqlgGraph sqlgGraph) {
        List<Vertex> persons = sqlgGraph.traversal().V()
                .hasLabel("God")
                .out()
                .has("name", "pete")
                .has("idNumber", P.without(1,2,3))
                .toList();
        Assert.assertEquals(7, persons.size());
    }

    @Test
    public void testBulkWithinVertexCompileStep() throws InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        if (this.sqlgGraph.getSqlDialect().supportsBatchMode()) {
            this.sqlgGraph.tx().normalBatchModeOn();
        }
        Vertex god = this.sqlgGraph.addVertex(T.label, "God");
        List<String> uuids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String uuid = UUID.randomUUID().toString();
            uuids.add(uuid);
            Vertex person = this.sqlgGraph.addVertex(T.label, "Person", "idNumber", uuid);
            god.addEdge("creator", person);
        }
        this.sqlgGraph.tx().commit();
        stopWatch.stop();
        System.out.println(stopWatch.toString());
        stopWatch.reset();
        stopWatch.start();
        testBulkWithinVertexCompileStep_assert(this.sqlgGraph, god, uuids);
        if (this.sqlgGraph1 != null) {
            Thread.sleep(SLEEP_TIME);
            testBulkWithinVertexCompileStep_assert(this.sqlgGraph1, god, uuids);
        }
        stopWatch.stop();
        System.out.println(stopWatch.toString());

    }

    private void testBulkWithinVertexCompileStep_assert(SqlgGraph sqlgGraph, Vertex god, List<String> uuids) {
        List<Vertex> persons = sqlgGraph.traversal().V(god.id()).out().has("idNumber", P.without(uuids.subList(0, 2).toArray())).toList();
        Assert.assertEquals(98, persons.size());
        persons = this.sqlgGraph.traversal().V().hasLabel("God").out().has("idNumber", P.without(uuids.toArray())).toList();
        Assert.assertEquals(0, persons.size());
    }
}
