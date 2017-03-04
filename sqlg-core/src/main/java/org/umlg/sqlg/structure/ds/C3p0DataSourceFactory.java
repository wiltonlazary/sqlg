package org.umlg.sqlg.structure.ds;

import com.google.common.base.Preconditions;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.structure.SqlgDataSourceFactory;
import org.umlg.sqlg.structure.SqlgGraph;


/**
 * Created by petercipov on 27/02/2017.
 */
public class C3p0DataSourceFactory implements SqlgDataSourceFactory {

    private static Logger logger = LoggerFactory.getLogger(C3p0DataSourceFactory.class.getName());

    @Override
    public SqlgDataSource setup(String driver, Configuration configuration) throws Exception {
        Preconditions.checkState(configuration.containsKey(SqlgGraph.JDBC_URL));
        Preconditions.checkState(configuration.containsKey("jdbc.username"));
        Preconditions.checkState(configuration.containsKey("jdbc.password"));
        String connectURI = configuration.getString(SqlgGraph.JDBC_URL);
        String username = configuration.getString("jdbc.username");
        String password = configuration.getString("jdbc.password");

        //this odd logic is for travis, it needs log feedback to not kill the build
        if (configuration.getString(SqlgGraph.JDBC_URL).contains("postgresql")) {
            logger.debug(String.format("Setting up dataSource to %s for user %s", connectURI, username));
        } else {
            logger.debug(String.format("Setting up dataSource to %s for user %s", connectURI, username));
        }
        ComboPooledDataSource comboPooledDataSource = new ComboPooledDataSource();
        comboPooledDataSource.setDriverClass(driver);
        comboPooledDataSource.setJdbcUrl(connectURI);
        comboPooledDataSource.setMaxPoolSize(configuration.getInt("maxPoolSize", 100));
        comboPooledDataSource.setMaxIdleTime(configuration.getInt("maxIdleTime", 500));
        if (!StringUtils.isEmpty(username)) {
            comboPooledDataSource.setUser(username);
        }
        if (!StringUtils.isEmpty(username)) {
            comboPooledDataSource.setPassword(password);
        }

        return new C3P0DataSource(connectURI, comboPooledDataSource);
    }
}
