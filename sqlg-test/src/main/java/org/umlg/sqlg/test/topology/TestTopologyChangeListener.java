package org.umlg.sqlg.test.topology;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Before;
import org.junit.Test;
import org.umlg.sqlg.structure.*;
import org.umlg.sqlg.structure.topology.*;
import org.umlg.sqlg.test.BaseTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Date: 2017/01/22
 * Time: 6:58 PM
 */
public class TestTopologyChangeListener extends BaseTest {

    private List<Triple<TopologyInf, String, TopologyChangeAction>> topologyListenerTriple = new ArrayList<>();

    @Before
    public void before() throws Exception {
        super.before();
        this.topologyListenerTriple.clear();
    }

    @Test
    public void testAddSchemaAndVertexAndEdge() {
        TopologyListenerTest topologyListenerTest = new TopologyListenerTest(topologyListenerTriple);
        this.sqlgGraph.getTopology().registerListener(topologyListenerTest);
        Vertex a1 = this.sqlgGraph.addVertex(T.label, "A.A", "name", "asda");
        Vertex a2 = this.sqlgGraph.addVertex(T.label, "A.A", "name", "asdasd");
        Edge e1 = a1.addEdge("aa", a2);
        a1.property("surname", "asdasd");
        e1.property("special", "");
        Vertex b1 = this.sqlgGraph.addVertex(T.label, "A.B", "name", "asdasd");
        Edge e2 = a1.addEdge("aa", b1);

        Schema schema = this.sqlgGraph.getTopology().getSchema("A").get();
        VertexLabel aVertexLabel = schema.getVertexLabel("A").get();
        EdgeLabel edgeLabel = aVertexLabel.getOutEdgeLabel("aa").get();
        PropertyColumn vertexPropertyColumn = aVertexLabel.getProperty("surname").get();
        PropertyColumn edgePropertyColumn = edgeLabel.getProperty("special").get();
        VertexLabel bVertexLabel = schema.getVertexLabel("B").get();

        Index index = aVertexLabel.ensureIndexExists(IndexType.UNIQUE, new ArrayList<>(aVertexLabel.getProperties().values()));

        //This adds a schema and 2 indexes and the globalUniqueIndex, so 4 elements in all
        GlobalUniqueIndex globalUniqueIndex = schema.ensureGlobalUniqueIndexExist(new HashSet<>(aVertexLabel.getProperties().values()));

        assertEquals(12, this.topologyListenerTriple.size());

        assertEquals(schema, this.topologyListenerTriple.get(0).getLeft());
        assertEquals("", this.topologyListenerTriple.get(0).getMiddle());
        assertEquals(TopologyChangeAction.CREATE, this.topologyListenerTriple.get(0).getRight());

        assertEquals(aVertexLabel, this.topologyListenerTriple.get(1).getLeft());
        Map<String,PropertyColumn> props=((VertexLabel)this.topologyListenerTriple.get(1).getLeft()).getProperties();
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("surname"));

        assertEquals("", this.topologyListenerTriple.get(1).getMiddle());
        assertEquals(TopologyChangeAction.CREATE, this.topologyListenerTriple.get(1).getRight());

        assertEquals(edgeLabel, this.topologyListenerTriple.get(2).getLeft());
        String s=this.topologyListenerTriple.get(2).getLeft().toString();
        assertTrue(s.contains(edgeLabel.getSchema().getName()));
        props=((EdgeLabel)this.topologyListenerTriple.get(2).getLeft()).getProperties();
        assertTrue(props.containsKey("special"));        
        assertEquals("", this.topologyListenerTriple.get(2).getMiddle());
        assertEquals(TopologyChangeAction.CREATE, this.topologyListenerTriple.get(2).getRight());

        assertEquals(vertexPropertyColumn, this.topologyListenerTriple.get(3).getLeft());
        assertEquals("", this.topologyListenerTriple.get(3).getMiddle());
        assertEquals(TopologyChangeAction.CREATE, this.topologyListenerTriple.get(3).getRight());

        assertEquals(edgePropertyColumn, this.topologyListenerTriple.get(4).getLeft());
        assertEquals("", this.topologyListenerTriple.get(4).getMiddle());
        assertEquals(TopologyChangeAction.CREATE, this.topologyListenerTriple.get(4).getRight());

        assertEquals(bVertexLabel, this.topologyListenerTriple.get(5).getLeft());
        assertEquals("", this.topologyListenerTriple.get(5).getMiddle());
        assertEquals(TopologyChangeAction.CREATE, this.topologyListenerTriple.get(5).getRight());

        assertEquals(edgeLabel, this.topologyListenerTriple.get(6).getLeft());
        assertEquals("A.B", this.topologyListenerTriple.get(6).getMiddle());
        assertEquals(TopologyChangeAction.ADD_IN_VERTEX_LABELTO_EDGE, this.topologyListenerTriple.get(6).getRight());

        assertEquals(index, this.topologyListenerTriple.get(7).getLeft());
        assertEquals("", this.topologyListenerTriple.get(7).getMiddle());
        assertEquals(TopologyChangeAction.CREATE, this.topologyListenerTriple.get(7).getRight());

        assertEquals(globalUniqueIndex, this.topologyListenerTriple.get(11).getLeft());
        assertEquals("", this.topologyListenerTriple.get(11).getMiddle());
        assertEquals(TopologyChangeAction.CREATE, this.topologyListenerTriple.get(11).getRight());

        this.sqlgGraph.tx().commit();
    }

    public static  class TopologyListenerTest implements TopologyListener {
    	private List<Triple<TopologyInf, String, TopologyChangeAction>> topologyListenerTriple = new ArrayList<>();

        public TopologyListenerTest(List<Triple<TopologyInf, String, TopologyChangeAction>> topologyListenerTriple) {
			super();
			this.topologyListenerTriple = topologyListenerTriple;
		}
        
        public TopologyListenerTest(){
        	
        }

		@Override
        public void change(TopologyInf topologyInf, String oldValue, TopologyChangeAction action) {
			String s=topologyInf.toString();
			assertNotNull(s);
			assertTrue(s+"does not contain "+ topologyInf.getName(),s.contains(topologyInf.getName()));
            topologyListenerTriple.add(
                    Triple.of(topologyInf, oldValue, action)
            );
        }
		
		public List<Triple<TopologyInf, String, TopologyChangeAction>> getTopologyListenerTriple() {
			return topologyListenerTriple;
		}
		
		public boolean receivedEvent(TopologyInf topologyInf, TopologyChangeAction action){
			for (Triple<TopologyInf, String, TopologyChangeAction> t:topologyListenerTriple){
				if (t.getLeft().equals(topologyInf) && t.getRight().equals(action)){
					return true;
				}
			}
			return false;
		}
		
		public void reset(){
			topologyListenerTriple.clear();
		}
    }
}
