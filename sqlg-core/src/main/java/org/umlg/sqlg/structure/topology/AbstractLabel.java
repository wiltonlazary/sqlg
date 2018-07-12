package org.umlg.sqlg.structure.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.structure.*;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.umlg.sqlg.structure.topology.Topology.*;

/**
 * Date: 2016/09/14
 * Time: 11:19 AM
 */
public abstract class AbstractLabel implements TopologyInf {

    private static Logger logger = LoggerFactory.getLogger(AbstractLabel.class);
    protected boolean committed = true;
    protected String label;
    protected SqlgGraph sqlgGraph;
    protected Map<String, PropertyColumn> properties = new HashMap<>();
    Map<String, PropertyColumn> uncommittedProperties = new HashMap<>();
    Set<String> uncommittedRemovedProperties = new HashSet<>();

    private Map<String, PropertyColumn> globalUniqueIndexProperties = new HashMap<>();
    private Map<String, PropertyColumn> uncommittedGlobalUniqueIndexProperties = new HashMap<>();

    private Map<String, Index> indexes = new HashMap<>();
    private Map<String, Index> uncommittedIndexes = new HashMap<>();
    private Set<String> uncommittedRemovedIndexes = new HashSet<>();

    //Yet another cache to speed meta data up.
    private Map<String, PropertyType> propertyTypeMap;

    /**
     * Only called for a new vertex/edge label being added.
     *
     * @param label      The vertex or edge's label.
     * @param properties The vertex's properties.
     */
    AbstractLabel(SqlgGraph sqlgGraph, String label, Map<String, PropertyType> properties) {
        this.sqlgGraph = sqlgGraph;
        this.label = label;
        for (Map.Entry<String, PropertyType> propertyEntry : properties.entrySet()) {
            PropertyColumn property = new PropertyColumn(this, propertyEntry.getKey(), propertyEntry.getValue());
            property.setCommitted(false);
            this.uncommittedProperties.put(propertyEntry.getKey(), property);
        }
    }

    AbstractLabel(SqlgGraph sqlgGraph, String label) {
        this.sqlgGraph = sqlgGraph;
        this.label = label;
    }

    @Override
    public boolean isCommitted() {
        return this.committed;
    }

    public Index ensureIndexExists(final IndexType indexType, final List<PropertyColumn> properties) {
        String prefix = this instanceof VertexLabel ? VERTEX_PREFIX : EDGE_PREFIX;
        SchemaTable schemaTable = SchemaTable.of(this.getSchema().getName(), this.getLabel());

        String indexName = this.sqlgGraph.getSqlDialect().indexName(schemaTable, prefix, properties.stream().map(PropertyColumn::getName).collect(Collectors.toList()));
        if (indexName.length() > this.sqlgGraph.getSqlDialect().getMaximumIndexNameLength()) {
            // name was random, need to check the properties list
        	for (Index idx:this.getIndexes().values()){
            	if (idx.getProperties().equals(properties)){
            		return idx;
            	}
            }
        	
            this.getSchema().getTopology().lock();
            for (Index idx:this.getIndexes().values()){
            	if (idx.getProperties().equals(properties)){
            		return idx;
            	}
            }
        	RandomStringGenerator generator = new RandomStringGenerator.Builder()
                    .withinRange('a', 'z').build();
            indexName = generator.generate(this.sqlgGraph.getSqlDialect().getMaximumIndexNameLength());
            
            return this.createIndex(indexName, indexType, properties);
            
        } else {

	        Optional<Index> indexOptional = this.getIndex(indexName);
	        if (!indexOptional.isPresent()) {
	            this.getSchema().getTopology().lock();
	            indexOptional = this.getIndex(indexName);
	            if (!indexOptional.isPresent()) {
	                return this.createIndex(indexName, indexType, properties);
	            } else {
	                return indexOptional.get();
	            }
	        } else {
	            return indexOptional.get();
	        }
        }
    }

    private Index createIndex(String indexName, IndexType indexType, List<PropertyColumn> properties) {
        Index index = Index.createIndex(this.sqlgGraph, this, indexName, indexType, properties);
        this.uncommittedIndexes.put(indexName, index);
        this.getSchema().getTopology().fire(index, "", TopologyChangeAction.CREATE);
        return index;
    }

    void addIndex(Index i) {
        this.indexes.put(i.getName(), i);
    }

    public abstract Schema getSchema();

    public String getLabel() {
        return this.label;
    }

    @Override
    public String getName() {
        return this.label;
    }


    public String getFullName() {
        return getSchema().getName() + "." + getName();
    }

    public Map<String, PropertyColumn> getProperties() {
        Map<String, PropertyColumn> result = new HashMap<>();
        result.putAll(this.properties);
        if (this.getSchema().getTopology().isSqlWriteLockHeldByCurrentThread()) {
            result.putAll(this.uncommittedProperties);
            for (String s : this.uncommittedRemovedProperties) {
                result.remove(s);
            }
        }

        return result;
    }

    public Map<String, PropertyColumn> getGlobalUniqueIndexProperties() {
        Map<String, PropertyColumn> result = new HashMap<>();
        result.putAll(this.globalUniqueIndexProperties);
        if (this.getSchema().getTopology().isSqlWriteLockHeldByCurrentThread()) {
            result.putAll(this.uncommittedGlobalUniqueIndexProperties);
        }
        return result;
    }

    public Optional<PropertyColumn> getProperty(String key) {
        PropertyColumn propertyColumn = getProperties().get(key);
        if (propertyColumn != null) {
            return Optional.of(propertyColumn);
        } else {
            return Optional.empty();
        }
    }

    public Map<String, Index> getIndexes() {
        Map<String, Index> result = new HashMap<>();
        result.putAll(this.indexes);
        if (this.getSchema().getTopology().isSqlWriteLockHeldByCurrentThread()) {
            result.putAll(this.uncommittedIndexes);
            for (String i : this.uncommittedRemovedIndexes) {
                result.remove(i);
            }
        }
        return result;
    }

    public Optional<Index> getIndex(String key) {
        Index index = getIndexes().get(key);
        if (index != null) {
            return Optional.of(index);
        } else {
            return Optional.empty();
        }
    }

    Map<String, PropertyType> getPropertyTypeMap() {
        Map<String, PropertyType> result = new HashMap<>();
        for (Map.Entry<String, PropertyColumn> propertyEntry : this.properties.entrySet()) {
            result.put(propertyEntry.getKey(), propertyEntry.getValue().getPropertyType());
        }
        if (getSchema().getTopology().isSqlWriteLockHeldByCurrentThread()) {
            for (Map.Entry<String, PropertyColumn> uncommittedPropertyEntry : this.uncommittedProperties.entrySet()) {
                result.put(uncommittedPropertyEntry.getKey(), uncommittedPropertyEntry.getValue().getPropertyType());
            }
            for (String s : this.uncommittedRemovedProperties) {
                result.remove(s);
            }
        }
        return result;
    }

    Map<String, PropertyColumn> getUncommittedPropertyTypeMap() {
        if (getSchema().getTopology().isSqlWriteLockHeldByCurrentThread()) {
            return this.uncommittedProperties;
        } else {
            return Collections.emptyMap();
        }
    }

    Set<String> getUncommittedRemovedProperties() {
        if (getSchema().getTopology().isSqlWriteLockHeldByCurrentThread()) {
            return this.uncommittedRemovedProperties;
        } else {
            return Collections.emptySet();
        }
    }

    static void buildColumns(SqlgGraph sqlgGraph, Map<String, PropertyType> columns, StringBuilder sql, Properties additional) {
        int i = 1;
        //This is to make the columns sorted
        List<String> keys = new ArrayList<>(columns.keySet());
        Collections.sort(keys);
        for (String column : keys) {
            if (additional.containsKey(Topology.COLUMN_TYPE_PREFIX + column)) {
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column)).append(" ").append(additional.getProperty(Topology.COLUMN_TYPE_PREFIX + column));
            } else {
                PropertyType propertyType = columns.get(column);
                int count = 1;
                String[] propertyTypeToSqlDefinition = sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(propertyType);
                for (String sqlDefinition : propertyTypeToSqlDefinition) {
                    if (count > 1) {
                        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column + propertyType.getPostFixes()[count - 2])).append(" ").append(sqlDefinition);
                    } else {
                        //The first column existVertexLabel no postfix
                        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column)).append(" ").append(sqlDefinition);
                    }
                    if (count++ < propertyTypeToSqlDefinition.length) {
                        sql.append(", ");
                    }
                }
            }
            if (i++ < columns.size()) {
                sql.append(", ");
            }
        }
    }

    protected void addColumn(String schema, String table, ImmutablePair<String, PropertyType> keyValue) {
        int count = 1;
        String[] propertyTypeToSqlDefinition = this.sqlgGraph.getSqlDialect().propertyTypeToSqlDefinition(keyValue.getRight());
        for (String sqlDefinition : propertyTypeToSqlDefinition) {
            StringBuilder sql = new StringBuilder("ALTER TABLE ");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(schema));
            sql.append(".");
            sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(table));
            sql.append(" ADD ");
            if (count > 1) {
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(keyValue.getLeft() + keyValue.getRight().getPostFixes()[count - 2]));
            } else {
                //The first column existVertexLabel no postfix
                sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(keyValue.getLeft()));
            }
            count++;
            sql.append(" ");
            sql.append(sqlDefinition);

            if (sqlgGraph.getSqlDialect().needsSemicolon()) {
                sql.append(";");
            }
            if (logger.isDebugEnabled()) {
                logger.debug(sql.toString());
            }
            Connection conn = sqlgGraph.tx().getConnection();
            try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void addProperty(Vertex propertyVertex) {
        Preconditions.checkState(this.getSchema().getTopology().isSqlWriteLockHeldByCurrentThread());
        PropertyColumn property = new PropertyColumn(this, propertyVertex.value(SQLG_SCHEMA_PROPERTY_NAME), PropertyType.valueOf(propertyVertex.value(SQLG_SCHEMA_PROPERTY_TYPE)));
        this.properties.put(propertyVertex.value(SQLG_SCHEMA_PROPERTY_NAME), property);
    }

    void afterCommit() {
        Preconditions.checkState(this.getSchema().getTopology().isSqlWriteLockHeldByCurrentThread(), "Abstract.afterCommit must hold the write lock");
        for (Iterator<Map.Entry<String, PropertyColumn>> it = this.uncommittedProperties.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, PropertyColumn> entry = it.next();
            this.properties.put(entry.getKey(), entry.getValue());
            entry.getValue().afterCommit();
            it.remove();
            if (this.propertyTypeMap != null) {
                this.propertyTypeMap.clear();
                this.propertyTypeMap = null;
            }
        }
        for (Iterator<String> it = this.uncommittedRemovedProperties.iterator(); it.hasNext(); ) {
            String prop = it.next();
            this.properties.remove(prop);
            it.remove();
            if (this.propertyTypeMap != null) {
                this.propertyTypeMap.clear();
                this.propertyTypeMap = null;
            }
        }
        for (Iterator<Map.Entry<String, PropertyColumn>> it = this.uncommittedGlobalUniqueIndexProperties.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, PropertyColumn> entry = it.next();
            this.globalUniqueIndexProperties.put(entry.getKey(), entry.getValue());
            entry.getValue().afterCommit();
            it.remove();
        }
        for (Iterator<Map.Entry<String, Index>> it = this.uncommittedIndexes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Index> entry = it.next();
            this.indexes.put(entry.getKey(), entry.getValue());
            entry.getValue().afterCommit();
            it.remove();
        }
        for (Iterator<String> it = this.uncommittedRemovedIndexes.iterator(); it.hasNext(); ) {
            String prop = it.next();
            this.indexes.remove(prop);
            it.remove();
        }
        for (Iterator<Map.Entry<String, PropertyColumn>> it = this.properties.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, PropertyColumn> entry = it.next();
            entry.getValue().afterCommit();
        }
        this.committed = true;
    }

    void afterRollback() {
        Preconditions.checkState(this.getSchema().getTopology().isSqlWriteLockHeldByCurrentThread(), "Abstract.afterRollback must hold the write lock");
        for (Iterator<Map.Entry<String, PropertyColumn>> it = this.uncommittedProperties.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, PropertyColumn> entry = it.next();
            entry.getValue().afterRollback();
            it.remove();
            if (this.propertyTypeMap != null) {
                this.propertyTypeMap.clear();
                this.propertyTypeMap = null;
            }
        }
        this.uncommittedRemovedProperties.clear();
        this.uncommittedGlobalUniqueIndexProperties.clear();
        for (Iterator<Map.Entry<String, Index>> it = this.uncommittedIndexes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Index> entry = it.next();
            entry.getValue().afterRollback();
            it.remove();
        }
        this.uncommittedRemovedIndexes.clear();
        for (Iterator<Map.Entry<String, PropertyColumn>> it = this.properties.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, PropertyColumn> entry = it.next();
            entry.getValue().afterRollback();
        }
    }

    protected JsonNode toJson() {
        ArrayNode propertyArrayNode = new ArrayNode(Topology.OBJECT_MAPPER.getNodeFactory());
        for (PropertyColumn property : this.properties.values()) {
            propertyArrayNode.add(property.toNotifyJson());
        }
        return propertyArrayNode;
    }

    protected Optional<JsonNode> toNotifyJson() {
        if (this.getSchema().getTopology().isSqlWriteLockHeldByCurrentThread()) {
            ObjectNode result = new ObjectNode(Topology.OBJECT_MAPPER.getNodeFactory());
            ArrayNode propertyArrayNode = new ArrayNode(Topology.OBJECT_MAPPER.getNodeFactory());
            for (PropertyColumn property : this.uncommittedProperties.values()) {
                propertyArrayNode.add(property.toNotifyJson());
            }
            ArrayNode removedPropertyArrayNode = new ArrayNode(Topology.OBJECT_MAPPER.getNodeFactory());
            for (String property : this.uncommittedRemovedProperties) {
                removedPropertyArrayNode.add(property);
            }
            ArrayNode indexArrayNode = new ArrayNode(Topology.OBJECT_MAPPER.getNodeFactory());
            for (Index index : this.uncommittedIndexes.values()) {
                //noinspection OptionalGetWithoutIsPresent
                Optional<JsonNode> indexJsonOptional = index.toNotifyJson();
                Preconditions.checkState(indexJsonOptional.isPresent());
                //noinspection OptionalGetWithoutIsPresent
                indexArrayNode.add(indexJsonOptional.get());
            }
            ArrayNode removedIndexArrayNode = new ArrayNode(Topology.OBJECT_MAPPER.getNodeFactory());
            for (String property : this.uncommittedRemovedIndexes) {
                removedIndexArrayNode.add(property);
            }
            result.set("uncommittedProperties", propertyArrayNode);
            result.set("uncommittedRemovedProperties", removedPropertyArrayNode);
            result.set("uncommittedIndexes", indexArrayNode);
            result.set("uncommittedRemovedIndexes", removedIndexArrayNode);
            if (propertyArrayNode.size() == 0 && removedPropertyArrayNode.size() == 0 && indexArrayNode.size() == 0 && removedIndexArrayNode.size() == 0) {
                return Optional.empty();
            }
            return Optional.of(result);
        } else {
            return Optional.empty();
        }
    }

    /**
     * @param vertexLabelJson
     * @param fire            should we fire topology events
     */
    void fromPropertyNotifyJson(JsonNode vertexLabelJson, boolean fire) {
        ArrayNode propertiesNode = (ArrayNode) vertexLabelJson.get("uncommittedProperties");
        if (propertiesNode != null) {
            for (JsonNode propertyNode : propertiesNode) {
                PropertyColumn propertyColumn = PropertyColumn.fromNotifyJson(this, propertyNode);
                PropertyColumn old = this.properties.put(propertyColumn.getName(), propertyColumn);
                if (fire && old == null) {
                    this.getSchema().getTopology().fire(propertyColumn, "", TopologyChangeAction.CREATE);
                }
            }
        }
        ArrayNode removedPropertyArrayNode = (ArrayNode) vertexLabelJson.get("uncommittedRemovedProperties");
        if (removedPropertyArrayNode != null) {
            for (JsonNode propertyNode : removedPropertyArrayNode) {
                String pName = propertyNode.asText();
                PropertyColumn old = this.properties.remove(pName);
                if (fire && old != null) {
                    this.getSchema().getTopology().fire(old, "", TopologyChangeAction.DELETE);
                }
            }
        }
        ArrayNode indexNodes = (ArrayNode) vertexLabelJson.get("uncommittedIndexes");
        if (indexNodes != null) {
            for (JsonNode indexNode : indexNodes) {
                Index index = Index.fromNotifyJson(this, indexNode);
                this.indexes.put(index.getName(), index);
                this.getSchema().getTopology().fire(index, "", TopologyChangeAction.CREATE);
            }
        }
        ArrayNode removedIndexArrayNode = (ArrayNode) vertexLabelJson.get("uncommittedRemovedIndexes");
        if (removedIndexArrayNode != null) {
            for (JsonNode indexNode : removedIndexArrayNode) {
                String iName = indexNode.asText();
                Index old = this.indexes.remove(iName);
                if (fire && old != null) {
                    this.getSchema().getTopology().fire(old, "", TopologyChangeAction.DELETE);
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof AbstractLabel)) {
            return false;
        }
        AbstractLabel other = (AbstractLabel) o;
        if (!this.label.equals(other.label)) {
            return false;
        }
        return true;
    }

    void addGlobalUniqueIndexToUncommittedProperties(PropertyColumn propertyColumn) {
        this.uncommittedGlobalUniqueIndexProperties.put(propertyColumn.getName(), propertyColumn);
    }

    void addGlobalUniqueIndexToProperties(PropertyColumn propertyColumn) {
        this.globalUniqueIndexProperties.put(propertyColumn.getName(), propertyColumn);
    }

    protected abstract List<Topology.TopologyValidationError> validateTopology(DatabaseMetaData metadata) throws SQLException;

    protected abstract String getPrefix();

    /**
     * remove a given property
     *
     * @param propertyColumn the property column
     * @param preserveData   should we preserve the SQL data?
     */
    abstract void removeProperty(PropertyColumn propertyColumn, boolean preserveData);

    /**
     * remove a column from the table
     *
     * @param schema the schema
     * @param table  the table name
     * @param column the column name
     */
    void removeColumn(String schema, String table, String column) {
        StringBuilder sql = new StringBuilder("ALTER TABLE ");
        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(schema));
        sql.append(".");
        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(table));
        sql.append(" DROP COLUMN IF EXISTS ");
        sql.append(sqlgGraph.getSqlDialect().maybeWrapInQoutes(column));
        if (sqlgGraph.getSqlDialect().supportsCascade()) {
            sql.append(" CASCADE");
        }
        if (sqlgGraph.getSqlDialect().needsSemicolon()) {
            sql.append(";");
        }
        if (logger.isDebugEnabled()) {
            logger.debug(sql.toString());
        }
        Connection conn = sqlgGraph.tx().getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * remove a given index that was on this label
     *
     * @param idx          the index
     * @param preserveData should we keep the SQL data
     */
    void removeIndex(Index idx, boolean preserveData) {
        this.getSchema().getTopology().lock();
        if (!uncommittedRemovedIndexes.contains(idx.getName())) {
            uncommittedRemovedIndexes.add(idx.getName());
            TopologyManager.removeIndex(this.sqlgGraph, idx);
            if (!preserveData) {
                idx.delete(sqlgGraph);
            }
            this.getSchema().getTopology().fire(idx, "", TopologyChangeAction.DELETE);
        }
    }

    /**
     * check if we're valid (have a valid schema, for example)
     * this is used for edge labels that require at least one out vertex but sometimes don't (in the middle of deletion operations)
     *
     * @return
     */
    boolean isValid() {
        return true;
    }

}
