package org.umlg.sqlg.structure;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tinkerpop.gremlin.structure.T;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.sql.dialect.SqlDialect;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.umlg.sqlg.structure.SchemaManager.EDGE_PREFIX;
import static org.umlg.sqlg.structure.Topology.SQLG_SCHEMA;
import static org.umlg.sqlg.structure.Topology.SQLG_SCHEMA_SCHEMA;

/**
 * This class contains the logic to ensure all is well on startup.
 * <p>
 * Date: 2016/11/14
 * Time: 10:22 AM
 */
class SqlgStartupManager {

    private Logger logger = LoggerFactory.getLogger(SqlgStartupManager.class.getName());
    private SqlgGraph sqlgGraph;
    private SqlDialect sqlDialect;

    SqlgStartupManager(SqlgGraph sqlgGraph) {
        this.sqlgGraph = sqlgGraph;
        this.sqlDialect = sqlgGraph.getSqlDialect();
    }

    void loadSchema() {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("SchemaManager.loadSchema()...");
            }
            //check if the topology schema exists, if not createVertexLabel it
            boolean existSqlgSchema = existSqlgSchema();
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            if (!existSqlgSchema) {
                //This exist separately because Hsqldb and H2 do not support "if exist" in the schema creation sql.
                createSqlgSchema();
            }
            if (!existGuiSchema()) {
                createGuiSchema();
            }
            if (!existSqlgSchema) {
                createSqlgSchemaTablesAndIndexes();
            }
            //committing here will ensure that sqlg creates the tables.
            this.sqlgGraph.tx().commit();
            stopWatch.stop();
            logger.debug("Time to createVertexLabel sqlg topology: " + stopWatch.toString());
            if (!existSqlgSchema) {
                addPublicSchema();
                this.sqlgGraph.tx().commit();
            }
            if (!existSqlgSchema) {
                //old versions of sqlg needs the topology populated from the information_schema table.
                logger.debug("Upgrading sqlg from pre sqlg_schema version to sqlg_schema version");
                loadSqlgSchemaFromInformationSchema();
                logger.debug("Done upgrading sqlg from pre sqlg_schema version to sqlg_schema version");
            } else {
                //make sure the property index column exist, this if for upgrading from 1.3.2 to 1.4.0
                upgradePropertyIndexTypeToExist();
                this.sqlgGraph.tx().commit();
            }
            cacheTopology();
            if (this.sqlgGraph.configuration().getBoolean("validate.topology", false)) {
                validateTopology();
            }
            this.sqlgGraph.tx().commit();
        } catch (Exception e) {
            this.sqlgGraph.tx().rollback();
            throw e;
        }
    }


    private void cacheTopology() {
        this.sqlgGraph.getTopology().cacheTopology();
    }

    private void validateTopology() {
        this.sqlgGraph.getTopology().validateTopology();
        if (!this.sqlgGraph.getTopology().getValidationErrors().isEmpty()) {
            for (Topology.TopologyValidationError topologyValidationError : this.sqlgGraph.getTopology().getValidationErrors()) {
                logger.warn(topologyValidationError.toString());
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void upgradePropertyIndexTypeToExist() {
        Connection conn = this.sqlgGraph.tx().getConnection();
        try {
            DatabaseMetaData metadata = conn.getMetaData();
            String catalog = null;
            String schemaPattern = "sqlg_schema";
            ResultSet propertyRs = metadata.getColumns(catalog, schemaPattern, "V_property", "index_type");
            if (!propertyRs.next()) {
                Statement statement = conn.createStatement();
                String sql = this.sqlDialect.sqlgAddPropertyIndexTypeColumn();
                statement.execute(sql);
            }
        } catch (SQLException e) {
//            throw new RuntimeException(e);
        }

    }

    @SuppressWarnings("ConstantConditions")
    private void loadSqlgSchemaFromInformationSchema() {
        Connection conn = this.sqlgGraph.tx().getConnection();
        try {
            DatabaseMetaData metadata = conn.getMetaData();
            String catalog = null;
            String schemaPattern = null;
            String[] types = new String[]{"TABLE"};
            //load the schemas
            try (ResultSet schemaRs = metadata.getSchemas()) {
                while (schemaRs.next()) {
                    String schema = schemaRs.getString(1);
                    if (schema.equals(SQLG_SCHEMA) ||
                            this.sqlDialect.getDefaultSchemas().contains(schema) ||
                            this.sqlDialect.getGisSchemas().contains(schema)) {
                        continue;
                    }
                    TopologyManager.addSchema(this.sqlgGraph, schema);
                }
            }
            //load the vertices
            try (ResultSet vertexRs = metadata.getTables(catalog, schemaPattern, "V_%", types)) {
                while (vertexRs.next()) {
                    String schema = vertexRs.getString(2);
                    String table = vertexRs.getString(3);

                    //check if is internal, if so ignore.
                    Set<String> schemasToIgnore = new HashSet<>(this.sqlDialect.getDefaultSchemas());
                    schemasToIgnore.remove(this.sqlDialect.getPublicSchema());
                    if (schema.equals(SQLG_SCHEMA) ||
                            schemasToIgnore.contains(schema) ||
                            this.sqlDialect.getGisSchemas().contains(schema)) {
                        continue;
                    }
                    if (this.sqlDialect.getSpacialRefTable().contains(table)) {
                        continue;
                    }

                    Map<String, PropertyType> columns = new ConcurrentHashMap<>();
                    //get the columns
                    ResultSet columnsRs = metadata.getColumns(catalog, schema, table, null);
                    //Cache the column meta data as we need to go backwards and forward through the resetSet and H2 does not support that.
                    List<Triple<String, Integer, String>> metaDatas = new ArrayList<>();
                    //noinspection Duplicates
                    while (columnsRs.next()) {
                        String columnName = columnsRs.getString(4);
                        int columnType = columnsRs.getInt(5);
                        String typeName = columnsRs.getString("TYPE_NAME");
                        metaDatas.add(Triple.of(columnName, columnType, typeName));
                    }
                    columnsRs.close();

                    ListIterator<Triple<String, Integer, String>> metaDataIter = metaDatas.listIterator();
                    while (metaDataIter.hasNext()) {
                        Triple<String, Integer, String> tripple = metaDataIter.next();
                        String columnName = tripple.getLeft();
                        int columnType = tripple.getMiddle();
                        String typeName = tripple.getRight();
                        if (!columnName.equals(SchemaManager.ID)) {
                            extractProperty(schema, table, columnName, columnType, typeName, columns, metaDataIter);
                        }
                    }
                    TopologyManager.addVertexLabel(this.sqlgGraph, schema, table.substring(SchemaManager.VERTEX_PREFIX.length()), columns);
                }
            }
            //load the edges without their properties
            try (ResultSet edgeRs = metadata.getTables(catalog, schemaPattern, "E_%", types)) {
                while (edgeRs.next()) {
                    String schema = edgeRs.getString(2);
                    String table = edgeRs.getString(3);

                    //check if is internal, if so ignore.
                    Set<String> schemasToIgnore = new HashSet<>(this.sqlDialect.getDefaultSchemas());
                    schemasToIgnore.remove(this.sqlDialect.getPublicSchema());
                    if (schema.equals(SQLG_SCHEMA) ||
                            schemasToIgnore.contains(schema) ||
                            this.sqlDialect.getGisSchemas().contains(schema)) {
                        continue;
                    }
                    if (this.sqlDialect.getSpacialRefTable().contains(table)) {
                        continue;
                    }

                    Map<SchemaTable, MutablePair<SchemaTable, SchemaTable>> inOutSchemaTableMap = new HashMap<>();
                    Map<String, PropertyType> columns = Collections.emptyMap();
                    //get the columns
                    ResultSet columnsRs = metadata.getColumns(catalog, schema, table, null);
                    SchemaTable edgeSchemaTable = SchemaTable.of(schema, table);
                    boolean edgeAdded = false;
                    while (columnsRs.next()) {
                        String column = columnsRs.getString(4);
                        if (table.startsWith(EDGE_PREFIX) && (column.endsWith(SchemaManager.IN_VERTEX_COLUMN_END) || column.endsWith(SchemaManager.OUT_VERTEX_COLUMN_END))) {
                            String[] split = column.split("\\.");
                            SchemaTable foreignKey = SchemaTable.of(split[0], split[1]);
                            if (column.endsWith(SchemaManager.IN_VERTEX_COLUMN_END)) {
                                SchemaTable schemaTable = SchemaTable.of(
                                        split[0],
                                        split[1].substring(0, split[1].length() - SchemaManager.IN_VERTEX_COLUMN_END.length())
                                );
                                if (inOutSchemaTableMap.containsKey(edgeSchemaTable)) {
                                    MutablePair<SchemaTable, SchemaTable> inSchemaTable = inOutSchemaTableMap.get(edgeSchemaTable);
                                    if (inSchemaTable.getLeft() == null) {
                                        inSchemaTable.setLeft(schemaTable);
                                    } else {
                                        TopologyManager.addLabelToEdge(this.sqlgGraph, schema, table, true, foreignKey);
                                    }
                                } else {
                                    inOutSchemaTableMap.put(edgeSchemaTable, MutablePair.of(schemaTable, null));
                                }
                            } else if (column.endsWith(SchemaManager.OUT_VERTEX_COLUMN_END)) {
                                SchemaTable schemaTable = SchemaTable.of(
                                        split[0],
                                        split[1].substring(0, split[1].length() - SchemaManager.OUT_VERTEX_COLUMN_END.length())
                                );
                                if (inOutSchemaTableMap.containsKey(edgeSchemaTable)) {
                                    MutablePair<SchemaTable, SchemaTable> outSchemaTable = inOutSchemaTableMap.get(edgeSchemaTable);
                                    if (outSchemaTable.getRight() == null) {
                                        outSchemaTable.setRight(schemaTable);
                                    } else {
                                        TopologyManager.addLabelToEdge(this.sqlgGraph, schema, table, false, foreignKey);
                                    }
                                } else {
                                    inOutSchemaTableMap.put(edgeSchemaTable, MutablePair.of(null, schemaTable));
                                }
                            }
                            MutablePair<SchemaTable, SchemaTable> inOutLabels = inOutSchemaTableMap.get(edgeSchemaTable);
                            if (!edgeAdded && inOutLabels.getLeft() != null && inOutLabels.getRight() != null) {
                                TopologyManager.addEdgeLabel(this.sqlgGraph, schema, table, inOutLabels.getRight(), inOutLabels.getLeft(), columns);
                                edgeAdded = true;
                            }
                        }
                    }
                }
            }
            //load the edges without their in and out vertices
            try (ResultSet edgeRs = metadata.getTables(catalog, schemaPattern, "E_%", types)) {
                while (edgeRs.next()) {
                    String schema = edgeRs.getString(2);
                    String table = edgeRs.getString(3);

                    //check if is internal, if so ignore.
                    Set<String> schemasToIgnore = new HashSet<>(this.sqlDialect.getDefaultSchemas());
                    schemasToIgnore.remove(this.sqlDialect.getPublicSchema());
                    if (schema.equals(SQLG_SCHEMA) ||
                            schemasToIgnore.contains(schema) ||
                            this.sqlDialect.getGisSchemas().contains(schema)) {
                        continue;
                    }
                    if (this.sqlDialect.getSpacialRefTable().contains(table)) {
                        continue;
                    }

                    Map<String, PropertyType> columns = new HashMap<>();
                    //get the columns
                    ResultSet columnsRs = metadata.getColumns(catalog, schema, table, null);
                    //Cache the column meta data as we need to go backwards and forward through the resetSet and H2 does not support that.
                    List<Triple<String, Integer, String>> metaDatas = new ArrayList<>();
                    //noinspection Duplicates
                    while (columnsRs.next()) {
                        String columnName = columnsRs.getString(4);
                        int columnType = columnsRs.getInt(5);
                        String typeName = columnsRs.getString("TYPE_NAME");
                        metaDatas.add(Triple.of(columnName, columnType, typeName));
                    }
                    columnsRs.close();

                    ListIterator<Triple<String, Integer, String>> metaDataIter = metaDatas.listIterator();
                    while (metaDataIter.hasNext()) {
                        Triple<String, Integer, String> tripple = metaDataIter.next();
                        String columnName = tripple.getLeft();
                        String typeName = tripple.getRight();
                        int columnType = tripple.getMiddle();
                        if (!columnName.equals(SchemaManager.ID)) {
                            extractProperty(schema, table, columnName, columnType, typeName, columns, metaDataIter);
                        }
                    }
                    TopologyManager.addEdgeColumn(this.sqlgGraph, schema, table, columns);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void extractProperty(String schema, String table, String columnName, Integer columnType, String typeName, Map<String, PropertyType> columns, ListIterator<Triple<String, Integer, String>> metaDataIter) throws SQLException {
        //check for ZONEDDATETIME, PERIOD, DURATION as they use more than one field to represent the type
        PropertyType propertyType = null;
        if (metaDataIter.hasNext()) {
            Triple<String, Integer, String> column2MetaData = metaDataIter.next();
            String column2Name = column2MetaData.getLeft();
            String typeName2 = column2MetaData.getRight();
            int column2Type = column2MetaData.getMiddle();
            if (column2Name.startsWith(columnName + "~~~")) {
                if (column2Type == Types.VARCHAR) {
                    propertyType = PropertyType.ZONEDDATETIME;
                } else if ((column2Type == Types.ARRAY && this.sqlDialect.sqlArrayTypeNameToPropertyType(typeName2, this.sqlgGraph, schema, table, column2Name, metaDataIter) == PropertyType.STRING_ARRAY)) {
                    propertyType = PropertyType.ZONEDDATETIME_ARRAY;
                } else {
                    if (metaDataIter.hasNext()) {
                        Triple<String, Integer, String> column3MetaData = metaDataIter.next();
                        String column3Name = column3MetaData.getLeft();
                        String typeName3 = column3MetaData.getRight();
                        int column3Type = column3MetaData.getMiddle();
                        if (column3Name.startsWith(columnName + "~~~")) {
                            if (column3Type == Types.ARRAY) {
                                Preconditions.checkState(sqlDialect.sqlArrayTypeNameToPropertyType(typeName3, this.sqlgGraph, schema, table, column3Name, metaDataIter) == PropertyType.INTEGER_ARRAY, "Only Period have a third column and it must be a Integer");
                                propertyType = PropertyType.PERIOD_ARRAY;
                            } else {
                                Preconditions.checkState(column3Type == Types.INTEGER, "Only Period have a third column and it must be a Integer");
                                propertyType = PropertyType.PERIOD;
                            }
                        } else {
                            metaDataIter.previous();
                            if (column2Type == Types.ARRAY) {
                                Preconditions.checkState(sqlDialect.sqlArrayTypeNameToPropertyType(typeName2, this.sqlgGraph, schema, table, column2Name, metaDataIter) == PropertyType.INTEGER_ARRAY, "Only Period have a third column and it must be a Integer");
                                propertyType = PropertyType.DURATION_ARRAY;
                            } else {
                                Preconditions.checkState(column2Type == Types.INTEGER, "Only Duration and Period have a second column and it must be a Integer");
                                propertyType = PropertyType.DURATION;
                            }
                        }
                    }
                }
            } else {
                metaDataIter.previous();
            }
        }
        if (propertyType == null) {
            propertyType = this.sqlDialect.sqlTypeToPropertyType(this.sqlgGraph, schema, table, columnName, columnType, typeName, metaDataIter);
        }
        columns.put(columnName, propertyType);
    }

    private void addPublicSchema() {
        this.sqlgGraph.addVertex(
                T.label, SQLG_SCHEMA + "." + SQLG_SCHEMA_SCHEMA,
                "name", this.sqlDialect.getPublicSchema(),
                Topology.CREATED_ON, LocalDateTime.now()
        );
    }

    private void createGuiSchema() {
        Connection conn = this.sqlgGraph.tx().getConnection();
        try (Statement statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA \"" + Schema.GLOBAL_UNIQUE_INDEX_SCHEMA + "\";");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void createSqlgSchemaTablesAndIndexes() {
        Connection conn = this.sqlgGraph.tx().getConnection();
        try (Statement statement = conn.createStatement()) {
            List<String> creationScripts = this.sqlDialect.sqlgTopologyCreationScripts();
            //Hsqldb can not do this in one go
            for (String creationScript : creationScripts) {
                statement.execute(creationScript);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean existSqlgSchema() {
        Connection conn = this.sqlgGraph.tx().getConnection();
        try {
            if (this.sqlDialect.supportSchemas()) {
                DatabaseMetaData metadata = conn.getMetaData();
                ResultSet schemaRs = metadata.getSchemas(null /*catalog*/, SQLG_SCHEMA);
                return schemaRs.next();
            } else {
                throw new IllegalStateException("schemas not supported not supported, i.e. probably MariaDB not supported.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean existGuiSchema() {
        Connection conn = this.sqlgGraph.tx().getConnection();
        try {
            if (this.sqlDialect.supportSchemas()) {
                DatabaseMetaData metadata = conn.getMetaData();
                ResultSet schemaRs = metadata.getSchemas(null /*catalog*/, Schema.GLOBAL_UNIQUE_INDEX_SCHEMA);
                return schemaRs.next();
            } else {
                throw new IllegalStateException("schemas not supported not supported, i.e. probably MariaDB not supported.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void createSqlgSchema() {
        Connection conn = this.sqlgGraph.tx().getConnection();
        try (Statement statement = conn.createStatement()) {
            String creationScript = this.sqlDialect.sqlgSqlgSchemaCreationScript();
            statement.execute(creationScript);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
