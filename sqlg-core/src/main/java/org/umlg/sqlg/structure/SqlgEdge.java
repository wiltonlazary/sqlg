package org.umlg.sqlg.structure;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.util.SqlgUtil;

import java.sql.*;
import java.util.*;

/**
 * Date: 2014/07/12
 * Time: 5:41 AM
 */
public class SqlgEdge extends SqlgElement implements Edge {

    private Logger logger = LoggerFactory.getLogger(SqlgEdge.class.getName());
    private SqlgVertex inVertex;
    private SqlgVertex outVertex;

    /**
     * Called from @link {@link SqlgVertex} to create a brand new edge.
     *
     * @param sqlgGraph       The graph.
     * @param streaming       If in batch mode this indicates if its streaming or not.
     * @param schema          The schema the edge is in.
     * @param table           The edge's label which translates to a table name.
     * @param inVertex        The edge's in vertex.
     * @param outVertex       The edge's out vertex.
     * @param keyValueMapPair A pair of properties of the edge. Left contains all the properties and right the null valued properties.
     */
    public SqlgEdge(SqlgGraph sqlgGraph, boolean streaming, String schema, String table, SqlgVertex inVertex, SqlgVertex outVertex, Pair<Map<String, Object>, Map<String, Object>> keyValueMapPair) {
        super(sqlgGraph, schema, table);
        this.inVertex = inVertex;
        this.outVertex = outVertex;
        try {
            insertEdge(streaming, keyValueMapPair);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

//    /**
//     * Called from {@link SqlgVertex#edges(Direction, String...)}
//     * The edge already exist and must supply its table id. Not a {@link RecordId}.
//     *
//     * @param sqlgGraph The graph.
//     * @param id        The edge's id. This the edge's table's id. Not a {@link RecordId}.
//     * @param schema    The schema the edge is in.
//     * @param table     The table the edge is in. This translates to its label.
//     * @param inVertex  The in vertex.
//     * @param outVertex The out vertex.
//     * @param keyValues The properties of the edge. #TODO this is not used at present. It is loaded again from the db
//     *                  when the property is accessed.
//     */
//    public SqlgEdge(SqlgGraph sqlgGraph, Long id, String schema, String table, SqlgVertex inVertex, SqlgVertex outVertex, Object... keyValues) {
//        super(sqlgGraph, id, schema, table);
//        this.inVertex = inVertex;
//        this.outVertex = outVertex;
//        properties.clear();
//        properties.putAll(SqlgUtil.transformToInsertValues(keyValues).getRight());
//    }

    /**
     * This is the primary constructor for loading edges from the db via gremlin.
     *
     * @param sqlgGraph The graph.
     * @param id        The edge's id. This the edge's table's id. Not a {@link RecordId}.
     * @param schema    The schema the edge is in.
     * @param table     The table the edge is in. This translates to its label.
     */
    public SqlgEdge(SqlgGraph sqlgGraph, Long id, String schema, String table) {
        super(sqlgGraph, id, schema, table);
    }

    @Override
    public <V> Property<V> property(String key, V value) {
        if (this.removed) throw Element.Exceptions.elementAlreadyRemoved(Edge.class, this.id());
        this.sqlgGraph.tx().readWrite();
        return super.property(key, value);
    }

    private Iterator<Vertex> internalGetVertices(Direction direction) {
        final List<Vertex> vertices = new ArrayList<>();
        if (direction.equals(Direction.OUT) || direction.equals(Direction.BOTH))
            vertices.add(getOutVertex());
        if (direction.equals(Direction.IN) || direction.equals(Direction.BOTH))
            vertices.add(getInVertex());
        return vertices.iterator();
    }

    @Override
    public void remove() {
        this.sqlgGraph.tx().readWrite();
        if (this.removed)
            throw Element.Exceptions.elementAlreadyRemoved(this.getClass(), this.id());

        if (this.sqlgGraph.features().supportsBatchMode() && this.sqlgGraph.tx().isInBatchMode()) {
            this.sqlgGraph.tx().getBatchManager().removeEdge(this.schema, this.table, this);
        } else {
            super.remove();
        }

    }

    public SqlgVertex getInVertex() {
        if (this.inVertex == null) {
            load();
        }
        return this.inVertex;
    }

    public SqlgVertex getOutVertex() {
        if (this.outVertex == null) {
            load();
        }
        return this.outVertex;
    }

    @Override
    public String toString() {
        if (this.inVertex == null) {
            load();
        }
        return StringFactory.edgeString(this);
    }

    private void insertEdge(boolean complete, Pair<Map<String, Object>, Map<String, Object>> keyValueMapPair) throws SQLException {
        Map<String, Object> allKeyValueMap = keyValueMapPair.getLeft();
        Map<String, Object> notNullKeyValueMap = keyValueMapPair.getRight();
        if (this.sqlgGraph.features().supportsBatchMode() && this.sqlgGraph.tx().isInBatchMode()) {
            internalBatchAddEdge(complete, allKeyValueMap);
        } else {
            internalAddEdge(notNullKeyValueMap);
        }
        //Cache the properties
        this.properties.putAll(notNullKeyValueMap);
    }

    private void internalBatchAddEdge(boolean streaming, Map<String, Object> keyValueMap) {
        this.sqlgGraph.tx().getBatchManager().addEdge(streaming, this, this.outVertex, this.inVertex, keyValueMap);
    }

    private void internalAddEdge(Map<String, Object> keyValueMap) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes(this.schema));
        sql.append(".");
        sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes(SchemaManager.EDGE_PREFIX + this.table));
        sql.append(" (");

        Map<String, Pair<PropertyColumn, Object>> propertyColumnValueMap = new HashMap<>();
        Map<String, PropertyColumn> propertyColumns = null;
        if (!keyValueMap.isEmpty()) {
            propertyColumns = this.sqlgGraph.getTopology()
                    .getSchema(this.schema).orElseThrow(() -> new IllegalStateException(String.format("Schema %s not found", this.schema)))
                    .getEdgeLabel(this.table).orElseThrow(() -> new IllegalStateException(String.format("EdgeLabel %s not found", this.table)))
                    .getProperties();

            //sync up the keyValueMap with its PropertyColumn
            for (Map.Entry<String, Object> keyValueEntry : keyValueMap.entrySet()) {
                PropertyColumn propertyColumn = propertyColumns.get(keyValueEntry.getKey());
                Pair<PropertyColumn, Object> propertyColumnObjectPair = Pair.of(propertyColumn, keyValueEntry.getValue());
                propertyColumnValueMap.put(keyValueEntry.getKey(), propertyColumnObjectPair);
            }
        }
        writeColumnNames(propertyColumnValueMap, sql);
        if (keyValueMap.size() > 0) {
            sql.append(", ");
        }
        sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes(this.inVertex.schema + "." + this.inVertex.table + SchemaManager.IN_VERTEX_COLUMN_END));
        sql.append(", ");
        sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes(this.outVertex.schema + "." + this.outVertex.table + SchemaManager.OUT_VERTEX_COLUMN_END));
        sql.append(") VALUES (");
        writeColumnParameters(propertyColumnValueMap, sql);
        if (keyValueMap.size() > 0) {
            sql.append(", ");
        }
        sql.append("?, ?");
        sql.append(")");
        if (this.sqlgGraph.getSqlDialect().needsSemicolon()) {
            sql.append(";");
        }
        if (logger.isDebugEnabled()) {
            logger.debug(sql.toString());
        }
        int i = 1;
        Connection conn = this.sqlgGraph.tx().getConnection();
        try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS)) {
            i = SqlgUtil.setKeyValuesAsParameterUsingPropertyColumn(this.sqlgGraph, i, preparedStatement, propertyColumnValueMap);
            preparedStatement.setLong(i++, this.inVertex.recordId.getId());
            preparedStatement.setLong(i, this.outVertex.recordId.getId());
            preparedStatement.executeUpdate();
            ResultSet generatedKeys = preparedStatement.getGeneratedKeys();
            if (generatedKeys.next()) {
                this.recordId = RecordId.from(SchemaTable.of(this.schema, this.table), generatedKeys.getLong(1));
            } else {
                throw new RuntimeException("Could not retrieve the id after an insert into " + SchemaManager.VERTICES);
            }
            if (!keyValueMap.isEmpty()) {
                insertGlobalUniqueIndex(keyValueMap, propertyColumns);
            }
        }
    }


    //TODO this needs optimizing, an edge created in the transaction need not go to the db to load itself again
    @Override
    protected void load() {
        //recordId can be null when in batchMode
        if (recordId != null && this.properties.isEmpty()) {

            if (this.sqlgGraph.tx().getBatchManager().isStreaming()) {
                throw new IllegalStateException("streaming is in progress, first flush or commit before querying.");
            }

            StringBuilder sql = new StringBuilder("SELECT * FROM ");
            sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes(this.schema));
            sql.append(".");
            sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes(SchemaManager.EDGE_PREFIX + this.table));
            sql.append(" WHERE ");
            sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes("ID"));
            sql.append(" = ?");
            if (this.sqlgGraph.getSqlDialect().needsSemicolon()) {
                sql.append(";");
            }
            Connection conn = this.sqlgGraph.tx().getConnection();
            if (logger.isDebugEnabled()) {
                logger.debug(sql.toString());
            }
            try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                preparedStatement.setLong(1, this.recordId.getId());
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    loadResultSet(resultSet);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void loadInVertex(ResultSet resultSet, String label, int columnIdx) throws SQLException {
        SchemaTable inVertexColumnName = SchemaTable.from(this.sqlgGraph, label, this.sqlgGraph.getSqlDialect().getPublicSchema());
        Long inId = resultSet.getLong(columnIdx);
        if (!resultSet.wasNull()) {
            this.inVertex = SqlgVertex.of(this.sqlgGraph, inId, inVertexColumnName.getSchema(), SqlgUtil.removeTrailingInId(inVertexColumnName.getTable()));
        }
    }

    public void loadOutVertex(ResultSet resultSet, String label, int columnIdx) throws SQLException {
        SchemaTable outVertexColumnName = SchemaTable.from(this.sqlgGraph, label, this.sqlgGraph.getSqlDialect().getPublicSchema());
        Long outId = resultSet.getLong(columnIdx);
        if (!resultSet.wasNull()) {
            this.outVertex = SqlgVertex.of(this.sqlgGraph, outId, outVertexColumnName.getSchema(), SqlgUtil.removeTrailingOutId(outVertexColumnName.getTable()));
        }
    }

    @Override
    public void loadResultSet(ResultSet resultSet) throws SQLException {
        SchemaTable inVertexColumnName = null;
        SchemaTable outVertexColumnName = null;
        int inVertexColumnIndex = 0;
        int outVertexColumnIndex = 0;
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            String columnName = resultSetMetaData.getColumnLabel(i);
            if (!columnName.equals("ID") &&
                    !columnName.endsWith(SchemaManager.OUT_VERTEX_COLUMN_END) &&
                    !columnName.endsWith(SchemaManager.IN_VERTEX_COLUMN_END)) {

                loadProperty(resultSet, columnName, i);
            }
            if (columnName.endsWith(SchemaManager.IN_VERTEX_COLUMN_END)) {
                inVertexColumnName = SchemaTable.from(this.sqlgGraph, columnName, this.sqlgGraph.getSqlDialect().getPublicSchema());
                inVertexColumnIndex = i;
            } else if (columnName.endsWith(SchemaManager.OUT_VERTEX_COLUMN_END)) {
                outVertexColumnName = SchemaTable.from(this.sqlgGraph, columnName, this.sqlgGraph.getSqlDialect().getPublicSchema());
                outVertexColumnIndex = i;
            }
        }
        if (inVertexColumnName == null || inVertexColumnIndex == 0 || outVertexColumnName == null || outVertexColumnIndex == 0) {
            throw new IllegalStateException("in or out vertex id not set!!!!");
        }
        Long inId = resultSet.getLong(inVertexColumnIndex);
        Long outId = resultSet.getLong(outVertexColumnIndex);

        this.inVertex = SqlgVertex.of(this.sqlgGraph, inId, inVertexColumnName.getSchema(), SqlgUtil.removeTrailingInId(inVertexColumnName.getTable()));
        this.outVertex = SqlgVertex.of(this.sqlgGraph, outId, outVertexColumnName.getSchema(), SqlgUtil.removeTrailingOutId(outVertexColumnName.getTable()));
    }


    @Override
    public <V> Iterator<Property<V>> properties(final String... propertyKeys) {
        return (Iterator) super.properties(propertyKeys);
    }

    @Override
    public Iterator<Vertex> vertices(Direction direction) {
        SqlgEdge.this.sqlgGraph.tx().readWrite();
        return internalGetVertices(direction);
    }

    @Override
    SchemaTable getSchemaTablePrefixed() {
        return SchemaTable.of(this.getSchema(), SchemaManager.EDGE_PREFIX + this.getTable());
    }
}
