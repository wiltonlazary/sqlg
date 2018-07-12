package org.umlg.sqlg;

import org.umlg.sqlg.sql.dialect.CockroachdbDialect;
import org.umlg.sqlg.sql.dialect.SqlDialect;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 *         Date: 2017/06/30
 */
public class CockroachdbPlugin implements SqlgPlugin {

    @Override
    public boolean canWorkWith(DatabaseMetaData metaData) throws SQLException {
        return metaData.getDatabaseProductName().toLowerCase().contains("postgres");
    }

    @Override
    public String getDriverFor(String connectionUrl) {
        return connectionUrl.startsWith("jdbc:postgresql") ? "org.postgresql.xa.PGXADataSource" : null;
    }

    @Override
    public SqlDialect instantiateDialect() {
        return new CockroachdbDialect();
    }
}
