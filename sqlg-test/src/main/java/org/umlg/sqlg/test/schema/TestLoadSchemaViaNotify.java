package org.umlg.sqlg.test.schema;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.umlg.sqlg.structure.*;
import org.umlg.sqlg.test.BaseTest;
import org.umlg.sqlg.test.topology.TestTopologyChangeListener;
import org.umlg.sqlg.test.topology.TestTopologyChangeListener.TopologyListenerTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Date: 2014/11/03
 * Time: 6:22 PM
 */
public class TestLoadSchemaViaNotify extends BaseTest {

    @BeforeClass
    public static void beforeClass() throws ClassNotFoundException, IOException, PropertyVetoException {
        URL sqlProperties = Thread.currentThread().getContextClassLoader().getResource("sqlg.properties");
        try {
            configuration = new PropertiesConfiguration(sqlProperties);
            Assume.assumeTrue(configuration.getString("jdbc.url").contains("postgresql"));
            configuration.addProperty("distributed", true);
            if (!configuration.containsKey("jdbc.url"))
                throw new IllegalArgumentException(String.format("SqlGraph configuration requires that the %s be set", "jdbc.url"));

        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLoadSchemaForeignKeyOutSchemaToPublic() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            Vertex person = this.sqlgGraph.addVertex(T.label, "Mammal.Person", "name", "a");
            Vertex car = this.sqlgGraph.addVertex(T.label, "Car", "name", "a");
            person.addEdge("drives", car);
            this.sqlgGraph.tx().commit();
            Thread.sleep(1_000);
            car = sqlgGraph1.traversal().V(car.id()).next();
            Iterator<Vertex> verticesIter = car.vertices(Direction.IN, "drives");
            int size = IteratorUtils.toList(verticesIter).size();
            Assert.assertEquals(1, size);
        }
    }

    @Test
    public void testLoadSchemaForeignKeyInSchemaToPublic() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            Vertex person = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            Vertex car = this.sqlgGraph.addVertex(T.label, "Fleet.Car", "name", "a");
            person.addEdge("drives", car);
            this.sqlgGraph.tx().commit();
            Thread.sleep(1_000);
            car = sqlgGraph1.traversal().V(car.id()).next();
            Iterator<Vertex> verticesIter = car.vertices(Direction.IN, "drives");
            int size = IteratorUtils.toList(verticesIter).size();
            Assert.assertEquals(1, size);
        }
    }

    @Test
    public void testLazyLoadTableViaVertexHas() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            //add a vertex in the old, the new should only see it after a commit
            this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            this.sqlgGraph.tx().commit();
            //postgresql notify only happens after the commit, need to wait a bit.
            Thread.sleep(1000);
            Assert.assertEquals(1, sqlgGraph1.traversal().V().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Person").count().next().intValue());
            Assert.assertEquals("a", sqlgGraph1.traversal().V().has(T.label, "Person").next().value("name"));
            sqlgGraph1.tx().rollback();
        }
    }

    @Test
    public void testLazyLoadTableViaVertexHasWithKey() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            //add a vertex in the old, the new should only see it after a commit
            this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            this.sqlgGraph.tx().commit();
            Thread.sleep(1000);

            Assert.assertEquals(1, sqlgGraph1.traversal().V().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Person").has("name", "a").count().next().intValue());
            sqlgGraph1.tx().rollback();
        }
    }

    @Test
    public void testLazyLoadTableViaVertexHasWithKeyMissingColumn() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            //add a vertex in the old, the new should only see it after a commit
            Vertex v1 = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            this.sqlgGraph.tx().commit();
            Thread.sleep(1000);

            Assert.assertEquals(1, sqlgGraph1.traversal().V().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Person").has("name", "a").count().next().intValue());
            Vertex v11 = sqlgGraph1.traversal().V().has(T.label, "Person").<Vertex>has("name", "a").next();
            Assert.assertFalse(v11.property("surname").isPresent());
            //the next alter will lock if this transaction is still active
            sqlgGraph1.tx().rollback();

            //add column in one
            v1.property("surname", "bbb");
            this.sqlgGraph.tx().commit();

            Thread.sleep(1_000);

            Vertex v12 = sqlgGraph1.addVertex(T.label, "Person", "surname", "ccc");
            Assert.assertEquals("ccc", v12.value("surname"));
            sqlgGraph1.tx().rollback();
        }
    }

    //Fails via maven for Hsqldb
    @Test
    public void testLazyLoadTableViaEdgeCreation() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            //add a vertex in the old, the new should only see it after a commit
            Vertex v1 = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            Vertex v2 = this.sqlgGraph.addVertex(T.label, "Person", "name", "b");
            this.sqlgGraph.tx().commit();
            Thread.sleep(1000);
            Vertex v11 = sqlgGraph1.addVertex(T.label, "Person", "surname", "ccc");

            Vertex v12 = sqlgGraph1.addVertex(T.label, "Person", "surname", "ccc");
            sqlgGraph1.tx().commit();

            v1.addEdge("friend", v2);
            this.sqlgGraph.tx().commit();

            v11.addEdge("friend", v12);
            sqlgGraph1.tx().commit();

            Assert.assertEquals(1, vertexTraversal(sqlgGraph1, v11).out("friend").count().next().intValue());
            sqlgGraph1.tx().rollback();
        }
    }

    @Test
    public void testLazyLoadTableViaEdgesHas() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            //add a vertex in the old, the new should only see it after a commit
            Vertex v1 = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            Vertex v2 = this.sqlgGraph.addVertex(T.label, "Person", "name", "b");
            v1.addEdge("friend", v2);
            this.sqlgGraph.tx().commit();
            //Not entirely sure what this is for, else it seems hazelcast existVertexLabel not yet distributed the map
            Thread.sleep(1000);

            Assert.assertEquals(1, this.sqlgGraph.traversal().E().has(T.label, "friend").count().next().intValue());

            Assert.assertEquals(1, sqlgGraph1.traversal().E().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().E().has(T.label, "friend").count().next().intValue());
            Assert.assertEquals(2, sqlgGraph1.traversal().V().has(T.label, "Person").count().next().intValue());
            sqlgGraph1.tx().rollback();
        }
    }

    @Test
    public void testLoadSchemaRemembersUncommittedSchemas() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            Vertex v1 = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            Vertex v2 = this.sqlgGraph.addVertex(T.label, "Person", "name", "b");
            v1.addEdge("friend", v2);
            this.sqlgGraph.tx().commit();
            this.sqlgGraph.addVertex(T.label, "Animal", "name", "b");
            this.sqlgGraph.addVertex(T.label, "Car", "name", "b");
            Assert.assertEquals(1, this.sqlgGraph.traversal().E().has(T.label, "friend").count().next().intValue());
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1000);

            Assert.assertEquals(1, sqlgGraph1.traversal().E().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().E().has(T.label, "friend").count().next().intValue());
            Assert.assertEquals(2, sqlgGraph1.traversal().V().has(T.label, "Person").count().next().intValue());

            sqlgGraph1.tx().rollback();
        }
    }

    @Test
    public void testLoadSchemaEdge() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            Vertex personVertex = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            Vertex dogVertex = this.sqlgGraph.addVertex(T.label, "Dog", "name", "b");
            Edge petEdge = personVertex.addEdge("pet", dogVertex, "test", "this");
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1_000);

            Assert.assertEquals(1, sqlgGraph1.traversal().E().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().E().has(T.label, "pet").count().next().intValue());
            Assert.assertEquals("this", sqlgGraph1.traversal().E().has(T.label, "pet").next().value("test"));
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Person").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Dog").count().next().intValue());

            Assert.assertEquals(dogVertex, sqlgGraph1.traversal().V(personVertex.id()).out("pet").next());
            Assert.assertEquals(personVertex, sqlgGraph1.traversal().V(dogVertex.id()).in("pet").next());
            Assert.assertEquals("a", sqlgGraph1.traversal().V(personVertex.id()).next().<String>value("name"));
            Assert.assertEquals("b", sqlgGraph1.traversal().V(dogVertex.id()).next().<String>value("name"));

            sqlgGraph1.tx().rollback();

            //add a property to the vertex
            personVertex.property("surname", "AAA");
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1_000);
            Assert.assertEquals("AAA", sqlgGraph1.traversal().V(personVertex.id()).next().<String>value("surname"));

            //add property to the edge
            petEdge.property("edgeProperty1", "a");
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1_000);
            Assert.assertEquals("a", sqlgGraph1.traversal().E(petEdge.id()).next().<String>value("edgeProperty1"));

            //add an edge
            Vertex addressVertex = this.sqlgGraph.addVertex(T.label, "Address", "name", "1 Nowhere");
            personVertex.addEdge("homeAddress", addressVertex);
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1_000);
            Assert.assertEquals(2, sqlgGraph1.traversal().E().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().E().has(T.label, "pet").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().E().has(T.label, "homeAddress").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Person").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Dog").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Address").count().next().intValue());

        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void loadIndex() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {

            Map<String, PropertyType> properties = new HashMap<>();
            properties.put("name", PropertyType.STRING);
            VertexLabel vertexLabel = this.sqlgGraph.getTopology().getPublicSchema().ensureVertexLabelExist("A", properties);
            vertexLabel.ensureIndexExists(IndexType.UNIQUE, Collections.singletonList(vertexLabel.getProperty("name").get()));
            this.sqlgGraph.tx().commit();
            Optional<Index> index = this.sqlgGraph.getTopology().getPublicSchema()
                    .getVertexLabel("A").get()
                    .getIndex(this.sqlgGraph.getSqlDialect()
                            .indexName(
                                    SchemaTable.of(this.sqlgGraph.getSqlDialect().getPublicSchema(), "A"),
                                    SchemaManager.VERTEX_PREFIX,
                                    Collections.singletonList("name")));
            Assert.assertTrue(index.isPresent());

            //allow time for notification to happen
            Thread.sleep(1_000);
            index = sqlgGraph1.getTopology().getPublicSchema()
                    .getVertexLabel("A").get()
                    .getIndex(this.sqlgGraph.getSqlDialect()
                            .indexName(
                                    SchemaTable.of(this.sqlgGraph.getSqlDialect().getPublicSchema(), "A"),
                                    SchemaManager.VERTEX_PREFIX,
                                    Collections.singletonList("name")));
            Assert.assertTrue(index.isPresent());
        }
    }

    @Test
    public void testGlobalUniqueIndexViaNotify() throws Exception {
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            Map<String, PropertyType> properties = new HashMap<>();
            properties.put("name1", PropertyType.STRING);
            properties.put("name2", PropertyType.STRING);
            VertexLabel aVertexLabel = this.sqlgGraph.getTopology().getPublicSchema().ensureVertexLabelExist("A", properties);
            properties.clear();
            properties.put("name3", PropertyType.STRING);
            properties.put("name4", PropertyType.STRING);
            VertexLabel bVertexLabel = this.sqlgGraph.getTopology().getPublicSchema().ensureVertexLabelExist("B", properties);
            properties.clear();
            properties.put("name5", PropertyType.STRING);
            properties.put("name6", PropertyType.STRING);
            EdgeLabel edgeLabel = aVertexLabel.ensureEdgeLabelExist("ab", bVertexLabel, properties);
            Set<PropertyColumn> globalUniqueIndexPropertyColumns = new HashSet<>();
            globalUniqueIndexPropertyColumns.addAll(new HashSet<>(aVertexLabel.getProperties().values()));
            globalUniqueIndexPropertyColumns.addAll(new HashSet<>(bVertexLabel.getProperties().values()));
            globalUniqueIndexPropertyColumns.addAll(new HashSet<>(edgeLabel.getProperties().values()));
            this.sqlgGraph.getTopology().ensureGlobalUniqueIndexExist(globalUniqueIndexPropertyColumns);
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1_000);

            Assert.assertEquals(1, sqlgGraph1.getTopology().getGlobalUniqueIndexes().size());

            sqlgGraph1.addVertex(T.label, "A", "name1", "123");
            sqlgGraph1.tx().commit();
            try {
                sqlgGraph.addVertex(T.label, "A", "name1", "123");
                Assert.fail("GlobalUniqueIndex should prevent this from happening");
            } catch (Exception e) {
                //swallow
            }
        }
    }

    @Test
    public void testViaNotifyIsCommitted() throws Exception {
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            Vertex a1 = this.sqlgGraph.addVertex(T.label, "A.A", "name", "halo");
            Vertex b1 = this.sqlgGraph.addVertex(T.label, "B.B", "name", "halo");
            a1.addEdge("ab", b1, "name", "asd");
            this.sqlgGraph.getTopology().getSchema("A").get().getVertexLabel("A")
                    .ifPresent(v->v.ensureIndexExists(IndexType.UNIQUE, Collections.singletonList(v.getProperty("name").get())));

            Schema aSchema = this.sqlgGraph.getTopology().getSchema("A").get();
            Assert.assertTrue(aSchema.isUncommitted());
            VertexLabel vertexLabel = aSchema.getVertexLabel("A").get();
            Assert.assertTrue(vertexLabel.isUncommitted());
            PropertyColumn namePropertyColumn = vertexLabel.getProperty("name").get();
            Assert.assertTrue(namePropertyColumn.isUncommitted());
            String indexName = this.sqlgGraph.getSqlDialect().indexName(SchemaTable.of("A", "A"), SchemaManager.VERTEX_PREFIX, Collections.singletonList("name"));
            Index index = vertexLabel.getIndex(indexName).get();
            Assert.assertTrue(index.isUncommitted());

            this.sqlgGraph.tx().commit();
            //allow time for notification to happen
            Thread.sleep(1_000);
            aSchema = sqlgGraph1.getTopology().getSchema("A").get();
            Assert.assertTrue(aSchema.isCommitted());
            vertexLabel = aSchema.getVertexLabel("A").get();
            Assert.assertTrue(vertexLabel.isCommitted());
            namePropertyColumn = vertexLabel.getProperty("name").get();
            Assert.assertTrue(namePropertyColumn.isCommitted());
            indexName = sqlgGraph1.getSqlDialect().indexName(SchemaTable.of("A", "A"), SchemaManager.VERTEX_PREFIX, Collections.singletonList("name"));
            index = vertexLabel.getIndex(indexName).get();
            Assert.assertTrue(index.isCommitted());
        }
    }

    @Test
    public void testDistributedTopologyListener() throws Exception {
    	try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
    		List<Triple<TopologyInf, String, TopologyChangeAction>> topologyListenerTriple = new ArrayList<>();

	    	 TopologyListenerTest topologyListenerTest = new TestTopologyChangeListener.TopologyListenerTest(topologyListenerTriple);
	         sqlgGraph1.getTopology().registerListener(topologyListenerTest);
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
	         VertexLabel bVertexLabel = schema.getVertexLabel("B").get();
	         Index index = aVertexLabel.ensureIndexExists(IndexType.UNIQUE, new ArrayList<>(aVertexLabel.getProperties().values()));

	         //This adds a schema and 2 indexes and the globalUniqueIndex, so 4 elements in all
	         GlobalUniqueIndex globalUniqueIndex = schema.ensureGlobalUniqueIndexExist(new HashSet<>(aVertexLabel.getProperties().values()));

	         
	         this.sqlgGraph.tx().commit();
	         //allow time for notification to happen
	         Thread.sleep(1_000);
	         
	         // we're not getting property notification since we get vertex label notification, these include all properties committed
	         assertEquals(9, topologyListenerTriple.size());
	         
	         assertEquals(schema, topologyListenerTriple.get(0).getLeft());
	         assertEquals("", topologyListenerTriple.get(0).getMiddle());
	         assertEquals(TopologyChangeAction.CREATE, topologyListenerTriple.get(0).getRight());

	         assertEquals(aVertexLabel, topologyListenerTriple.get(1).getLeft());
	         assertEquals("", topologyListenerTriple.get(1).getMiddle());
	         assertEquals(TopologyChangeAction.CREATE, topologyListenerTriple.get(1).getRight());
	         Map<String,PropertyColumn> props=((VertexLabel)topologyListenerTriple.get(1).getLeft()).getProperties();
	         assertTrue(props.containsKey("name"));
	         assertTrue(props.containsKey("surname"));
	         
	         assertEquals(index, topologyListenerTriple.get(2).getLeft());
	         assertEquals("", topologyListenerTriple.get(2).getMiddle());
	         assertEquals(TopologyChangeAction.CREATE, topologyListenerTriple.get(2).getRight());
	         
	         assertEquals(edgeLabel, topologyListenerTriple.get(3).getLeft());
	         String s=topologyListenerTriple.get(3).getLeft().toString();
	         assertTrue(s.contains(edgeLabel.getSchema().getName()));
	         props=((EdgeLabel)topologyListenerTriple.get(3).getLeft()).getProperties();
	         assertTrue(props.containsKey("special"));     
	         assertEquals("", topologyListenerTriple.get(3).getMiddle());
	         assertEquals(TopologyChangeAction.CREATE, topologyListenerTriple.get(3).getRight());

	         assertEquals(bVertexLabel, topologyListenerTriple.get(4).getLeft());
	         assertEquals("", topologyListenerTriple.get(4).getMiddle());
	         assertEquals(TopologyChangeAction.CREATE, topologyListenerTriple.get(4).getRight());
    
	         assertEquals(globalUniqueIndex, topologyListenerTriple.get(8).getLeft());
	         assertEquals("", topologyListenerTriple.get(8).getMiddle());
	         assertEquals(TopologyChangeAction.CREATE, topologyListenerTriple.get(8).getRight());
    	}
    }
}
