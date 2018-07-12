package org.umlg.sqlg.test.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SchemaTable;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.*;
import org.umlg.sqlg.test.BaseTest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Date: 2014/08/17
 * Time: 2:43 PM
 */
public class TestIndex extends BaseTest {

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void testIndexViaTopology() {
        this.sqlgGraph.traversal().V().hasLabel("Person").values(T.id.getAccessor());
        Assume.assumeTrue(this.sqlgGraph.getSqlDialect().supportsTransactionalSchema());
        for (int i = 0; i < 5000; i++) {
            this.sqlgGraph.addVertex(T.label, "Person", "name", "john");
        }
        this.sqlgGraph.tx().commit();
        Optional<VertexLabel> personVertexOptional = this.sqlgGraph.getTopology().getVertexLabel(this.sqlgGraph.getSqlDialect().getPublicSchema(), "Person");
        Assert.assertTrue(personVertexOptional.isPresent());
        Optional<PropertyColumn> namePropertyOptional = personVertexOptional.get().getProperty("name");
        Assert.assertTrue(namePropertyOptional.isPresent());
        Optional<Index> indexOptional = personVertexOptional.get().getIndex("name");
        Assert.assertFalse(indexOptional.isPresent());

        this.sqlgGraph.tx().rollback();

        indexOptional = personVertexOptional.get().getIndex("name");
        Assert.assertFalse(indexOptional.isPresent());

        Index index = personVertexOptional.get().ensureIndexExists(IndexType.NON_UNIQUE, Collections.singletonList(namePropertyOptional.get()));
        this.sqlgGraph.tx().commit();
        Assert.assertTrue(index.getIndexType() == IndexType.NON_UNIQUE);

        //Check if the index is being used
        Connection conn = this.sqlgGraph.tx().getConnection();
        try (Statement statement = conn.createStatement()) {
            if (this.sqlgGraph.getSqlDialect().getClass().getSimpleName().contains("Postgres")) {
                ResultSet rs = statement.executeQuery("explain analyze SELECT * FROM \"public\".\"V_Person\" a WHERE a.\"name\" = 'john'");
                Assert.assertTrue(rs.next());
                String result = rs.getString(1);
                System.out.println(result);
                Assert.assertTrue(result.contains("Index Scan") || result.contains("Bitmap Heap Scan"));
            }
        } catch (SQLException e) {
            Assert.fail(e.getMessage());
        }
        this.sqlgGraph.tx().rollback();
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void testUniqueIndexViaTopolgy() {
        this.sqlgGraph.addVertex(T.label, "Person", "name", "john");
        this.sqlgGraph.tx().commit();
        Optional<VertexLabel> personVertexLabelOptional = this.sqlgGraph.getTopology().getVertexLabel(this.sqlgGraph.getSqlDialect().getPublicSchema(), "Person");
        Assert.assertTrue(personVertexLabelOptional.isPresent());
        Optional<PropertyColumn> propertyOptional = personVertexLabelOptional.get().getProperty("name");
        Assert.assertTrue(propertyOptional.isPresent());

        personVertexLabelOptional.get().ensureIndexExists(IndexType.UNIQUE, Collections.singletonList(propertyOptional.get()));
        this.sqlgGraph.tx().commit();
        try {
            this.sqlgGraph.addVertex(T.label, "Person", "name", "john");
            Assert.fail("Unique index did not work.");
        } catch (RuntimeException e) {

        }
        this.sqlgGraph.tx().rollback();
    }

    @Test
    public void testIndexOnInteger() {
        this.sqlgGraph.createVertexLabeledIndex("Person", "name", "dummy", "age", 1);
        this.sqlgGraph.tx().commit();
        for (int i = 0; i < 5000; i++) {
            this.sqlgGraph.addVertex(T.label, "Person", "name", "john" + i, "age", i);
        }
        this.sqlgGraph.tx().commit();
        //Check if the index is being used
        Connection conn = this.sqlgGraph.tx().getConnection();
        try (Statement statement = conn.createStatement()) {
            if (this.sqlgGraph.getSqlDialect().getClass().getSimpleName().contains("Postgres")) {
                ResultSet rs = statement.executeQuery("explain analyze SELECT * FROM \"public\".\"V_Person\" a WHERE a.\"name\" = 'john50'");
                Assert.assertTrue(rs.next());
                String result = rs.getString(1);
                System.out.println(result);
                Assert.assertTrue(result.contains("Index Scan") || result.contains("Bitmap Heap Scan"));
                statement.close();
            }
        } catch (SQLException e) {
            Assert.fail(e.getMessage());
        }
        this.sqlgGraph.tx().rollback();
    }

    @Test
    public void testIndexOnVertex2() throws SQLException {
        VertexLabel vertexLabel = this.sqlgGraph.getTopology().ensureVertexLabelExist("Person", new HashMap<String, PropertyType>() {{
            put("name", PropertyType.STRING);
        }});
        vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, new ArrayList<>(vertexLabel.getProperties().values()));
        this.sqlgGraph.tx().commit();
        for (int i = 0; i < 5000; i++) {
            this.sqlgGraph.addVertex(T.label, "Person", "name", "john" + i);
        }
        this.sqlgGraph.tx().commit();
        Assert.assertEquals(1, this.sqlgGraph.traversal().V().has(T.label, "Person").has("name", "john50").count().next(), 0);
        Assert.assertEquals(1, this.sqlgGraph.traversal().V().has(T.label, "Person").has("name", P.eq("john50")).count().next().intValue());

        //Check if the index is being used
        Connection conn = this.sqlgGraph.tx().getConnection();
        Statement statement = conn.createStatement();
        if (this.sqlgGraph.getSqlDialect().getClass().getSimpleName().contains("Postgres")) {
            ResultSet rs = statement.executeQuery("explain analyze SELECT * FROM \"public\".\"V_Person\" a WHERE a.\"name\" = 'john50'");
            Assert.assertTrue(rs.next());
            String result = rs.getString(1);
            Assert.assertTrue(result, result.contains("Index Scan") || result.contains("Bitmap Heap Scan"));
            statement.close();
        }
        this.sqlgGraph.tx().rollback();
    }

    @Test
    public void testIndexOnVertex() throws SQLException {
        this.sqlgGraph.createVertexLabeledIndex("Person", "name1", "dummy", "name2", "dummy", "name3", "dummy");
        this.sqlgGraph.tx().commit();
        for (int i = 0; i < 5000; i++) {
            this.sqlgGraph.addVertex(T.label, "Person", "name1", "john" + i, "name2", "tom" + i, "name3", "piet" + i);
        }
        this.sqlgGraph.tx().commit();
        Assert.assertEquals(1, this.sqlgGraph.traversal().V().has(T.label, "Person").has("name1", "john50").count().next(), 0);
        Assert.assertEquals(1, this.sqlgGraph.traversal().V().has(T.label, "Person").has("name1", P.eq("john50")).count().next().intValue());

        //Check if the index is being used
        Connection conn = this.sqlgGraph.tx().getConnection();
        Statement statement = conn.createStatement();
        if (this.sqlgGraph.getSqlDialect().getClass().getSimpleName().contains("Postgres")) {
            ResultSet rs = statement.executeQuery("explain analyze SELECT * FROM \"public\".\"V_Person\" a WHERE a.\"name1\" = 'john50'");
            Assert.assertTrue(rs.next());
            String result = rs.getString(1);
            Assert.assertTrue(result, result.contains("Index Scan") || result.contains("Bitmap Heap Scan"));
            statement.close();
        }
        this.sqlgGraph.tx().rollback();
    }

    @Test
    public void testIndexOnVertex1() throws SQLException {
        //This is for postgres only
        Assume.assumeTrue(this.sqlgGraph.getSqlDialect().getClass().getSimpleName().contains("Postgres"));
        this.sqlgGraph.createVertexLabeledIndex("Person", "name1", "dummy", "name2", "dummy", "name3", "dummy");
        this.sqlgGraph.tx().commit();
        for (int i = 0; i < 5000; i++) {
            this.sqlgGraph.addVertex(T.label, "Person", "name1", "john" + i, "name2", "tom" + i, "name3", "piet" + i);
        }
        this.sqlgGraph.tx().commit();
        Assert.assertEquals(1, this.sqlgGraph.traversal().V().has(T.label, "Person").has("name1", "john50").count().next(), 0);
        Connection conn = this.sqlgGraph.getConnection();
        Statement statement = conn.createStatement();
        if (this.sqlgGraph.getSqlDialect().getClass().getSimpleName().contains("Postgres")) {
            ResultSet rs = statement.executeQuery("explain analyze SELECT * FROM \"public\".\"V_Person\" a WHERE a.\"name1\" = 'john50'");
            Assert.assertTrue(rs.next());
            String result = rs.getString(1);
            Assert.assertTrue(result, result.contains("Index Scan") || result.contains("Bitmap Heap Scan"));
            statement.close();
            conn.close();
        }
    }

    @Test
    public void testIndexOnVertex1Schema() throws SQLException {
        //This is for postgres only
        Assume.assumeTrue(this.sqlgGraph.getSqlDialect().getClass().getSimpleName().contains("Postgres"));
        this.sqlgGraph.createVertexLabeledIndex("MySchema.Person", "name1", "dummy", "name2", "dummy", "name3", "dummy");
        this.sqlgGraph.tx().commit();
        for (int i = 0; i < 5000; i++) {
            this.sqlgGraph.addVertex(T.label, "MySchema.Person", "name1", "john" + i, "name2", "tom" + i, "name3", "piet" + i);
        }
        this.sqlgGraph.tx().commit();
        Assert.assertEquals(1, this.sqlgGraph.traversal().V().has(T.label, "MySchema.Person").has("name1", "john50").count().next(), 0);
        Connection conn = this.sqlgGraph.getConnection();
        Statement statement = conn.createStatement();
        if (this.sqlgGraph.getSqlDialect().getClass().getSimpleName().contains("Postgres")) {
            ResultSet rs = statement.executeQuery("explain analyze SELECT * FROM \"MySchema\".\"V_Person\" a WHERE a.\"name1\" = 'john50'");
            Assert.assertTrue(rs.next());
            String result = rs.getString(1);
            Assert.assertTrue(result, result.contains("Index Scan") || result.contains("Bitmap Heap Scan"));
            statement.close();
            conn.close();
        }
    }

    @Test
    public void testIndexExist() throws Exception {
        this.sqlgGraph.createVertexLabeledIndex("Person", "name", "a");
        this.sqlgGraph.tx().commit();
        this.sqlgGraph.createVertexLabeledIndex("Person", "name", "a");
        this.sqlgGraph.createVertexLabeledIndex("Person", "name", "a");
        this.sqlgGraph.tx().commit();

        this.sqlgGraph.close();
        this.sqlgGraph = SqlgGraph.open(configuration);
        this.sqlgGraph.createVertexLabeledIndex("Person", "name", "a");


    }

    @Test
    public void testIndexExistSchema() throws Exception {
        this.sqlgGraph.createVertexLabeledIndex("MySchema.Person", "name", "a");
        this.sqlgGraph.createVertexLabeledIndex("Person", "name", "a");

        this.sqlgGraph.tx().commit();
        this.sqlgGraph.createVertexLabeledIndex("MySchema.Person", "name", "a");
        this.sqlgGraph.createVertexLabeledIndex("MySchema.Person", "name", "a");
        this.sqlgGraph.tx().commit();

        this.sqlgGraph.close();
        this.sqlgGraph = SqlgGraph.open(configuration);
        this.sqlgGraph.createVertexLabeledIndex("MySchema.Person", "name", "a");

    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void testIndexOnEdge() throws Exception {
        Map<String, PropertyType> columns = new HashMap<>();
        columns.put("name", PropertyType.STRING);

        String publicSchema = this.sqlgGraph.getSqlDialect().getPublicSchema();
        this.sqlgGraph.getTopology().ensureVertexLabelExist("Person", columns);
        this.sqlgGraph.getTopology().ensureVertexLabelExist("Address", columns);
        this.sqlgGraph.getTopology().ensureEdgeLabelExist("person_address", SchemaTable.of(publicSchema, "Person"), SchemaTable.of(publicSchema, "Address"), columns);
        EdgeLabel edgeLabel = this.sqlgGraph.getTopology().getEdgeLabel(publicSchema, "person_address").get();
        edgeLabel.ensureIndexExists(IndexType.UNIQUE, Collections.singletonList(edgeLabel.getProperty("name").get()));
        this.sqlgGraph.tx().commit();

        for (int i = 0; i < 5000; i++) {
            Vertex person1 = this.sqlgGraph.addVertex(T.label, "Person", "name", "john");
            Vertex address1 = this.sqlgGraph.addVertex(T.label, "Person", "name", "john");
            person1.addEdge("person_address", address1, "name", "address" + i);
        }
        this.sqlgGraph.tx().commit();

        if (this.sqlgGraph.getSqlDialect().getClass().getSimpleName().contains("Postgres")) {
            Connection conn = this.sqlgGraph.getConnection();
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery("explain analyze SELECT * FROM \"public\".\"E_person_address\" a WHERE a.\"name\" = 'address1001'");
            Assert.assertTrue(rs.next());
            String result = rs.getString(1);
            Assert.assertTrue(result, result.contains("Index Scan") || result.contains("Bitmap Heap Scan"));
            statement.close();
            conn.close();
        }

        this.sqlgGraph.close();
        this.sqlgGraph = SqlgGraph.open(configuration);
        edgeLabel = this.sqlgGraph.getTopology().getEdgeLabel(publicSchema, "person_address").get();
        edgeLabel.ensureIndexExists(IndexType.UNIQUE, Collections.singletonList(edgeLabel.getProperty("name").get()));


    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void testIndexOnEdgeAcrossSchemas() {
        Vertex aa1 = this.sqlgGraph.addVertex(T.label, "A.A", "name", "aa");
        Vertex bb1 = this.sqlgGraph.addVertex(T.label, "B.B", "name", "bb");
        Vertex cc1 = this.sqlgGraph.addVertex(T.label, "C.C", "name", "cc");
        aa1.addEdge("test", bb1, "name", "ola");
        bb1.addEdge("test", cc1, "name", "ola");
        this.sqlgGraph.tx().commit();

        EdgeLabel edgeLabel = this.sqlgGraph.getTopology().getEdgeLabel("A", "test").get();
        edgeLabel.ensureIndexExists(IndexType.UNIQUE, Collections.singletonList(edgeLabel.getProperty("name").get()));
        this.sqlgGraph.tx().commit();

        Assert.assertEquals(IndexType.UNIQUE, edgeLabel.getIndex(this.sqlgGraph.getSqlDialect().indexName(SchemaTable.of("A", "test"), Topology.EDGE_PREFIX, Collections.singletonList("name"))).get().getIndexType());
        Assert.assertFalse(edgeLabel.getIndex(this.sqlgGraph.getSqlDialect().indexName(SchemaTable.of("B", "test"), Topology.EDGE_PREFIX, Collections.singletonList("name"))).isPresent());

        try {
            aa1.addEdge("test", bb1, "name", "ola");
            Assert.fail("Unique constraint should prevent this from happening");
        } catch (Exception e) {
            //swallow
        }
        this.sqlgGraph.tx().rollback();

        //this one is ok, no unique constraint
        bb1.addEdge("test", cc1, "name", "ola");
        this.sqlgGraph.tx().commit();
    }

    @Test
    public void testIndexTypeFromJSON() throws Exception {
        IndexType it1 = IndexType.fromNotifyJson(new ObjectMapper().readTree("{\"name\":\"UNIQUE\"}"));
        Assert.assertEquals(IndexType.UNIQUE, it1);
        IndexType it2 = IndexType.fromNotifyJson(new ObjectMapper().readTree("\"UNIQUE\""));
        Assert.assertEquals(IndexType.UNIQUE, it2);

        it1 = IndexType.fromNotifyJson(new ObjectMapper().readTree("{\"name\":\"NON_UNIQUE\"}"));
        Assert.assertEquals(IndexType.NON_UNIQUE, it1);
        it2 = IndexType.fromNotifyJson(new ObjectMapper().readTree("\"NON_UNIQUE\""));
        Assert.assertEquals(IndexType.NON_UNIQUE, it2);

    }

    @Test
    public void testLongIndexName() throws Exception {
    	String i1=buildLongIndex(sqlgGraph);
    	String i2=buildLongIndex(sqlgGraph);
    	Assert.assertEquals(i1,i2);
    	sqlgGraph.close();
    	sqlgGraph=SqlgGraph.open(getConfigurationClone());
    	String i3=buildLongIndex(sqlgGraph);
    	Assert.assertEquals(i1,i3);
    }

    private String buildLongIndex(SqlgGraph g){
    	Schema sch=g.getTopology().ensureSchemaExist("longIndex");
    	Map<String,PropertyType> columns=new HashMap<String, PropertyType>();
    	columns.put("longpropertyname1",PropertyType.STRING);
    	columns.put("longpropertyname2",PropertyType.STRING);
    	columns.put("longpropertyname3",PropertyType.STRING);
    	VertexLabel label=sch.ensureVertexLabelExist("LongIndex", columns);
    	List<PropertyColumn> properties=Arrays.asList(
    			label.getProperty("longpropertyname1").get()
    			,label.getProperty("longpropertyname2").get()
    			,label.getProperty("longpropertyname3").get());
    	Index idx=label.ensureIndexExists(IndexType.NON_UNIQUE, properties);

    	g.tx().commit();
    	return idx.getName();
    }

    @Test
    public void testShortIndexName() throws Exception {
    	String i1=buildShortIndex(sqlgGraph);
    	String i2=buildShortIndex(sqlgGraph);
    	Assert.assertEquals(i1,i2);
    	sqlgGraph.close();
    	sqlgGraph=SqlgGraph.open(getConfigurationClone());
    	String i3=buildShortIndex(sqlgGraph);
    	Assert.assertEquals(i1,i3);
    }

    private String buildShortIndex(SqlgGraph g){
    	Schema sch=g.getTopology().ensureSchemaExist("longIndex");
    	Map<String,PropertyType> columns=new HashMap<String, PropertyType>();
    	columns.put("short1",PropertyType.STRING);
    	columns.put("short2",PropertyType.STRING);
    	columns.put("short3",PropertyType.STRING);
    	VertexLabel label=sch.ensureVertexLabelExist("LongIndex", columns);
    	List<PropertyColumn> properties=Arrays.asList(
    			label.getProperty("short1").get()
    			,label.getProperty("short2").get()
    			,label.getProperty("short3").get());
    	Index idx=label.ensureIndexExists(IndexType.NON_UNIQUE, properties);

    	g.tx().commit();
    	return idx.getName();
    }

    @Test
    public void testMultipleIndexesOnLabel() throws Exception {

        VertexLabel vertexLabel = this.sqlgGraph.getTopology().getPublicSchema().ensureVertexLabelExist(
                "Person",
                new HashMap<String, PropertyType>() {{
                    put("name", PropertyType.STRING);
                    put("surname", PropertyType.STRING);
                }}
        );
        PropertyColumn propertyColumn = vertexLabel.getProperty("name").orElseThrow(()->new RuntimeException("its a bug"));
        vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, Collections.singletonList(propertyColumn));
        this.sqlgGraph.tx().commit();

        propertyColumn = vertexLabel.getProperty("surname").orElseThrow(()->new RuntimeException("its a bug"));
        vertexLabel.ensureIndexExists(IndexType.NON_UNIQUE, Collections.singletonList(propertyColumn));
        this.sqlgGraph.tx().commit();

        this.sqlgGraph.addVertex(T.label, "Person", "name", "John", "surname", "Smith");
        this.sqlgGraph.tx().commit();

        this.sqlgGraph.close();
        this.sqlgGraph = SqlgGraph.open(configuration);
        Optional<VertexLabel> vertexLabelOptional = this.sqlgGraph.getTopology().getPublicSchema().getVertexLabel("Person");
        Assert.assertTrue(vertexLabelOptional.isPresent());
        Map<String, Index> indexMap = vertexLabelOptional.get().getIndexes();
        Assert.assertEquals(2, indexMap.size());
    }
}
