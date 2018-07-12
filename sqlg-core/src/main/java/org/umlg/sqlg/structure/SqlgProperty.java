package org.umlg.sqlg.structure;

import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.NoSuchElementException;

import static org.umlg.sqlg.structure.topology.Topology.EDGE_PREFIX;
import static org.umlg.sqlg.structure.topology.Topology.VERTEX_PREFIX;

/**
 * Date: 2014/07/12
 * Time: 5:43 AM
 */
public class SqlgProperty<V> implements Property<V>, Serializable {

    private static Logger logger = LoggerFactory.getLogger(SqlgProperty.class);
    private final String key;
    private V value;
    private SqlgElement element;
    protected SqlgGraph sqlgGraph;

    SqlgProperty(SqlgGraph sqlgGraph, SqlgElement element, String key, V value) {
        this.sqlgGraph = sqlgGraph;
        this.element = element;
        this.key = key;
        this.value = value;
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public V value() throws NoSuchElementException {
        return this.value;
    }

    @Override
    public boolean isPresent() {
        return this.value != null;
    }

    @Override
    public Element element() {
        return this.element;
    }

    @Override
    public void remove() {
        this.element.properties.remove(this.key);
        boolean elementInInsertedCache = false;
        if (this.sqlgGraph.getSqlDialect().supportsBatchMode() && this.sqlgGraph.tx().isInBatchMode()) {
            elementInInsertedCache = this.sqlgGraph.tx().getBatchManager().removeProperty(this, key);
        }

        if (!elementInInsertedCache) {
            PropertyType propertyType = PropertyType.from(value);
            String[] postfixes = propertyType.getPostFixes();

            StringBuilder sql = new StringBuilder("UPDATE ");
            sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes(this.element.schema));
            sql.append(".");
            sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes((this.element instanceof Vertex ? VERTEX_PREFIX : EDGE_PREFIX) + this.element.table));
            sql.append(" SET ");
            sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes(this.key));
            sql.append(" = ?");
            for (String postfix : postfixes) {
                sql.append(", ");
                sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes(key + postfix));
                sql.append(" = ?");
            }
            sql.append(" WHERE ");
            sql.append(this.sqlgGraph.getSqlDialect().maybeWrapInQoutes("ID"));
            sql.append(" = ?");
            if (this.sqlgGraph.getSqlDialect().needsSemicolon()) {
                sql.append(";");
            }
            if (logger.isDebugEnabled()) {
                logger.debug(sql.toString());
            }
            Connection conn = this.sqlgGraph.tx().getConnection();
            try (PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {
                int[] sqlTypes = this.sqlgGraph.getSqlDialect().propertyTypeToJavaSqlType(propertyType);
                int parameterIndex = 1;
                for (int sqlType : sqlTypes) {
                    preparedStatement.setNull(parameterIndex++, sqlType);
                }
                preparedStatement.setLong(parameterIndex, ((RecordId) this.element.id()).getId());
                int numberOfRowsUpdated = preparedStatement.executeUpdate();
                if (numberOfRowsUpdated != 1) {
                    throw new IllegalStateException("Remove property failed!");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String toString() {
        return StringFactory.propertyString(this);
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(final Object object) {
        return ElementHelper.areEqual(this, object);
    }

    @Override
    public int hashCode() {
        return ElementHelper.hashCode(this);
    }
}
