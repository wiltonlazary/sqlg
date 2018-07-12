package org.umlg.sqlg.sql.dialect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.structure.*;
import org.umlg.sqlg.structure.topology.GlobalUniqueIndex;
import org.umlg.sqlg.structure.topology.PropertyColumn;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.util.SqlgUtil;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;

import static org.umlg.sqlg.structure.topology.Topology.*;

/**
 * Date: 2014/08/21
 * Time: 6:52 PM
 */
public abstract class BaseSqlDialect implements SqlDialect, SqlBulkDialect, SqlSchemaChangeDialect {

    protected Logger logger = LoggerFactory.getLogger(getClass().getName());

    public BaseSqlDialect() {
    }

    public void validateColumnName(String column) {
        if (column.endsWith(IN_VERTEX_COLUMN_END) || column.endsWith(OUT_VERTEX_COLUMN_END)) {
            throw SqlgExceptions.invalidColumnName("Column names may not end with " + IN_VERTEX_COLUMN_END + " or " + OUT_VERTEX_COLUMN_END + ". column = " + column);
        }
    }

    @Override
    public List<String> getSchemaNames(DatabaseMetaData metaData) {
        List<String> schemaNames = new ArrayList<>();
        try {
            try (ResultSet schemaRs = metaData.getSchemas()) {
                while (schemaRs.next()) {
                    String schema = schemaRs.getString(1);
                    if (!this.getInternalSchemas().contains(schema)) {
                        schemaNames.add(schema);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return schemaNames;
    }


    @Override
    public List<Triple<String, String, String>> getVertexTables(DatabaseMetaData metaData) {
        List<Triple<String, String, String>> vertexTables = new ArrayList<>();
        String[] types = new String[]{"TABLE"};
        try {
            //load the vertices
            try (ResultSet vertexRs = metaData.getTables(null, null, "V_%", types)) {
                while (vertexRs.next()) {
                    String tblCat = vertexRs.getString(1);
                    String schema = vertexRs.getString(2);
                    String table = vertexRs.getString(3);

                    //verify the table name matches our pattern
                    if (!table.startsWith("V_")) {
                        continue;
                    }
                    vertexTables.add(Triple.of(tblCat, schema, table));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return vertexTables;
    }

    @Override
    public List<Triple<String, String, String>> getEdgeTables(DatabaseMetaData metaData) {
        List<Triple<String, String, String>> edgeTables = new ArrayList<>();
        String[] types = new String[]{"TABLE"};
        try {
            //load the edges without their properties
            try (ResultSet edgeRs = metaData.getTables(null, null, "E_%", types)) {
                while (edgeRs.next()) {
                    String edgCat = edgeRs.getString(1);
                    String schema = edgeRs.getString(2);
                    String table = edgeRs.getString(3);
                    //verify the table name matches our pattern
                    if (!table.startsWith("E_")) {
                        continue;
                    }
                    edgeTables.add(Triple.of(edgCat, schema, table));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return edgeTables;
    }

    @Override
    public void flushVertexCache(SqlgGraph sqlgGraph, Map<SchemaTable, Pair<SortedSet<String>, Map<SqlgVertex, Map<String, Object>>>> vertexCache) {
        for (Map.Entry<SchemaTable, Pair<SortedSet<String>, Map<SqlgVertex, Map<String, Object>>>> entry : vertexCache.entrySet()) {
            SchemaTable schemaTable = entry.getKey();
            Pair<SortedSet<String>, Map<SqlgVertex, Map<String, Object>>> vertices = entry.getValue();
            SortedSet<String> columns = vertices.getLeft();
            Map<SqlgVertex, Map<String, Object>> rows = vertices.getRight();

            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO ");
            if (!schemaTable.isTemporary() || sqlgGraph.getSqlDialect().needsTemporaryTableSchema()) {
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(schemaTable.getSchema()));
                sql.append(".");
            }
            if (!schemaTable.isTemporary() || !sqlgGraph.getSqlDialect().needsTemporaryTablePrefix()) {
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(VERTEX_PREFIX + schemaTable.getTable()));
            } else {
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(
                        sqlgGraph.getSqlDialect().temporaryTablePrefix() +
                        VERTEX_PREFIX + schemaTable.getTable()));
            }

            Map<String, PropertyColumn> propertyColumns = null;
            Map<String, PropertyType> properties = null;
            if (!schemaTable.isTemporary()) {
                propertyColumns = sqlgGraph.getTopology()
                        .getSchema(schemaTable.getSchema()).orElseThrow(() -> new IllegalStateException(String.format("Schema %s not found", schemaTable.getSchema())))
                        .getVertexLabel(schemaTable.getTable()).orElseThrow(() -> new IllegalStateException(String.format("VertexLabel %s not found", schemaTable.getTable())))
                        .getProperties();

            } else {
                properties = sqlgGraph.getTopology().getPublicSchema().getTemporaryTable(VERTEX_PREFIX + schemaTable.getTable());
            }
            if (!columns.isEmpty()) {
                Map<String, PropertyType> propertyTypeMap = new HashMap<>();
                for (String column : columns) {
                    if (!schemaTable.isTemporary()) {
                        PropertyColumn propertyColumn = propertyColumns.get(column);
                        propertyTypeMap.put(column, propertyColumn.getPropertyType());
                    } else {
                        propertyTypeMap.put(column, properties.get(column));
                    }
                }
                sql.append(" (");
                int i = 1;
                //noinspection Duplicates
                for (String column : columns) {
                    PropertyType propertyType = propertyTypeMap.get(column);
                    String[] sqlDefinitions = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(propertyType);
                    int count = 1;
                    for (@SuppressWarnings("unused") String sqlDefinition : sqlDefinitions) {
                        if (count > 1) {
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column + propertyType.getPostFixes()[count - 2]));
                        } else {
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column));
                        }
                        if (count++ < sqlDefinitions.length) {
                            sql.append(",");
                        }
                    }
                    if (i++ < columns.size()) {
                        sql.append(", ");
                    }
                }
                sql.append(") VALUES ( ");

                i = 1;
                //noinspection Duplicates
                for (String column : columns) {
                    PropertyType propertyType = propertyTypeMap.get(column);
                    String[] sqlDefinitions = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(propertyType);
                    int count = 1;
                    //noinspection Duplicates
                    for (@SuppressWarnings("unused") String sqlDefinition : sqlDefinitions) {
                        if (count > 1) {
                            sql.append("?");
                        } else {
                            sql.append("?");
                        }
                        if (count++ < sqlDefinitions.length) {
                            sql.append(",");
                        }
                    }
                    if (i++ < columns.size()) {
                        sql.append(", ");
                    }
                }
                sql.append(")");
            } else {
                sql.append(sqlgGraph.getSqlDialect().sqlInsertEmptyValues());
            }
            if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                sql.append(";");
            }
            if (logger.isDebugEnabled()) {
                logger.debug(sql.toString());
            }
            Connection conn = sqlgGraph.tx().getConnection();
            try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS)) {
                List<SqlgVertex> sqlgVertices = new ArrayList<>();
                for (Map.Entry<SqlgVertex, Map<String, Object>> rowEntry : rows.entrySet()) {
                    int i = 1;
                    SqlgVertex sqlgVertex = rowEntry.getKey();
                    sqlgVertices.add(sqlgVertex);
                    if (!columns.isEmpty()) {
                        Map<String, Object> parameterValueMap = rowEntry.getValue();
                        List<Pair<PropertyType, Object>> typeAndValues = new ArrayList<>();
                        for (String column : columns) {
                            if (!schemaTable.isTemporary()) {
                                PropertyColumn propertyColumn = propertyColumns.get(column);
                                typeAndValues.add(Pair.of(propertyColumn.getPropertyType(), parameterValueMap.get(column)));
                            } else {
                                typeAndValues.add(Pair.of(properties.get(column), parameterValueMap.get(column)));
                            }
                        }
                        SqlgUtil.setKeyValuesAsParameterUsingPropertyColumn(sqlgGraph, true, i, preparedStatement, typeAndValues);
                    }
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                ResultSet generatedKeys = preparedStatement.getGeneratedKeys();
                int i = 0;
                while (generatedKeys.next()) {
                    sqlgVertices.get(i++).setInternalPrimaryKey(RecordId.from(schemaTable, generatedKeys.getLong(1)));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void flushEdgeCache(SqlgGraph sqlgGraph, Map<MetaEdge, Pair<SortedSet<String>, Map<SqlgEdge, Triple<SqlgVertex, SqlgVertex, Map<String, Object>>>>> edgeCache) {
        for (MetaEdge metaEdge : edgeCache.keySet()) {
            Pair<SortedSet<String>, Map<SqlgEdge, Triple<SqlgVertex, SqlgVertex, Map<String, Object>>>> triples = edgeCache.get(metaEdge);
            Map<String, PropertyType> propertyTypeMap = sqlgGraph.getTopology().getTableFor(metaEdge.getSchemaTable().withPrefix(EDGE_PREFIX));
            SortedSet<String> columns = triples.getLeft();
            Map<SqlgEdge, Triple<SqlgVertex, SqlgVertex, Map<String, Object>>> rows = triples.getRight();

            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(metaEdge.getSchemaTable().getSchema()));
            sql.append(".");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(EDGE_PREFIX + metaEdge.getSchemaTable().getTable()));
            sql.append(" (");

            Map<String, PropertyColumn> propertyColumns = sqlgGraph.getTopology()
                    .getSchema(metaEdge.getSchemaTable().getSchema()).orElseThrow(() -> new IllegalStateException(String.format("Schema %s not found", metaEdge.getSchemaTable().getSchema())))
                    .getEdgeLabel(metaEdge.getSchemaTable().getTable()).orElseThrow(() -> new IllegalStateException(String.format("EdgeLabel %s not found", metaEdge.getSchemaTable().getTable())))
                    .getProperties();

            int i = 1;
            for (String column : columns) {
                PropertyType propertyType = propertyTypeMap.get(column);
                String[] sqlDefinitions = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(propertyType);
                int count = 1;
                for (@SuppressWarnings("unused") String sqlDefinition : sqlDefinitions) {
                    if (count > 1) {
                        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column + propertyType.getPostFixes()[count - 2]));
                    } else {
                        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column));
                    }
                    if (count++ < sqlDefinitions.length) {
                        sql.append(",");
                    }
                }
                if (i++ < columns.size()) {
                    sql.append(", ");
                }
            }
            if (!columns.isEmpty()) {
                sql.append(", ");
            }
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(metaEdge.getOutLabel() + OUT_VERTEX_COLUMN_END));
            sql.append(", ");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(metaEdge.getInLabel() + IN_VERTEX_COLUMN_END));
            sql.append(") VALUES (");

            i = 1;
            for (String column : columns) {
                PropertyType propertyType = propertyTypeMap.get(column);
                String[] sqlDefinitions = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(propertyType);
                int count = 1;
                //noinspection Duplicates
                for (@SuppressWarnings("unused") String sqlDefinition : sqlDefinitions) {
                    if (count > 1) {
                        sql.append("?");
                    } else {
                        sql.append("?");
                    }
                    if (count++ < sqlDefinitions.length) {
                        sql.append(",");
                    }
                }
                if (i++ < columns.size()) {
                    sql.append(", ");
                }
            }
            if (!columns.isEmpty()) {
                sql.append(", ");
            }
            sql.append("?, ?");
            sql.append(")");
            if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                sql.append(";");
            }
            if (logger.isDebugEnabled()) {
                logger.debug(sql.toString());
            }
            Connection conn = sqlgGraph.tx().getConnection();
            try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS)) {
                List<SqlgEdge> sqlgEdges = new ArrayList<>();
                for (Map.Entry<SqlgEdge, Triple<SqlgVertex, SqlgVertex, Map<String, Object>>> rowEntry : rows.entrySet()) {
                    i = 1;
                    SqlgEdge sqlgEdge = rowEntry.getKey();
                    sqlgEdges.add(sqlgEdge);
                    Triple<SqlgVertex, SqlgVertex, Map<String, Object>> parameterValueMap = rowEntry.getValue();
                    List<Pair<PropertyType, Object>> typeAndValues = new ArrayList<>();
                    for (String column : columns) {
                        PropertyColumn propertyColumn = propertyColumns.get(column);
                        typeAndValues.add(Pair.of(propertyColumn.getPropertyType(), parameterValueMap.getRight().get(column)));
                    }
                    i = SqlgUtil.setKeyValuesAsParameterUsingPropertyColumn(sqlgGraph, true, i, preparedStatement, typeAndValues);
                    preparedStatement.setLong(i++, ((RecordId) parameterValueMap.getLeft().id()).getId());
                    preparedStatement.setLong(i, ((RecordId) parameterValueMap.getMiddle().id()).getId());
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                ResultSet generatedKeys = preparedStatement.getGeneratedKeys();
                i = 0;
                while (generatedKeys.next()) {
                    sqlgEdges.get(i++).setInternalPrimaryKey(RecordId.from(metaEdge.getSchemaTable(), generatedKeys.getLong(1)));
                }
//                insertGlobalUniqueIndex(keyValueMap, propertyColumns);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void flushVertexPropertyCache(SqlgGraph sqlgGraph, Map<SchemaTable, Pair<SortedSet<String>, Map<SqlgVertex, Map<String, Object>>>> vertexPropertyCache) {
        for (Map.Entry<SchemaTable, Pair<SortedSet<String>, Map<SqlgVertex, Map<String, Object>>>> entry : vertexPropertyCache.entrySet()) {
            SchemaTable schemaTable = entry.getKey();
            Pair<SortedSet<String>, Map<SqlgVertex, Map<String, Object>>> vertices = entry.getValue();
            SortedSet<String> columns = vertices.getLeft();
            Map<SqlgVertex, Map<String, Object>> rows = vertices.getRight();

            StringBuilder sql = new StringBuilder();
            sql.append("UPDATE ");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(schemaTable.getSchema()));
            sql.append(".");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(VERTEX_PREFIX + schemaTable.getTable()));
            sql.append(" SET ");

            Map<String, PropertyColumn> propertyColumns = sqlgGraph.getTopology()
                    .getSchema(schemaTable.getSchema()).orElseThrow(() -> new IllegalStateException(String.format("Schema %s not found", schemaTable.getSchema())))
                    .getVertexLabel(schemaTable.getTable()).orElseThrow(() -> new IllegalStateException(String.format("VertexLabel %s not found", schemaTable.getTable())))
                    .getProperties();
            if (!columns.isEmpty()) {
                Map<String, PropertyType> propertyTypeMap = new HashMap<>();
                for (String column : columns) {
                    PropertyColumn propertyColumn = propertyColumns.get(column);
                    propertyTypeMap.put(column, propertyColumn.getPropertyType());
                }
                sql.append(" ");
                int i = 1;
                //noinspection Duplicates
                for (String column : columns) {
                    PropertyType propertyType = propertyTypeMap.get(column);
                    String[] sqlDefinitions = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(propertyType);
                    int count = 1;
                    for (@SuppressWarnings("unused") String sqlDefinition : sqlDefinitions) {
                        if (count > 1) {
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column + propertyType.getPostFixes()[count - 2]));
                            sql.append(" = ?");
                        } else {
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column));
                            sql.append(" = ?");
                        }
                        if (count++ < sqlDefinitions.length) {
                            sql.append(",");
                        }
                    }
                    if (i++ < columns.size()) {
                        sql.append(", ");
                    }
                }
            }
            sql.append(" WHERE ");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(Topology.ID));
            sql.append(" = ?");
            if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                sql.append(";");
            }
            if (logger.isDebugEnabled()) {
                logger.debug(sql.toString());
            }
            Connection conn = sqlgGraph.tx().getConnection();
            try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                for (Map.Entry<SqlgVertex, Map<String, Object>> rowEntry : rows.entrySet()) {
                    int i = 1;
                    SqlgVertex sqlgVertex = rowEntry.getKey();
                    if (!columns.isEmpty()) {
                        Map<String, Object> parameterValueMap = rowEntry.getValue();
                        List<Pair<PropertyType, Object>> typeAndValues = new ArrayList<>();
                        for (String column : columns) {
                            PropertyColumn propertyColumn = propertyColumns.get(column);
                            Object value = parameterValueMap.get(column);
                            if (value == null) {
                                //if the value is not present update it to what is currently is.
                                if (sqlgVertex.property(column).isPresent()) {
                                    value = sqlgVertex.value(column);
                                } else {
                                    value = null;
                                }
                            }
                            typeAndValues.add(Pair.of(propertyColumn.getPropertyType(), value));
                        }
                        i = SqlgUtil.setKeyValuesAsParameterUsingPropertyColumn(sqlgGraph, true, i, preparedStatement, typeAndValues);
                        preparedStatement.setLong(i, ((RecordId) sqlgVertex.id()).getId());
                    }
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void flushEdgePropertyCache(SqlgGraph sqlgGraph, Map<SchemaTable, Pair<SortedSet<String>, Map<SqlgEdge, Map<String, Object>>>> edgePropertyCache) {
        for (Map.Entry<SchemaTable, Pair<SortedSet<String>, Map<SqlgEdge, Map<String, Object>>>> entry : edgePropertyCache.entrySet()) {
            SchemaTable schemaTable = entry.getKey();
            Pair<SortedSet<String>, Map<SqlgEdge, Map<String, Object>>> edges = entry.getValue();
            SortedSet<String> columns = edges.getLeft();
            Map<SqlgEdge, Map<String, Object>> rows = edges.getRight();

            StringBuilder sql = new StringBuilder();
            sql.append("UPDATE ");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(schemaTable.getSchema()));
            sql.append(".");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(EDGE_PREFIX + schemaTable.getTable()));
            sql.append(" SET ");

            Map<String, PropertyColumn> propertyColumns = sqlgGraph.getTopology()
                    .getSchema(schemaTable.getSchema()).orElseThrow(() -> new IllegalStateException(String.format("Schema %s not found", schemaTable.getSchema())))
                    .getEdgeLabel(schemaTable.getTable()).orElseThrow(() -> new IllegalStateException(String.format("EdgeLabel %s not found", schemaTable.getTable())))
                    .getProperties();
            if (!columns.isEmpty()) {
                Map<String, PropertyType> propertyTypeMap = new HashMap<>();
                for (String column : columns) {
                    PropertyColumn propertyColumn = propertyColumns.get(column);
                    propertyTypeMap.put(column, propertyColumn.getPropertyType());
                }
                sql.append(" ");
                int i = 1;
                //noinspection Duplicates
                for (String column : columns) {
                    PropertyType propertyType = propertyTypeMap.get(column);
                    String[] sqlDefinitions = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(propertyType);
                    int count = 1;
                    for (@SuppressWarnings("unused") String sqlDefinition : sqlDefinitions) {
                        if (count > 1) {
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column + propertyType.getPostFixes()[count - 2]));
                            sql.append(" = ?");
                        } else {
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column));
                            sql.append(" = ?");
                        }
                        if (count++ < sqlDefinitions.length) {
                            sql.append(",");
                        }
                    }
                    if (i++ < columns.size()) {
                        sql.append(", ");
                    }
                }
            }
            sql.append(" WHERE ");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(Topology.ID));
            sql.append(" = ?");
            if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                sql.append(";");
            }
            if (logger.isDebugEnabled()) {
                logger.debug(sql.toString());
            }
            Connection conn = sqlgGraph.tx().getConnection();
            try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                for (Map.Entry<SqlgEdge, Map<String, Object>> rowEntry : rows.entrySet()) {
                    int i = 1;
                    SqlgEdge sqlgEdge = rowEntry.getKey();
                    if (!columns.isEmpty()) {
                        Map<String, Object> parameterValueMap = rowEntry.getValue();
                        List<Pair<PropertyType, Object>> typeAndValues = new ArrayList<>();
                        for (String column : columns) {
                            PropertyColumn propertyColumn = propertyColumns.get(column);
                            Object value = parameterValueMap.get(column);
                            if (value == null) {
                                //if the value is not present update it to what is currently is.
                                if (sqlgEdge.property(column).isPresent()) {
                                    value = sqlgEdge.value(column);
                                } else {
                                    value = null;
                                }
                            }
                            typeAndValues.add(Pair.of(propertyColumn.getPropertyType(), value));
                        }
                        i = SqlgUtil.setKeyValuesAsParameterUsingPropertyColumn(sqlgGraph, true, i, preparedStatement, typeAndValues);
                        preparedStatement.setLong(i, ((RecordId) sqlgEdge.id()).getId());
                    }
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void flushRemovedVertices(SqlgGraph sqlgGraph, Map<SchemaTable, List<SqlgVertex>> removeVertexCache) {

        if (!removeVertexCache.isEmpty()) {


            //split the list of vertices, postgres existVertexLabel a 2 byte limit in the in clause
            for (Map.Entry<SchemaTable, List<SqlgVertex>> schemaVertices : removeVertexCache.entrySet()) {

                SchemaTable schemaTable = schemaVertices.getKey();

                Pair<Set<SchemaTable>, Set<SchemaTable>> tableLabels = sqlgGraph.getTopology().getTableLabels(SchemaTable.of(schemaTable.getSchema(), VERTEX_PREFIX + schemaTable.getTable()));

                //This is causing dead locks under load
//                dropForeignKeys(sqlgGraph, schemaTable);

                List<SqlgVertex> vertices = schemaVertices.getValue();
                int numberOfLoops = (vertices.size() / sqlInParameterLimit());
                int previous = 0;
                for (int i = 1; i <= numberOfLoops + 1; i++) {

                    int subListTo = i * sqlInParameterLimit();
                    List<SqlgVertex> subVertices;
                    if (i <= numberOfLoops) {
                        subVertices = vertices.subList(previous, subListTo);
                    } else {
                        subVertices = vertices.subList(previous, vertices.size());
                    }

                    previous = subListTo;

                    if (!subVertices.isEmpty()) {

                        Set<SchemaTable> inLabels = tableLabels.getLeft();
                        Set<SchemaTable> outLabels = tableLabels.getRight();

                        deleteEdges(sqlgGraph, schemaTable, subVertices, inLabels, true);
                        deleteEdges(sqlgGraph, schemaTable, subVertices, outLabels, false);

                        StringBuilder sql = new StringBuilder("DELETE FROM ");
                        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(schemaTable.getSchema()));
                        sql.append(".");
                        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes((VERTEX_PREFIX) + schemaTable.getTable()));
                        sql.append(" WHERE ");
                        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes("ID"));
                        sql.append(" in (");
                        int count = 1;
                        for (SqlgVertex sqlgVertex : subVertices) {
                            sql.append("?");
                            if (count++ < subVertices.size()) {
                                sql.append(",");
                            }
                        }
                        sql.append(")");
                        if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                            sql.append(";");
                        }
                        if (logger.isDebugEnabled()) {
                            logger.debug(sql.toString());
                        }
                        Connection conn = sqlgGraph.tx().getConnection();
                        try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                            count = 1;
                            for (SqlgVertex sqlgVertex : subVertices) {
                                preparedStatement.setLong(count++, ((RecordId) sqlgVertex.id()).getId());
                            }
                            preparedStatement.executeUpdate();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
//                createForeignKeys(sqlgGraph, schemaTable);
            }
        }
    }

    @Override
    public void flushRemovedEdges(SqlgGraph sqlgGraph, Map<SchemaTable, List<SqlgEdge>> removeEdgeCache) {

        if (!removeEdgeCache.isEmpty()) {

            //split the list of edges, postgres existVertexLabel a 2 byte limit in the in clause
            for (Map.Entry<SchemaTable, List<SqlgEdge>> schemaEdges : removeEdgeCache.entrySet()) {

                List<SqlgEdge> edges = schemaEdges.getValue();
                int numberOfLoops = (edges.size() / sqlInParameterLimit());
                int previous = 0;
                for (int i = 1; i <= numberOfLoops + 1; i++) {

                    int subListTo = i * sqlInParameterLimit();
                    List<SqlgEdge> subEdges;
                    if (i <= numberOfLoops) {
                        subEdges = edges.subList(previous, subListTo);
                    } else {
                        subEdges = edges.subList(previous, edges.size());
                    }
                    previous = subListTo;

                    if (!subEdges.isEmpty()) {

                        for (SchemaTable schemaTable : removeEdgeCache.keySet()) {
                            StringBuilder sql = new StringBuilder("DELETE FROM ");
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(schemaTable.getSchema()));
                            sql.append(".");
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes((EDGE_PREFIX) + schemaTable.getTable()));
                            sql.append(" WHERE ");
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes("ID"));
                            sql.append(" in (");
                            int count = 1;
                            for (@SuppressWarnings("unused") SqlgEdge sqlgEdge : subEdges) {
                                sql.append("?");
                                if (count++ < subEdges.size()) {
                                    sql.append(",");
                                }
                            }
                            sql.append(")");
                            if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                                sql.append(";");
                            }
                            if (logger.isDebugEnabled()) {
                                logger.debug(sql.toString());
                            }
                            Connection conn = sqlgGraph.tx().getConnection();
                            try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                                count = 1;
                                for (SqlgEdge sqlgEdge : subEdges) {
                                    preparedStatement.setLong(count++, ((RecordId) sqlgEdge.id()).getId());
                                }
                                preparedStatement.executeUpdate();
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        }
    }

    private void deleteEdges(SqlgGraph sqlgGraph, SchemaTable schemaTable, List<SqlgVertex> subVertices, Set<SchemaTable> labels, boolean inDirection) {
        for (SchemaTable inLabel : labels) {

            StringBuilder sql = new StringBuilder();
            sql.append("DELETE FROM ");
            sql.append(maybeWrapInQoutes(inLabel.getSchema()));
            sql.append(".");
            sql.append(maybeWrapInQoutes(inLabel.getTable()));
            sql.append(" WHERE ");
            sql.append(maybeWrapInQoutes(schemaTable.toString() + (inDirection ? IN_VERTEX_COLUMN_END : OUT_VERTEX_COLUMN_END)));
            sql.append(" IN (");
            int count = 1;
            for (Vertex vertexToDelete : subVertices) {
                sql.append("?");
                if (count++ < subVertices.size()) {
                    sql.append(",");
                }
            }
            sql.append(")");
            if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                sql.append(";");
            }
            if (logger.isDebugEnabled()) {
                logger.debug(sql.toString());
            }
            Connection conn = sqlgGraph.tx().getConnection();
            try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                count = 1;
                for (Vertex vertexToDelete : subVertices) {
                    preparedStatement.setLong(count++, ((RecordId) vertexToDelete.id()).getId());
                }
                int deleted = preparedStatement.executeUpdate();
                if (logger.isDebugEnabled()) {
                    logger.debug("Deleted " + deleted + " edges from " + inLabel.toString());
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public int sqlInParameterLimit() {
        return 1000;
    }

    @Override
    public List<Triple<String, Integer, String>> getTableColumns(DatabaseMetaData metaData, String catalog, String schemaPattern,
                                                                 String tableNamePattern, String columnNamePattern) {
        List<Triple<String, Integer, String>> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern)) {
            while (rs.next()) {
                String columnName = rs.getString(4);
                int columnType = rs.getInt(5);
                String typeName = rs.getString("TYPE_NAME");
                columns.add(Triple.of(columnName, columnType, typeName));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return columns;
    }

    @Override
    public List<Triple<String, Boolean, String>> getIndexInfo(DatabaseMetaData metaData, String catalog,
                                                              String schema, String table, boolean unique, boolean approximate) {
        List<Triple<String, Boolean, String>> indexes = new ArrayList<>();
        try (ResultSet indexRs = metaData.getIndexInfo(null, schema, table, false, true)) {
            while (indexRs.next()) {
                String indexName = indexRs.getString("INDEX_NAME");
                boolean nonUnique = indexRs.getBoolean("NON_UNIQUE");
                String columnName = indexRs.getString("COLUMN_NAME");
                indexes.add(Triple.of(indexName, nonUnique, columnName));
            }
            return indexes;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void flushVertexGlobalUniqueIndexes(SqlgGraph sqlgGraph, Map<SchemaTable, Pair<SortedSet<String>, Map<SqlgVertex, Map<String, Object>>>> vertexCache) {
        for (SchemaTable schemaTable : vertexCache.keySet()) {
            Pair<SortedSet<String>, Map<SqlgVertex, Map<String, Object>>> vertices = vertexCache.get(schemaTable);

            Map<String, PropertyColumn> propertyColumnMap = sqlgGraph.getTopology().getPropertiesFor(schemaTable.withPrefix(VERTEX_PREFIX));
            for (Map.Entry<String, PropertyColumn> propertyColumnEntry : propertyColumnMap.entrySet()) {
                PropertyColumn propertyColumn = propertyColumnEntry.getValue();
                for (GlobalUniqueIndex globalUniqueIndex : propertyColumn.getGlobalUniqueIndices()) {
                    Map<SqlgVertex, Map<String, Object>> rows = vertices.getRight();
                    StringBuilder sql = new StringBuilder();
                    sql.append("INSERT INTO ");
                    sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(Schema.GLOBAL_UNIQUE_INDEX_SCHEMA));
                    sql.append(".");
                    sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(VERTEX_PREFIX + globalUniqueIndex.getName()));
                    sql.append(" (");
                    PropertyType propertyType = propertyColumn.getPropertyType();
                    String[] sqlDefinitions = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(propertyType);
                    int count = 1;
                    for (@SuppressWarnings("unused") String sqlDefinition : sqlDefinitions) {
                        if (count++ > 1) {
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(GlobalUniqueIndex.GLOBAL_UNIQUE_INDEX_VALUE + propertyType.getPostFixes()[count - 2]));
                        } else {
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(GlobalUniqueIndex.GLOBAL_UNIQUE_INDEX_VALUE));
                        }
                        sql.append(",");
                    }
                    sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(GlobalUniqueIndex.GLOBAL_UNIQUE_INDEX_RECORD_ID));
                    sql.append(",");
                    sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(GlobalUniqueIndex.GLOBAL_UNIQUE_INDEX_PROPERTY_NAME));
                    sql.append(") VALUES ( ");
                    count = 1;
                    //noinspection Duplicates
                    for (@SuppressWarnings("unused") String sqlDefinition : sqlDefinitions) {
                        if (count++ > 1) {
                            sql.append("?");
                        } else {
                            sql.append("?");
                        }
                        sql.append(", ");
                    }
                    sql.append("?, ?)");
                    if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                        sql.append(";");
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug(sql.toString());
                    }
                    Connection conn = sqlgGraph.tx().getConnection();
                    try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                        for (Map.Entry<SqlgVertex, Map<String, Object>> rowEntry : rows.entrySet()) {
                            SqlgVertex sqlgVertex = rowEntry.getKey();
                            Map<String, Object> parameterValueMap = rowEntry.getValue();
                            Object value = parameterValueMap.get(propertyColumn.getName());
                            List<Pair<PropertyType, Object>> typeAndValues = new ArrayList<>();
                            typeAndValues.add(Pair.of(propertyColumn.getPropertyType(), value));
                            typeAndValues.add(Pair.of(PropertyType.STRING, sqlgVertex.id().toString()));
                            typeAndValues.add(Pair.of(PropertyType.STRING, propertyColumn.getName()));
                            SqlgUtil.setKeyValuesAsParameterUsingPropertyColumn(sqlgGraph, true, 1, preparedStatement, typeAndValues);
                            preparedStatement.addBatch();
                        }
                        preparedStatement.executeBatch();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public void flushEdgeGlobalUniqueIndexes(SqlgGraph sqlgGraph, Map<MetaEdge, Pair<SortedSet<String>, Map<SqlgEdge, Triple<SqlgVertex, SqlgVertex, Map<String, Object>>>>> edgeCache) {
        for (MetaEdge metaEdge : edgeCache.keySet()) {

            Pair<SortedSet<String>, Map<SqlgEdge, Triple<SqlgVertex, SqlgVertex, Map<String, Object>>>> triples = edgeCache.get(metaEdge);
            Map<SqlgEdge, Triple<SqlgVertex, SqlgVertex, Map<String, Object>>> edgeMap = triples.getRight();
            Map<String, PropertyColumn> propertyColumnMap = sqlgGraph.getTopology().getPropertiesFor(metaEdge.getSchemaTable().withPrefix(EDGE_PREFIX));
            Map<SqlgEdge, Triple<SqlgVertex, SqlgVertex, Map<String, Object>>> rows = triples.getRight();

            for (Map.Entry<String, PropertyColumn> propertyColumnEntry : propertyColumnMap.entrySet()) {
                PropertyColumn propertyColumn = propertyColumnEntry.getValue();
                for (GlobalUniqueIndex globalUniqueIndex : propertyColumn.getGlobalUniqueIndices()) {

                    StringBuilder sql = new StringBuilder();
                    sql.append("INSERT INTO ");
                    sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(Schema.GLOBAL_UNIQUE_INDEX_SCHEMA));
                    sql.append(".");
                    sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(VERTEX_PREFIX + globalUniqueIndex.getName()));
                    sql.append(" (");
                    PropertyType propertyType = propertyColumn.getPropertyType();
                    String[] sqlDefinitions = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(propertyType);
                    int count = 1;
                    for (@SuppressWarnings("unused") String sqlDefinition : sqlDefinitions) {
                        if (count++ > 1) {
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(GlobalUniqueIndex.GLOBAL_UNIQUE_INDEX_VALUE + propertyType.getPostFixes()[count - 2]));
                        } else {
                            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(GlobalUniqueIndex.GLOBAL_UNIQUE_INDEX_VALUE));
                        }
                        sql.append(",");
                    }
                    sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(GlobalUniqueIndex.GLOBAL_UNIQUE_INDEX_RECORD_ID));
                    sql.append(",");
                    sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(GlobalUniqueIndex.GLOBAL_UNIQUE_INDEX_PROPERTY_NAME));
                    sql.append(") VALUES ( ");
                    count = 1;
                    //noinspection Duplicates
                    for (@SuppressWarnings("unused") String sqlDefinition : sqlDefinitions) {
                        if (count++ > 1) {
                            sql.append("?");
                        } else {
                            sql.append("?");
                        }
                        sql.append(", ");
                    }
                    sql.append("?, ?)");
                    if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                        sql.append(";");
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug(sql.toString());
                    }

                    Connection conn = sqlgGraph.tx().getConnection();
                    try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                        for (Map.Entry<SqlgEdge, Triple<SqlgVertex, SqlgVertex, Map<String, Object>>> rowEntry : rows.entrySet()) {
                            SqlgEdge sqlgEdge = rowEntry.getKey();
                            Map<String, Object> parameterValueMap = rowEntry.getValue().getRight();
                            Object value = parameterValueMap.get(propertyColumn.getName());
                            List<Pair<PropertyType, Object>> typeAndValues = new ArrayList<>();
                            typeAndValues.add(Pair.of(propertyColumn.getPropertyType(), value));
                            typeAndValues.add(Pair.of(PropertyType.STRING, sqlgEdge.id().toString()));
                            typeAndValues.add(Pair.of(PropertyType.STRING, propertyColumn.getName()));
                            SqlgUtil.setKeyValuesAsParameterUsingPropertyColumn(sqlgGraph, true, 1, preparedStatement, typeAndValues);
                            preparedStatement.addBatch();
                        }
                        preparedStatement.executeBatch();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public void flushRemovedGlobalUniqueIndexVertices(SqlgGraph sqlgGraph, Map<SchemaTable, List<SqlgVertex>> removeVertexCache) {

        if (!removeVertexCache.isEmpty()) {

            //split the list of vertices, postgres existVertexLabel a 2 byte limit in the in clause
            for (Map.Entry<SchemaTable, List<SqlgVertex>> schemaVertices : removeVertexCache.entrySet()) {

                SchemaTable schemaTable = schemaVertices.getKey();
                Map<String, PropertyColumn> propertyColumns = sqlgGraph.getTopology().getPropertiesWithGlobalUniqueIndexFor(schemaTable.withPrefix(VERTEX_PREFIX));
                for (PropertyColumn propertyColumn : propertyColumns.values()) {
                    for (GlobalUniqueIndex globalUniqueIndex : propertyColumn.getGlobalUniqueIndices()) {
                        List<SqlgVertex> vertices = schemaVertices.getValue();


                        SecureRandom random = new SecureRandom();
                        byte bytes[] = new byte[6];
                        random.nextBytes(bytes);
                        String tmpTableIdentified = Base64.getEncoder().encodeToString(bytes);
                        StringBuilder createTmpTableStatement = new StringBuilder(createTemporaryTableStatement());
                        createTmpTableStatement.append(maybeWrapInQoutes(tmpTableIdentified));
                        createTmpTableStatement.append("(");
                        createTmpTableStatement.append(maybeWrapInQoutes("recordId"));
                        createTmpTableStatement.append(" ");
                        createTmpTableStatement.append(propertyTypeToSqlDefinition(PropertyType.STRING)[0]);
                        createTmpTableStatement.append(", ");
                        createTmpTableStatement.append(maybeWrapInQoutes("property"));
                        createTmpTableStatement.append(" ");
                        createTmpTableStatement.append(propertyTypeToSqlDefinition(PropertyType.STRING)[0]);
                        createTmpTableStatement.append(")");
                        if (needsSemicolon()) {
                            createTmpTableStatement.append(";");
                        }
                        if (logger.isDebugEnabled()) {
                            logger.debug(createTmpTableStatement.toString());
                        }
                        Connection conn = sqlgGraph.tx().getConnection();
                        try (Statement statement = conn.createStatement()) {
                            statement.execute(createTmpTableStatement.toString());
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                        StringBuilder sql = new StringBuilder("INSERT INTO ");
                        if (needsTemporaryTableSchema()) {
                            sql.append(maybeWrapInQoutes(getPublicSchema()));
                            sql.append(".");
                        }
                        sql.append(maybeWrapInQoutes(tmpTableIdentified));
                        sql.append(" (");
                        sql.append(maybeWrapInQoutes("recordId"));
                        sql.append(", ");
                        sql.append(maybeWrapInQoutes("property"));
                        sql.append(") VALUES (?, ?)");
                        if (needsSemicolon()) {
                            sql.append(";");
                        }
                        if (logger.isDebugEnabled()) {
                            logger.debug(sql.toString());
                        }
                        try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                            for (SqlgVertex vertex : vertices) {
                                preparedStatement.setString(1, vertex.id().toString());
                                preparedStatement.setString(2, propertyColumn.getName());
                                preparedStatement.addBatch();
                            }
                            preparedStatement.executeBatch();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                        sql = new StringBuilder("DELETE FROM\n\t");
                        sql.append(maybeWrapInQoutes(Schema.GLOBAL_UNIQUE_INDEX_SCHEMA));
                        sql.append(".");
                        sql.append(maybeWrapInQoutes(VERTEX_PREFIX + globalUniqueIndex.getName()));
                        sql.append("\nWHERE\n\t");
                        sql.append("CONCAT(");
                        sql.append(maybeWrapInQoutes("recordId"));
                        sql.append(", ");
                        sql.append(maybeWrapInQoutes("property"));
                        sql.append(") IN (\n");
                        sql.append("SELECT\n\tCONCAT(");
                        sql.append(maybeWrapInQoutes("recordId"));
                        sql.append(", ");
                        sql.append(maybeWrapInQoutes("property"));
                        sql.append(")\nFROM\n\t");
                        if (needsTemporaryTableSchema()) {
                            sql.append(maybeWrapInQoutes(sqlgGraph.getSqlDialect().getPublicSchema()));
                            sql.append(".");
                        }
                        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(tmpTableIdentified));
                        sql.append(")");
                        if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                            sql.append(";");
                        }
                        if (logger.isDebugEnabled()) {
                            logger.debug(sql.toString());
                        }
                        conn = sqlgGraph.tx().getConnection();
                        try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                            preparedStatement.executeUpdate();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void flushVertexGlobalUniqueIndexPropertyCache(SqlgGraph sqlgGraph, Map<SchemaTable, Pair<SortedSet<String>, Map<SqlgVertex, Map<String, Object>>>> schemaVertexPropertyCache) {
        flushElementGlobalUniqueIndexPropertyCache(sqlgGraph, true, schemaVertexPropertyCache);
    }

    @Override
    public void flushEdgeGlobalUniqueIndexPropertyCache(SqlgGraph sqlgGraph, Map<SchemaTable, Pair<SortedSet<String>, Map<SqlgEdge, Map<String, Object>>>> edgePropertyCache) {
        flushElementGlobalUniqueIndexPropertyCache(sqlgGraph, false, edgePropertyCache);
    }

    private <T extends SqlgElement> void flushElementGlobalUniqueIndexPropertyCache(SqlgGraph sqlgGraph, boolean forVertices, Map<SchemaTable, Pair<SortedSet<String>, Map<T, Map<String, Object>>>> schemaVertexPropertyCache) {

        Connection conn = sqlgGraph.tx().getConnection();
        for (SchemaTable schemaTable : schemaVertexPropertyCache.keySet()) {

            Pair<SortedSet<String>, Map<T, Map<String, Object>>> vertexPropertyCache = schemaVertexPropertyCache.get(schemaTable);
            SortedSet<String> propertyNames = vertexPropertyCache.getKey();
            Map<String, PropertyColumn> globalUniqueIndexPropertyMap = sqlgGraph.getTopology().getPropertiesWithGlobalUniqueIndexFor(schemaTable.withPrefix(VERTEX_PREFIX));

            for (Map.Entry<String, PropertyColumn> propertyColumnEntry : globalUniqueIndexPropertyMap.entrySet()) {
                PropertyColumn propertyColumn = propertyColumnEntry.getValue();
                if (propertyNames.contains(propertyColumn.getName())) {
                    for (GlobalUniqueIndex globalUniqueIndex : propertyColumn.getGlobalUniqueIndices()) {
                        StringBuilder sql = new StringBuilder();
                        sql.append("UPDATE ");
                        sql.append(maybeWrapInQoutes(Schema.GLOBAL_UNIQUE_INDEX_SCHEMA));
                        sql.append(".");
                        sql.append(maybeWrapInQoutes((forVertices ? VERTEX_PREFIX : EDGE_PREFIX) + globalUniqueIndex.getName()));
                        sql.append(" \nSET\n\t");
                        sql.append(maybeWrapInQoutes("value"));
                        sql.append(" = ?\nWHERE\n\t");
                        sql.append(maybeWrapInQoutes("recordId"));
                        sql.append(" = ? AND ");
                        sql.append(maybeWrapInQoutes("property"));
                        sql.append(" = ?");
                        if (needsSemicolon()) {
                            sql.append(";");
                        }
                        if (logger.isDebugEnabled()) {
                            logger.debug(sql.toString());
                        }
                        try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                            boolean foundSomething = false;
                            for (T t : vertexPropertyCache.getRight().keySet()) {
                                Object value = vertexPropertyCache.getRight().get(t).get(propertyColumn.getName());
                                if (value != null) {
                                    foundSomething = true;
                                    SqlgUtil.setKeyValuesAsParameterUsingPropertyColumn(sqlgGraph, true, 1, preparedStatement, Collections.singleton(Pair.of(propertyColumn.getPropertyType(), value)));
                                    preparedStatement.setString(2, t.id().toString());
                                    preparedStatement.setString(3, propertyColumn.getName());
                                    preparedStatement.addBatch();
                                }
                            }
                            if (foundSomething) {
                                preparedStatement.executeBatch();
                            }
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setJson(PreparedStatement preparedStatement, int parameterStartIndex, JsonNode right) {
        try {
            preparedStatement.setString(parameterStartIndex, right.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handleOther(Map<String, Object> properties, String columnName, Object o, PropertyType propertyType) {
        switch (propertyType) {
            case JSON:
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    JsonNode jsonNode = objectMapper.readTree(o.toString());
                    properties.put(columnName, jsonNode);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            default:
                throw new IllegalStateException("sqlgDialect.handleOther does not handle " + propertyType.name());
        }
    }

    /**
     * escape quotes by doubling them when we need a string inside quotes
     * @param o
     * @return
     */
    protected String escapeQuotes(Object o){
        if (o!=null){
            return o.toString().replace("'", "''");
        }
        return null;
    }
}
