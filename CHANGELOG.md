##1.3.3

* Replace `ResultSet.getObject(index)` with `ResultSet.getString/Int...` as its faster.
* Added support for global  unique indexes. This means that a unique index can be placed on multiple properties from any Vertex or Edge.
* Rewrite of the topology/schema management. `SchemaManager` is replaced with `Topology`.
There are now object representing the topology. `Topology`, `Schema`, `VertexLabel`, `EdgeLabel`, `PropertyColumn` and `Index`
* Strengthened the reloading of the topology from the information_schema tables.
This highlighted some limitations. It is not possible to tell a primitive array from a object array. 
As such all arrays are  loaded as object arrays. i.e. `int[]{1,2,3}` will become `Integer[]{1,2,3}`

    Example of how to fix a property' type
    
    update sqlg_schema."V_property" set type = 'byte_ARRAY' where name = 'password' and type = 'BYTE_ARRAY'
    update sqlg_schema."V_property" set type = 'byte_ARRAY' where name = 'password_salt' and type = 'BYTE_ARRAY'
* Fix bug [#116](https://github.com/pietermartin/sqlg/issues/116)

    If a `RepeapStep` could not be optimized then the incoming emit `Element` did not get a label so it was not being returned from the sql.


##1.3.2

* Ensure SqlgGraphStepStrategy and SqlgVertexStepStrategy fires before InlineFilterStrategy.
* Fix a bug where hasId uses the P.neq predicate.
* Use BIGSERIAL for auto increment columns in Postgresql [#91](https://github.com/pietermartin/sqlg/issues/91)
* Fix bug [#92](https://github.com/pietermartin/sqlg/issues/92)
* Broaded SqlgGraph.bulkAddEdges to take a Collection of ids as opposed to a List.
    Fix bug [#102](https://github.com/pietermartin/sqlg/issues/102)
* Fix bug [#73](https://github.com/pietermartin/sqlg/issues/73)
        Thanks to [JPMoresmau](https://github.com/JPMoresmau)

##1.3.1

* 1.3.0 uploaded strange byte code, this fixes that.

##1.3.0

* Upgrade to TinkerPop 3.2.2
* Added H2 support.
* Added support for getting the data source from JNDI.
* Optimize `SqlgGraph.bulkAddEdges(...) to use the correct types for the in and out properties. This has a marginal performance increase.
* Refactored pom to separate out `gremlin-groovy` to be an optional dependency.

##1.2.0

* Optimize lazy iteration. Remove unnecessary list creation for managing state.
* Add thread local `PreparedStatement` cache, to close all statements on commit or rollback.
* Refactor the vertex transaction cache to use a `WeakHashMap`.
* Refactor Sqlg to lazily iterate the sql `ResultSet`.
* Add all optimizations to `LocalStep`.
* Refactor `RepeatStep` optimization to follow the same sql pattern as the `OptionalStep` optimization.
* Optimized the `OptionalStep`.
* Optimize `hasId(...)`
* Sqlg stores the schema in the graph. It is accessible via the `TopologyStrategy`. eg. `TopologyStrategy.build().selectFrom(SchemaManager.SQLG_SCHEMA_SCHEMA_TABLES)`
* Remove `SqlgTransaction.batchCommit()` as is no longer useful as the embedded topology change forced sqlg to auto flush the batch before any query.
* Add support for `java.time.ZonedDateTime`, `java.time.Duration` and `java.time.Period`
* Add support for array types in batch mode. `String[], int[], double[], long[], float[], short[], byte[]`

##1.1.0.RC2

* Use special characters to separate label + schema + table + column as opposed to a period. Periods may now be present in property names.
* Change postgresql copy command to use the csv format. This allows for specifying a quote character which prevents nasty bugs where backslash characters in the value escapes the delimiter.
* Added `SortedTree`. A utility class that wraps TinkerPop's Tree using a TreeMap instead.