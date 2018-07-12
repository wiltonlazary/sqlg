package org.umlg.sqlg.test.topology;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Assume;
import org.junit.Test;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.EdgeLabel;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.VertexLabel;
import org.umlg.sqlg.test.BaseTest;

/**
 * test deletion behavior in a specific scenarios
 *
 * @author JP Moresmau
 */
public class TestTopologyDeleteSpecific extends BaseTest {

    /**
     * this failed with a NPE because we lost the table definition we're working on
     *
     * @throws Exception
     */
    @Test
    public void testSchemaDelete() throws Exception {
        Assume.assumeTrue(this.sqlgGraph.getSqlDialect().supportsDistribution());
        String schema = "willDelete";
        Vertex v1 = sqlgGraph.addVertex(T.label, schema + ".t1", "name", "n1", "hello", "world");
        sqlgGraph.tx().commit();
        Configuration c = getConfigurationClone();
        c.setProperty(SqlgGraph.DISTRIBUTED, true);
        sqlgGraph = SqlgGraph.open(c);
        sqlgGraph.getTopology().getSchema(schema).ifPresent((Schema s) -> s.remove(false));
        sqlgGraph.tx().commit();

        v1 = sqlgGraph.addVertex(T.label, schema + ".t1", "name", "n1");
        Vertex v2 = sqlgGraph.addVertex(T.label, schema + ".t2", "name", "n2");
        Edge e1 = v1.addEdge("e1", v2);
        sqlgGraph.tx().commit();

        sqlgGraph.tx().normalBatchModeOn();
        v1.property("hello", "world");
        // this line was failing
        e1.property("hello", "world");

        sqlgGraph.tx().commit();

        assertEquals("world", e1.value("hello"));
    }

    /**
     * @see <a href="https://github.com/pietermartin/sqlg/issues/212">https://github.com/pietermartin/sqlg/issues/212</a>
     */
    @Test
    public void testRemoveAndAddInSameTransaction() {
        //remove it, it does not exist but duplicating work logic.
        Optional<EdgeLabel> aaEdgeLabelOpt = this.sqlgGraph.getTopology().getEdgeLabel(this.sqlgGraph.getSqlDialect().getPublicSchema(), "aa");
        if (aaEdgeLabelOpt.isPresent()) {
            aaEdgeLabelOpt.get().remove(false);
        }
        Optional<VertexLabel> aVertexLabelOpt = this.sqlgGraph.getTopology().getVertexLabel(this.sqlgGraph.getSqlDialect().getPublicSchema(), "A");
        if (aVertexLabelOpt.isPresent()) {
            aVertexLabelOpt.get().remove(false);
        }

        VertexLabel aVertexLabel = this.sqlgGraph.getTopology().ensureVertexLabelExist("A");
        aVertexLabel.ensureEdgeLabelExist("aa", aVertexLabel);
        this.sqlgGraph.tx().commit();

        aaEdgeLabelOpt = this.sqlgGraph.getTopology().getEdgeLabel(this.sqlgGraph.getSqlDialect().getPublicSchema(), "aa");
        if (aaEdgeLabelOpt.isPresent()) {
            aaEdgeLabelOpt.get().remove(false);
        }
        aVertexLabelOpt = this.sqlgGraph.getTopology().getVertexLabel(this.sqlgGraph.getSqlDialect().getPublicSchema(), "A");
        if (aVertexLabelOpt.isPresent()) {
            aVertexLabelOpt.get().remove(false);
        }

        aVertexLabel = this.sqlgGraph.getTopology().ensureVertexLabelExist("A");
        aVertexLabel.ensureEdgeLabelExist("aa", aVertexLabel);
        this.sqlgGraph.tx().commit();

        aaEdgeLabelOpt = this.sqlgGraph.getTopology().getEdgeLabel(this.sqlgGraph.getSqlDialect().getPublicSchema(), "aa");
        assertTrue(aaEdgeLabelOpt.isPresent());
        aVertexLabelOpt = this.sqlgGraph.getTopology().getVertexLabel(this.sqlgGraph.getSqlDialect().getPublicSchema(), "A");
        assertTrue(aVertexLabelOpt.isPresent());

    }
    
    @Test
    public void testRemoveSchemaWithCrossEdges() throws Exception {
    	  Assume.assumeTrue(this.sqlgGraph.getSqlDialect().supportsDistribution());
    	  Configuration c = getConfigurationClone();
          c.setProperty(SqlgGraph.DISTRIBUTED, true);
          sqlgGraph = SqlgGraph.open(c);
          
          String schema1 = "willDelete1";
          Vertex v1 = sqlgGraph.addVertex(T.label, schema1 + ".t1", "name", "n1", "hello", "world");
          String schema2 = "willDelete2";
          Vertex v2 = sqlgGraph.addVertex(T.label, schema2 + ".t2", "name", "n2", "hello", "world");
          Vertex v3 = sqlgGraph.addVertex(T.label, schema2 + ".t3", "name", "n3", "hello", "world");
          Vertex v4 = sqlgGraph.addVertex(T.label, schema2 + ".t4", "name", "n4", "hello", "world");
          
          v1.addEdge("e1", v3, "me","again");
          v2.addEdge("e1", v3, "me","again");
          v1.addEdge("e1", v4, "me","again");
          v2.addEdge("e1", v4, "me","again");
          
          
          sqlgGraph.tx().commit();
          
          assertTrue(sqlgGraph.getTopology().getSchema(schema1).isPresent());
          assertTrue(sqlgGraph.getTopology().getSchema(schema2).isPresent());
          
          sqlgGraph.getTopology().getSchema(schema1).ifPresent((Schema s) -> s.remove(false));
          sqlgGraph.tx().commit();
          
          assertFalse(sqlgGraph.getTopology().getSchema(schema1).isPresent());
          // this used to fail
          sqlgGraph.getTopology().getSchema(schema2).ifPresent((Schema s) -> s.remove(false));
          sqlgGraph.tx().commit();
        
          assertFalse(sqlgGraph.getTopology().getSchema(schema2).isPresent());
          
          
    }
}
