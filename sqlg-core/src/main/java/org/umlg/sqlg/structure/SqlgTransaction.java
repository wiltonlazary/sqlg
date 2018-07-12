package org.umlg.sqlg.structure;

import com.google.common.base.Preconditions;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.util.AbstractThreadLocalTransaction;
import org.apache.tinkerpop.gremlin.structure.util.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.sql.dialect.SqlBulkDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * This class is a singleton. Instantiated and owned by SqlGraph.
 * It manages the opening, commit, rollback and close of the java.sql.Connection in a threadvar.
 * Date: 2014/07/12
 * Time: 2:18 PM
 */
public class SqlgTransaction extends AbstractThreadLocalTransaction {

    private static final String BATCH_MODE_NOT_SUPPORTED = "Batch mode not supported!";
    @SuppressWarnings("WeakerAccess")
    public static final String QUERY_LAZY = "query.lazy";
    
    private SqlgGraph sqlgGraph;
    private BeforeCommit beforeCommitFunction;
    private AfterCommit afterCommitFunction;
    private AfterRollback afterRollbackFunction;
    private static Logger logger = LoggerFactory.getLogger(SqlgTransaction.class);
    private boolean cacheVertices = false;

    private final ThreadLocal<TransactionCache> threadLocalTx = ThreadLocal.withInitial(() -> null);

    private final ThreadLocal<PreparedStatementCache> threadLocalPreparedStatementTx = ThreadLocal.withInitial(PreparedStatementCache::new);

    /**
     * default fetch size
     */
    private Integer defaultFetchSize = null;


	SqlgTransaction(Graph sqlgGraph, boolean cacheVertices) {
        super(sqlgGraph);
        this.sqlgGraph = (SqlgGraph) sqlgGraph;
        this.cacheVertices = cacheVertices;
    }

    @Override
    protected void doOpen() {
        if (isOpen())
            throw Transaction.Exceptions.transactionAlreadyOpen();
        else {
            try {
                Connection connection = this.sqlgGraph.getConnection();
                connection.setAutoCommit(false);
                if (this.sqlgGraph.getSqlDialect().supportsClientInfo()) {
                    connection.setClientInfo("ApplicationName", Thread.currentThread().getName());
                }
                // read default setting for laziness
                boolean lazy=this.sqlgGraph.getConfiguration().getBoolean(QUERY_LAZY,true);
                TransactionCache tc=null;
                if (supportsBatchMode()) {
                   tc = TransactionCache.of(this.cacheVertices, connection, new BatchManager(this.sqlgGraph, ((SqlBulkDialect)this.sqlgGraph.getSqlDialect())),lazy);
                } else {
                   tc = TransactionCache.of(this.cacheVertices, connection, lazy);
                }
                tc.setFetchSize(getDefaultFetchSize());
                this.threadLocalTx.set(tc);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void doCommit() throws TransactionException {
        if (!isOpen()) {
            return;
        }
        try {
            if (supportsBatchMode() && this.threadLocalTx.get().getBatchManager().isInBatchMode()) {
                getBatchManager().flush();
            }
            Connection connection = this.threadLocalTx.get().getConnection();
            if (this.beforeCommitFunction != null) {
                this.beforeCommitFunction.doBeforeCommit();
            }
            connection.commit();
            connection.setAutoCommit(true);
            if (this.afterCommitFunction != null) {
                this.afterCommitFunction.doAfterCommit();
            }
            this.threadLocalPreparedStatementTx.get().close();
            connection.close();
        } catch (Exception e) {
            this.rollback();
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            if (this.threadLocalTx.get() != null) {
                this.threadLocalTx.get().clear();
                this.threadLocalTx.remove();
            }
            this.threadLocalPreparedStatementTx.remove();
        }
    }

    @Override
    protected void doRollback() throws TransactionException {
        if (!isOpen()) {
            return;
        }
        try {
            if (supportsBatchMode() && this.threadLocalTx.get().getBatchManager().isInBatchMode()) {
                try {
                    this.threadLocalTx.get().getBatchManager().close();
                } catch (Exception e) {
                    //swallow
                    logger.debug("exception closing streams on rollback", e);
                }
            }
            Connection connection = threadLocalTx.get().getConnection();
            connection.setAutoCommit(false);
            connection.rollback();
            if (this.afterRollbackFunction != null) {
                this.afterRollbackFunction.doAfterRollback();
            }
            //noinspection Convert2streamapi
            for (ElementPropertyRollback elementPropertyRollback : threadLocalTx.get().getElementPropertyRollback().keySet()) {
                elementPropertyRollback.clearProperties();
            }
            this.threadLocalPreparedStatementTx.get().close();
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (isOpen()) {
                this.threadLocalTx.get().clear();
                this.threadLocalTx.remove();
                this.threadLocalPreparedStatementTx.remove();
            }
        }
    }

    public void streamingWithLockBatchModeOn() {
        if (supportsBatchMode()) {
            readWrite();
            this.threadLocalTx.get().getBatchManager().batchModeOn(BatchManager.BatchModeType.STREAMING_WITH_LOCK);
        } else {
            throw new IllegalStateException(BATCH_MODE_NOT_SUPPORTED);
        }
    }

    public void streamingBatchModeOn() {
        if (supportsBatchMode()) {
            readWrite();
            this.threadLocalTx.get().getBatchManager().batchModeOn(BatchManager.BatchModeType.STREAMING);
        } else {
            throw new IllegalStateException(BATCH_MODE_NOT_SUPPORTED);
        }
    }

    public void batchMode(BatchManager.BatchModeType batchModeType) {
        if (supportsBatchMode()) {
            switch (batchModeType) {
                case NONE:
                    readWrite();
                    this.threadLocalTx.get().getBatchManager().batchModeOn(BatchManager.BatchModeType.NONE);
                    break;
                case NORMAL:
                    this.normalBatchModeOn();
                    break;
                case STREAMING:
                    this.streamingBatchModeOn();
                    break;
                case STREAMING_WITH_LOCK:
                    this.streamingWithLockBatchModeOn();
                    break;
                default:
                    throw new IllegalStateException("unhandled BatchModeType " + batchModeType.name());
            }
        }
    }

    public void normalBatchModeOn() {
        if (supportsBatchMode()) {
            readWrite();
            this.threadLocalTx.get().getBatchManager().batchModeOn(BatchManager.BatchModeType.NORMAL);
        } else {
            throw new IllegalStateException(BATCH_MODE_NOT_SUPPORTED);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isInBatchMode() {
        return supportsBatchMode() && isInNormalBatchMode() || isInStreamingBatchMode() || isInStreamingWithLockBatchMode();
    }

    public boolean isInNormalBatchMode() {
        return supportsBatchMode() && isOpen() && this.threadLocalTx.get().getBatchManager().isInNormalMode();
    }

    public boolean isInStreamingBatchMode() {
        return supportsBatchMode() && isOpen() && this.threadLocalTx.get().getBatchManager().isInStreamingMode();
    }

    public boolean isInStreamingWithLockBatchMode() {
        return supportsBatchMode() && isOpen() && this.threadLocalTx.get().getBatchManager().isInStreamingModeWithLock();
    }

    public BatchManager.BatchModeType getBatchModeType() {
        Preconditions.checkState(isOpen(), "SqlgTransaction.getBatchModeType() must be called within a transaction.");
        if (supportsBatchMode()) {
            return this.threadLocalTx.get().getBatchManager().getBatchModeType();
        } else {
            return BatchManager.BatchModeType.NONE;
        }
    }

    private boolean supportsBatchMode() {
        return this.sqlgGraph.getSqlDialect().supportsBatchMode();
    }

    public BatchManager getBatchManager() {
        return this.threadLocalTx.get().getBatchManager();
    }

    public Connection getConnection() {
        if (!isOpen()) {
            readWrite();
        }
        return this.threadLocalTx.get().getConnection();
    }

    public void flush() {
        if (!this.isInBatchMode()) {
            throw new IllegalStateException("Transaction must be in batch mode to flush");
        }
        this.logger.debug("flushing transaction!!!");
        if (!this.getBatchManager().isBusyFlushing()) {
            this.getBatchManager().flush();
        }
    }

    void addElementPropertyRollback(ElementPropertyRollback elementPropertyRollback) {
        if (!isOpen()) {
            throw new IllegalStateException("A transaction must be in progress to add a elementPropertyRollback function!");
        }
        this.threadLocalTx.get().getElementPropertyRollback().put(elementPropertyRollback, null);
    }

    public void beforeCommit(BeforeCommit beforeCommitFunction) {
        this.beforeCommitFunction = beforeCommitFunction;
    }

    public void afterCommit(AfterCommit afterCommitFunction) {
        this.afterCommitFunction = afterCommitFunction;
    }

    public void afterRollback(AfterRollback afterCommitFunction) {
        this.afterRollbackFunction = afterCommitFunction;
    }

    @Override
    public boolean isOpen() {
        return this.threadLocalTx.get() != null;
    }

    SqlgVertex putVertexIfAbsent(SqlgGraph sqlgGraph, String schema, String table, Long id) {
        return this.threadLocalTx.get().putVertexIfAbsent(sqlgGraph, schema, table, id);
    }

    //Called for vertices that exist but are not yet in the transaction cache
    SqlgVertex putVertexIfAbsent(SqlgVertex sqlgVertex) {
        return this.threadLocalTx.get().putVertexIfAbsent(sqlgVertex);
    }

    //Called for new vertices
    void add(SqlgVertex sqlgVertex) {
        this.threadLocalTx.get().add(sqlgVertex);
    }

    public void add(PreparedStatement preparedStatement) {
        this.threadLocalPreparedStatementTx.get().add(preparedStatement);
    }

    // only used for tests
    public PreparedStatementCache getPreparedStatementCache() {
        return threadLocalPreparedStatementTx.get();
    }
    
    /**
     * are we reading the SQL query results lazily?
     * @return true if we are processing the results lazily, false otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public boolean isLazyQueries(){
    	return this.threadLocalTx.get().isLazyQueries();
    }
    
    /**
     * set the laziness on query result reading
     * @param lazy boolean to set the query as lazy or not.
     */
    public void setLazyQueries(boolean lazy){
    	readWrite();
    	this.threadLocalTx.get().setLazyQueries(lazy);
    }
    
    /**
     * get default fetch size
     * @return
     */
    public Integer getDefaultFetchSize() {
		return defaultFetchSize;
	}

    /**
     * set default fetch size
     * @param fetchSize
     */
	public void setDefaultFetchSize(Integer fetchSize) {
		this.defaultFetchSize = fetchSize;
	}
	
	/**
	 * get fetch size for current transaction
	 * @return
	 */
	public Integer getFetchSize() {
		readWrite();
		return this.threadLocalTx.get().getFetchSize();
	}

	/**
	 * set fetch size for current transaction
	 * @param fetchSize
	 */
	public void setFetchSize(Integer fetchSize) {
		readWrite();
    	this.threadLocalTx.get().setFetchSize(fetchSize);
	}
}
