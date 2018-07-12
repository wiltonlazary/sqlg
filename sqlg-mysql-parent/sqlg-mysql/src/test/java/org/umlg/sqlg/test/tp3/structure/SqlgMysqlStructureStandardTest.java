package org.umlg.sqlg.test.tp3.structure;

import org.apache.tinkerpop.gremlin.GraphProviderClass;
import org.apache.tinkerpop.gremlin.structure.StructureStandardSuite;
import org.junit.runner.RunWith;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.test.tp3.SqlgMysqlProvider;


/**
 * Executes the Standard Gremlin Structure Test Suite using SqlG.
 */
@RunWith(StructureStandardSuite.class)
@GraphProviderClass(provider = SqlgMysqlProvider.class, graph = SqlgGraph.class)
public class SqlgMysqlStructureStandardTest {
}
