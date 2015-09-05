package org.umlg.sqlg.test.tp3.process;

import org.apache.tinkerpop.gremlin.GraphProviderClass;
import org.apache.tinkerpop.gremlin.process.GroovyProcessStandardSuite;
import org.apache.tinkerpop.gremlin.process.ProcessStandardSuite;
import org.junit.runner.RunWith;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.test.tp3.SqlgHsqldbProvider;


/**
 * Executes the Standard Gremlin Structure Test Suite using Hsqldb.
 */
@RunWith(GroovyProcessStandardSuite.class)
@GraphProviderClass(provider = SqlgHsqldbProvider.class, graph = SqlgGraph.class)
public class SqlgHsqldbGroovyProcessStandardTest {
}
